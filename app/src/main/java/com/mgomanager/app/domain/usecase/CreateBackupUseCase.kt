package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.operation.BackupRunner
import com.mgomanager.app.domain.util.FilePermissionManager
import com.mgomanager.app.domain.util.IdExtractor
import com.mgomanager.app.domain.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class BackupRequest(
    val accountName: String,
    val prefix: String,
    val backupRootPath: String,
    val hasFacebookLink: Boolean,
    val fbUsername: String? = null,
    val fbPassword: String? = null,
    val fb2FA: String? = null,
    val fbTempMail: String? = null
)

class CreateBackupUseCase @Inject constructor(
    private val rootUtil: RootUtil,
    private val idExtractor: IdExtractor,
    private val permissionManager: FilePermissionManager,
    private val accountRepository: AccountRepository,
    private val logRepository: LogRepository
) : BackupRunner {

    companion object {
        const val MGO_DATA_PATH = "/data/data/com.scopely.monopolygo"
        const val MGO_FILES_PATH = "$MGO_DATA_PATH/files/DiskBasedCacheDirectory"
        const val MGO_PREFS_PATH = "$MGO_DATA_PATH/shared_prefs"
        const val PLAYER_PREFS_FILE = "com.scopely.monopolygo.v2.playerprefs.xml"
        const val XPOSED_SHARED_FILE = "/data/local/tmp/mgo_current_appsetid.txt"
    }

    suspend fun execute(request: BackupRequest, forceDuplicate: Boolean = false): BackupResult = withContext(Dispatchers.IO) {
        try {
            logRepository.logInfo("BACKUP", "Starte Backup für ${request.accountName}")

            // Step 1: Force stop Monopoly Go
            rootUtil.forceStopMonopolyGo().getOrThrow()
            logRepository.logInfo("BACKUP", "Monopoly Go gestoppt", request.accountName)

            // Step 2: Read file permissions
            val permissions = permissionManager.getFilePermissions(MGO_FILES_PATH).getOrElse {
                logRepository.logError("BACKUP", "Fehler beim Lesen der Berechtigungen", request.accountName, it as? Exception)
                throw it
            }

            // Step 3: Create backup directory
            val backupPath = "${request.backupRootPath}${request.prefix}${request.accountName}/"
            val backupDir = File(backupPath)

            val createDirResult = rootUtil.executeCommand("mkdir -p $backupPath")
            if (createDirResult.isFailure) {
                val errorMsg = createDirResult.exceptionOrNull()?.message ?: "Unknown error"
                throw Exception("Backup-Verzeichnis konnte nicht erstellt werden: $errorMsg")
            }
            logRepository.logInfo("BACKUP", "Backup-Verzeichnis erstellt: $backupPath", request.accountName)

            // Step 4: Copy directories
            copyDirectory(MGO_FILES_PATH, "$backupPath/DiskBasedCacheDirectory/", request.accountName)
            copyDirectory(MGO_PREFS_PATH, "$backupPath/shared_prefs/", request.accountName)

            // Step 5: Extract IDs
            val playerPrefsFile = File("$backupPath/shared_prefs/$PLAYER_PREFS_FILE")
            val extractedIds = idExtractor.extractIdsFromPlayerPrefs(playerPrefsFile).getOrElse {
                logRepository.logError("BACKUP", "ID-Extraktion fehlgeschlagen", request.accountName, it as? Exception)
                throw Exception("User ID konnte nicht extrahiert werden (MANDATORY)")
            }

            // Step 6.5: Check for duplicate User ID (unless force flag is set)
            if (!forceDuplicate) {
                val existingAccount = accountRepository.getAccountByUserId(extractedIds.userId)
                if (existingAccount != null) {
                    logRepository.logWarning("BACKUP", "Duplicate User ID found: ${extractedIds.userId} exists as ${existingAccount.fullName}", request.accountName)
                    // Clean up the backup directory we just created
                    rootUtil.executeCommand("rm -rf $backupPath")
                    return@withContext BackupResult.DuplicateUserId(
                        userId = extractedIds.userId,
                        existingAccountName = existingAccount.fullName
                    )
                }
            }

            // Step 7: Read SSAID from hook file
            val ssaid = readSsaidFromHook()

            // Step 8: Create Account object
            val now = System.currentTimeMillis()
            val account = Account(
                accountName = request.accountName,
                prefix = request.prefix,
                createdAt = now,
                lastPlayedAt = now,
                userId = extractedIds.userId,
                gaid = extractedIds.gaid,
                deviceToken = extractedIds.deviceToken,
                appSetId = extractedIds.appSetId,
                ssaid = ssaid,
                hasFacebookLink = request.hasFacebookLink,
                fbUsername = request.fbUsername,
                fbPassword = request.fbPassword,
                fb2FA = request.fb2FA,
                fbTempMail = request.fbTempMail,
                backupPath = backupPath,
                fileOwner = permissions.owner,
                fileGroup = permissions.group,
                filePermissions = permissions.permissions
            )

            // Step 9: Save to database
            accountRepository.insertAccount(account)

            logRepository.logInfo(
                "BACKUP",
                "Backup erfolgreich abgeschlossen für ${request.accountName}",
                request.accountName
            )

            // Check if any IDs are missing
            val missingIds = mutableListOf<String>()
            if (extractedIds.gaid == "nicht vorhanden") missingIds.add("GAID")
            if (extractedIds.deviceToken == "nicht vorhanden") missingIds.add("Device Token")
            if (extractedIds.appSetId == "nicht vorhanden") missingIds.add("App Set ID")
            if (ssaid == "nicht vorhanden") missingIds.add("SSAID")

            if (missingIds.isNotEmpty()) {
                BackupResult.PartialSuccess(account, missingIds)
            } else {
                BackupResult.Success(account)
            }

        } catch (e: Exception) {
            logRepository.logError("BACKUP", "Backup fehlgeschlagen: ${e.message}", request.accountName, e)
            BackupResult.Failure("Backup fehlgeschlagen: ${e.message}", e)
        }
    }

    override suspend fun run(request: BackupRequest, forceDuplicate: Boolean): BackupResult {
        return execute(request, forceDuplicate)
    }

    private suspend fun copyDirectory(source: String, destination: String, accountName: String) {
        val result = rootUtil.executeCommand("cp -r $source $destination")
        if (result.isSuccess) {
            logRepository.logInfo("BACKUP", "Verzeichnis kopiert: $source -> $destination", accountName)
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
            logRepository.logError("BACKUP", "Fehler beim Kopieren: $source - $errorMsg", accountName)
            throw Exception("Verzeichnis konnte nicht kopiert werden: $source - $errorMsg")
        }
    }

    private suspend fun readSsaidFromHook(): String = withContext(Dispatchers.IO) {
        val result = rootUtil.executeCommand("cat $XPOSED_SHARED_FILE")
        if (result.isFailure) {
            return@withContext "nicht vorhanden"
        }

        val content = result.getOrNull()?.trim().orEmpty()
        if (content.isBlank()) {
            return@withContext "nicht vorhanden"
        }

        val parts = content.split("|")
        if (parts.size < 2) {
            return@withContext "nicht vorhanden"
        }

        parts[1].takeIf { it.isNotBlank() && it != "nicht vorhanden" } ?: "nicht vorhanden"
    }
}
