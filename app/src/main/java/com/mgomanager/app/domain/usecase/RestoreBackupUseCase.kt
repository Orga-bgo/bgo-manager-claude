package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FilePermissionManager
import com.mgomanager.app.domain.util.RootUtil
import com.mgomanager.app.domain.util.SsaidManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class RestoreBackupUseCase @Inject constructor(
    private val rootUtil: RootUtil,
    private val permissionManager: FilePermissionManager,
    private val accountRepository: AccountRepository,
    private val logRepository: LogRepository,
    private val ssaidManager: SsaidManager
) {

    companion object {
        const val MGO_DATA_PATH = "/data/data/com.scopely.monopolygo"
        const val MGO_FILES_PATH = "$MGO_DATA_PATH/files"
        const val MGO_PREFS_PATH = "$MGO_DATA_PATH/shared_prefs"

        // Shared file for Xposed hook - world-readable location (App Set ID only)
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

            // Step 5: Write SSAID directly to settings_ssaid.xml using SsaidManager
            // This is more reliable than copying the file and works across different device states
            if (account.ssaid != "nicht vorhanden" && ssaidManager.isValidAndroidId(account.ssaid)) {
                val ssaidSuccess = ssaidManager.setAndroidIdForPackage(
                    packageName = SsaidManager.TARGET_PACKAGE,
                    androidId = account.ssaid
                )

                if (ssaidSuccess) {
                    logRepository.logInfo(
                        "RESTORE",
                        "SSAID erfolgreich in settings_ssaid.xml geschrieben: ${account.ssaid}",
                        account.fullName
                    )
                } else {
                    logRepository.logWarning(
                        "RESTORE",
                        "SSAID konnte nicht in settings_ssaid.xml geschrieben werden",
                        account.fullName
                    )
                }
            } else {
                logRepository.logWarning(
                    "RESTORE",
                    "Keine gueltige SSAID vorhanden (${account.ssaid}), ueberspringe SSAID-Wiederherstellung",
                    account.fullName
                )
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
            // Note: SSAID is now handled via settings_ssaid.xml modification (no hook needed)
            writeSharedHookFile(account.appSetId, account.ssaid, account.fullName)

            logRepository.logInfo(
                "RESTORE",
                "Xposed Hook bereitgestellt - App Set ID: ${account.appSetId} (SSAID via settings_ssaid.xml)",
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
     * Write App Set ID to a shared file that the Xposed hook can read.
     * Uses /data/local/tmp/ which is world-readable and avoids SQLite WAL issues.
     *
     * Format: appSetId|ssaid|accountName|timestamp
     * Note: SSAID is included for backward compatibility but is no longer used by the hook.
     *       SSAID is now written directly to settings_ssaid.xml via SsaidManager.
     *
     * The hook uses:
     * - appSetId for App Set ID replacement only
     */
    private suspend fun writeSharedHookFile(appSetId: String, ssaid: String, accountName: String) {
        try {
            val timestamp = System.currentTimeMillis()
            val content = "$appSetId|$ssaid|$accountName|$timestamp"

            // Write file using root (echo with heredoc to handle special characters)
            rootUtil.executeCommand("echo '$content' > $XPOSED_SHARED_FILE")

            // Set permissions to 644 (world-readable)
            rootUtil.executeCommand("chmod 644 $XPOSED_SHARED_FILE")

            logRepository.logInfo(
                "RESTORE",
                "Xposed shared file geschrieben (AppSetId: $appSetId, SSAID: $ssaid)"
            )
        } catch (e: Exception) {
            // Log but don't fail the restore - hook might still work with cached values
            logRepository.logWarning(
                "RESTORE",
                "Konnte Xposed shared file nicht schreiben: ${e.message}"
            )
        }
    }
}
