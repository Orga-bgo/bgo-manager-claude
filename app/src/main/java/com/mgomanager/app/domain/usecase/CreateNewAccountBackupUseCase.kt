package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FilePermissionManager
import com.mgomanager.app.domain.util.IdExtractor
import com.mgomanager.app.domain.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

data class CreateAccountBackupProgress(
    val step: Int,
    val totalSteps: Int,
    val message: String
)

sealed class CreateNewAccountBackupResult {
    data class Success(val accountId: Long, val accountName: String) : CreateNewAccountBackupResult()
    data class Failure(val error: String, val exception: Exception? = null) : CreateNewAccountBackupResult()
    data class Progress(val progress: CreateAccountBackupProgress) : CreateNewAccountBackupResult()
}

class CreateNewAccountBackupUseCase @Inject constructor(
    private val rootUtil: RootUtil,
    private val idExtractor: IdExtractor,
    private val permissionManager: FilePermissionManager,
    private val accountRepository: AccountRepository,
    private val logRepository: LogRepository
) {

    companion object {
        private const val TOTAL_STEPS = 7
        private const val MGO_PACKAGE = "com.scopely.monopolygo"
        private const val MGO_DATA_PATH = "/data/data/$MGO_PACKAGE"
        private const val MGO_FILES_PATH = "$MGO_DATA_PATH/files/DiskBasedCacheDirectory"
        private const val MGO_PREFS_PATH = "$MGO_DATA_PATH/shared_prefs"
        private const val PLAYER_PREFS_FILE = "com.scopely.monopolygo.v2.playerprefs.xml"
    }

    fun executeWithProgress(accountId: Long, backupRootPath: String): Flow<CreateNewAccountBackupResult> = flow {
        var accountName: String? = null
        try {
            val account = accountRepository.getAccountById(accountId)
                ?: run {
                    emit(CreateNewAccountBackupResult.Failure("Account nicht gefunden"))
                    return@flow
                }
            accountName = account.fullName

            if (!rootUtil.requestRootAccess()) {
                emit(CreateNewAccountBackupResult.Failure("Root-Zugriff erforderlich"))
                return@flow
            }

            emit(CreateNewAccountBackupResult.Progress(CreateAccountBackupProgress(1, TOTAL_STEPS, "Starte Monopoly Go...")))
            val startResult = rootUtil.executeCommand("am start -n $MGO_PACKAGE/com.scopely.unity.ScopelyUnityActivity")
            if (startResult.isFailure) {
                val error = startResult.exceptionOrNull()?.message ?: "Unknown error"
                throw Exception("Monopoly Go konnte nicht gestartet werden: $error")
            }

            emit(CreateNewAccountBackupResult.Progress(CreateAccountBackupProgress(2, TOTAL_STEPS, "Warte auf App-Start...")))
            delay(5000)

            emit(CreateNewAccountBackupResult.Progress(CreateAccountBackupProgress(3, TOTAL_STEPS, "Stoppe Monopoly Go...")))
            rootUtil.forceStopMonopolyGo().getOrElse {
                logRepository.logWarning("CREATE_NEW_BACKUP", "Konnte Monopoly Go nicht stoppen: ${it.message}", account.fullName)
            }

            emit(CreateNewAccountBackupResult.Progress(CreateAccountBackupProgress(4, TOTAL_STEPS, "Bereite Backup vor...")))
            val permissions = permissionManager.getFilePermissions(MGO_FILES_PATH).getOrElse {
                logRepository.logError("CREATE_NEW_BACKUP", "Fehler beim Lesen der Berechtigungen", account.fullName, it as? Exception)
                throw it
            }

            val backupPath = "${backupRootPath}${account.prefix}${account.accountName}/"
            val createDirResult = rootUtil.executeCommand("mkdir -p $backupPath")
            if (createDirResult.isFailure) {
                val errorMsg = createDirResult.exceptionOrNull()?.message ?: "Unknown error"
                throw Exception("Backup-Verzeichnis konnte nicht erstellt werden: $errorMsg")
            }

            emit(CreateNewAccountBackupResult.Progress(CreateAccountBackupProgress(5, TOTAL_STEPS, "Sichere Spieldaten...")))
            copyDirectory(MGO_FILES_PATH, "$backupPath/DiskBasedCacheDirectory/", account.fullName)
            copyDirectory(MGO_PREFS_PATH, "$backupPath/shared_prefs/", account.fullName)

            emit(CreateNewAccountBackupResult.Progress(CreateAccountBackupProgress(6, TOTAL_STEPS, "Extrahiere User ID...")))
            val playerPrefsFile = File("$backupPath/shared_prefs/$PLAYER_PREFS_FILE")
            val extractedIds = idExtractor.extractIdsFromPlayerPrefs(playerPrefsFile).getOrElse {
                logRepository.logError("CREATE_NEW_BACKUP", "ID-Extraktion fehlgeschlagen", account.fullName, it as? Exception)
                throw Exception("User ID konnte nicht extrahiert werden (MANDATORY)")
            }

            emit(CreateNewAccountBackupResult.Progress(CreateAccountBackupProgress(7, TOTAL_STEPS, "Aktualisiere Account...")))
            val updatedAccount = account.copy(
                userId = extractedIds.userId,
                gaid = if (extractedIds.gaid != "nicht vorhanden") extractedIds.gaid else account.gaid,
                deviceToken = if (extractedIds.deviceToken != "nicht vorhanden") extractedIds.deviceToken else account.deviceToken,
                appSetId = if (extractedIds.appSetId != "nicht vorhanden") extractedIds.appSetId else account.appSetId,
                backupPath = backupPath,
                fileOwner = permissions.owner,
                fileGroup = permissions.group,
                filePermissions = permissions.permissions,
                lastPlayedAt = System.currentTimeMillis()
            )
            accountRepository.updateAccount(updatedAccount)

            logRepository.logInfo(
                "CREATE_NEW_BACKUP",
                "Backup für neuen Account erfolgreich abgeschlossen",
                account.fullName
            )

            rootUtil.forceStopMonopolyGo().getOrElse {
                logRepository.logWarning("CREATE_NEW_BACKUP", "Konnte Monopoly Go nicht stoppen: ${it.message}", account.fullName)
            }

            emit(CreateNewAccountBackupResult.Success(accountId, account.fullName))
        } catch (e: Exception) {
            val logName = accountName ?: "unbekannt"
            logRepository.logError("CREATE_NEW_BACKUP", "Backup fehlgeschlagen: ${e.message}", logName, e)
            rootUtil.forceStopMonopolyGo().getOrElse { }
            emit(CreateNewAccountBackupResult.Failure("Backup fehlgeschlagen: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun copyDirectory(source: String, destination: String, accountName: String) {
        val result = rootUtil.executeCommand("cp -r $source $destination")
        if (result.isSuccess) {
            logRepository.logInfo("CREATE_NEW_BACKUP", "Verzeichnis kopiert: $source -> $destination", accountName)
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
            logRepository.logError("CREATE_NEW_BACKUP", "Fehler beim Kopieren: $source - $errorMsg", accountName)
            throw Exception("Verzeichnis konnte nicht kopiert werden: $source - $errorMsg")
        }
    }
}
