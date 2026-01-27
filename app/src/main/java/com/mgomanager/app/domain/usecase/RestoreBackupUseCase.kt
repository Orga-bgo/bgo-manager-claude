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

        // Shared file for Xposed hook - world-readable location
        const val XPOSED_SHARED_FILE = "/data/local/tmp/mgo_current_appsetid.txt"
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

            // Step 8: Mark account as last restored for Xposed hook
            accountRepository.markAsLastRestored(accountId)

            // Step 9: Write App Set ID to shared file for Xposed hook access
            // The hook runs in Monopoly GO's process and reads from /data/local/tmp/
            writeSharedAppSetIdFile(account.appSetId, account.fullName)

            logRepository.logInfo(
                "RESTORE",
                "Account als zuletzt wiederhergestellt markiert. Xposed Hook nutzt App Set ID: ${account.appSetId}",
                account.fullName
            )

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

    /**
     * Write the App Set ID to a shared file that the Xposed hook can read.
     * Uses /data/local/tmp/ which is world-readable and avoids SQLite WAL issues.
     *
     * Format: appSetId|accountName|timestamp
     */
    private suspend fun writeSharedAppSetIdFile(appSetId: String, accountName: String) {
        try {
            val timestamp = System.currentTimeMillis()
            val content = "$appSetId|$accountName|$timestamp"

            // Write file using root (echo with heredoc to handle special characters)
            rootUtil.executeCommand("echo '$content' > $XPOSED_SHARED_FILE")

            // Set permissions to 644 (world-readable)
            rootUtil.executeCommand("chmod 644 $XPOSED_SHARED_FILE")

            logRepository.logInfo(
                "RESTORE",
                "Xposed shared file geschrieben: $XPOSED_SHARED_FILE"
            )
        } catch (e: Exception) {
            // Log but don't fail the restore - Settings.Secure hook is the primary method
            logRepository.logWarning(
                "RESTORE",
                "Konnte Xposed shared file nicht schreiben: ${e.message}"
            )
        }
    }
}
