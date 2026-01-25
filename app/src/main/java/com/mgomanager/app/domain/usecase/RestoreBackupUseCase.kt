package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FilePermissionManager
import com.mgomanager.app.domain.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class RestoreBackupUseCase @Inject constructor(
    private val rootUtil: RootUtil,
    private val permissionManager: FilePermissionManager,
    private val accountRepository: AccountRepository,
    private val logRepository: LogRepository
) {

    companion object {
        const val MGO_DATA_PATH = "/data/data/com.scopely.monopolygo"
        const val MGO_FILES_PATH = "$MGO_DATA_PATH/files"
        const val MGO_PREFS_PATH = "$MGO_DATA_PATH/shared_prefs"
        const val SSAID_PATH = "/data/system/users/0/settings_ssaid.xml"
    }

    suspend fun execute(accountId: Long): RestoreResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: Get account from database
            val account = accountRepository.getAccountById(accountId)
                ?: return@withContext RestoreResult.Failure("Account nicht gefunden")

            logRepository.logInfo("RESTORE", "Starte Restore für ${account.fullName}")

            // Step 2: Validate backup files exist
            val backupPath = account.backupPath
            if (!validateBackupFiles(backupPath)) {
                logRepository.logError("RESTORE", "Backup-Dateien fehlen", account.fullName)
                return@withContext RestoreResult.Failure("Backup-Dateien fehlen oder sind beschädigt")
            }

            // Step 3: Force stop Monopoly Go
            rootUtil.forceStopMonopolyGo().getOrThrow()
            logRepository.logInfo("RESTORE", "Monopoly Go gestoppt", account.fullName)

            // Step 4: Remove old directories and copy backup directories
            // First delete existing directories to prevent nested folder issues
            rootUtil.executeCommand("rm -rf $MGO_FILES_PATH/DiskBasedCacheDirectory")
            rootUtil.executeCommand("rm -rf $MGO_PREFS_PATH")

            // Copy directories to parent (without trailing slashes to copy the folder itself)
            copyBackupDirectory("${backupPath}DiskBasedCacheDirectory", "$MGO_FILES_PATH/", account.fullName)
            copyBackupDirectory("${backupPath}shared_prefs", "$MGO_DATA_PATH/", account.fullName)

            // Step 5: Copy SSAID file back
            val ssaidFile = File("${backupPath}settings_ssaid.xml")
            if (ssaidFile.exists()) {
                rootUtil.executeCommand("cp ${backupPath}settings_ssaid.xml $SSAID_PATH").getOrThrow()
                logRepository.logInfo("RESTORE", "SSAID wiederhergestellt", account.fullName)
            }

            // Step 6: Restore permissions
            permissionManager.setFileOwnership(
                MGO_FILES_PATH,
                account.fileOwner,
                account.fileGroup
            ).getOrThrow()

            permissionManager.setFileOwnership(
                MGO_PREFS_PATH,
                account.fileOwner,
                account.fileGroup
            ).getOrThrow()

            permissionManager.setFilePermissions(MGO_FILES_PATH, account.filePermissions).getOrThrow()
            permissionManager.setFilePermissions(MGO_PREFS_PATH, account.filePermissions).getOrThrow()

            logRepository.logInfo("RESTORE", "Berechtigungen wiederhergestellt", account.fullName)

            // Step 7: Update lastPlayedAt timestamp
            accountRepository.updateLastPlayedTimestamp(accountId)

            logRepository.logInfo("RESTORE", "Restore erfolgreich abgeschlossen", account.fullName)
            RestoreResult.Success(account.fullName)

        } catch (e: Exception) {
            logRepository.logError("RESTORE", "Restore fehlgeschlagen: ${e.message}", null, e)
            RestoreResult.Failure("Restore fehlgeschlagen: ${e.message}", e)
        }
    }

    private fun validateBackupFiles(backupPath: String): Boolean {
        val diskCacheDir = File("${backupPath}DiskBasedCacheDirectory/")
        val sharedPrefsDir = File("${backupPath}shared_prefs/")

        return diskCacheDir.exists() && sharedPrefsDir.exists()
    }

    private suspend fun copyBackupDirectory(source: String, destination: String, accountName: String) {
        val result = rootUtil.executeCommand("cp -r $source $destination")
        if (result.isSuccess) {
            logRepository.logInfo("RESTORE", "Verzeichnis wiederhergestellt: $source -> $destination", accountName)
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
            logRepository.logError("RESTORE", "Fehler beim Wiederherstellen: $source - $errorMsg", accountName)
            throw Exception("Verzeichnis konnte nicht wiederhergestellt werden: $source - $errorMsg")
        }
    }
}
