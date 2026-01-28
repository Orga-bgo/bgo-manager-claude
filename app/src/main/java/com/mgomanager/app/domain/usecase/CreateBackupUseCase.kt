package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
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
) {

    companion object {
        const val MGO_DATA_PATH = "/data/data/com.scopely.monopolygo"
        const val MGO_FILES_PATH = "$MGO_DATA_PATH/files/DiskBasedCacheDirectory"
        const val MGO_PREFS_PATH = "$MGO_DATA_PATH/shared_prefs"
        const val SSAID_PATH = "/data/system/users/0/settings_ssaid.xml"
        const val PLAYER_PREFS_FILE = "com.scopely.monopolygo.v2.playerprefs.xml"

        // Xposed hook capture mode files (for SSAID fallback)
        const val CAPTURE_MODE_FLAG = "/data/local/tmp/mgo_capture_mode"
        const val CAPTURED_SSAID_FILE = "/data/local/tmp/mgo_captured_ssaid.txt"
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

            // Step 5: Copy SSAID file
            val copyResult = rootUtil.executeCommand("cp $SSAID_PATH $backupPath/settings_ssaid.xml")
            if (copyResult.isFailure) {
                logRepository.logWarning("BACKUP", "SSAID-Datei konnte nicht kopiert werden", request.accountName)
            }

            // Step 6: Extract IDs
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

            // Step 7: Extract SSAID
            val ssaidFile = File("$backupPath/settings_ssaid.xml")
            val ssaid = if (ssaidFile.exists()) {
                idExtractor.extractSsaid(ssaidFile)
            } else {
                "nicht vorhanden"
            }

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

    // ============================================================
    // SSAID Capture Mode (Fallback when settings_ssaid.xml is missing)
    // ============================================================

    /**
     * Enable capture mode - creates flag file that tells the Xposed hook
     * to write the original Android ID to a capture file.
     */
    suspend fun enableCaptureMode(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Clean up any old capture file
            rootUtil.executeCommand("rm -f $CAPTURED_SSAID_FILE")
            // Create the capture mode flag
            rootUtil.executeCommand("touch $CAPTURE_MODE_FLAG && chmod 644 $CAPTURE_MODE_FLAG")
            logRepository.logInfo("BACKUP", "SSAID Capture Mode aktiviert")
            Result.success(Unit)
        } catch (e: Exception) {
            logRepository.logError("BACKUP", "Konnte Capture Mode nicht aktivieren: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Disable capture mode - removes the flag file.
     */
    suspend fun disableCaptureMode(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            rootUtil.executeCommand("rm -f $CAPTURE_MODE_FLAG")
            logRepository.logInfo("BACKUP", "SSAID Capture Mode deaktiviert")
            Result.success(Unit)
        } catch (e: Exception) {
            logRepository.logError("BACKUP", "Konnte Capture Mode nicht deaktivieren: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Start Monopoly GO app so the hook can capture the Android ID.
     */
    suspend fun startMonopolyGo(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            rootUtil.executeCommand("am start -n com.scopely.monopolygo/com.scopely.unity.ScopelyUnityActivity")
            logRepository.logInfo("BACKUP", "Monopoly GO gestartet für SSAID Capture")
            Result.success(Unit)
        } catch (e: Exception) {
            logRepository.logError("BACKUP", "Konnte Monopoly GO nicht starten: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Read the captured SSAID from the capture file.
     * Returns null if file doesn't exist or is invalid.
     */
    suspend fun readCapturedSsaid(): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(CAPTURED_SSAID_FILE)
            if (!file.exists()) {
                logRepository.logWarning("BACKUP", "Captured SSAID Datei nicht gefunden")
                return@withContext null
            }

            val content = file.readText().trim()
            // Format: ssaid|timestamp
            val parts = content.split("|")
            if (parts.isEmpty() || parts[0].isBlank()) {
                logRepository.logWarning("BACKUP", "Ungültiges Format in Captured SSAID Datei")
                return@withContext null
            }

            val ssaid = parts[0]
            logRepository.logInfo("BACKUP", "Captured SSAID gelesen: $ssaid")
            ssaid
        } catch (e: Exception) {
            logRepository.logError("BACKUP", "Fehler beim Lesen der Captured SSAID: ${e.message}")
            null
        }
    }

    /**
     * Check if SSAID file exists (before starting backup).
     */
    fun isSsaidAvailable(): Boolean {
        return File(SSAID_PATH).exists()
    }

    /**
     * Complete a backup that was paused for SSAID capture.
     * Updates the account's SSAID with the captured value.
     */
    suspend fun completeSsaidCapture(accountId: Long, capturedSsaid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Disable capture mode
            disableCaptureMode()

            // Get the account
            val account = accountRepository.getAccountById(accountId)
                ?: return@withContext Result.failure(Exception("Account nicht gefunden"))

            // Update the account with captured SSAID
            val updatedAccount = account.copy(ssaid = capturedSsaid)
            accountRepository.updateAccount(updatedAccount)

            logRepository.logInfo(
                "BACKUP",
                "Account SSAID aktualisiert: $capturedSsaid",
                account.fullName
            )

            Result.success(Unit)
        } catch (e: Exception) {
            logRepository.logError("BACKUP", "Fehler beim Aktualisieren der SSAID: ${e.message}")
            Result.failure(e)
        }
    }
}
