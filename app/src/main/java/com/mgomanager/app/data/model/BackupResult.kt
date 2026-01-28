package com.mgomanager.app.data.model

/**
 * Result of a backup operation
 */
sealed class BackupResult {
    data class Success(
        val account: Account,
        val message: String = "Backup erfolgreich erstellt"
    ) : BackupResult()

    data class Failure(
        val error: String,
        val exception: Exception? = null
    ) : BackupResult()

    data class PartialSuccess(
        val account: Account,
        val missingIds: List<String>,
        val message: String = "Backup erstellt, aber einige IDs fehlen"
    ) : BackupResult()

    data class DuplicateUserId(
        val userId: String,
        val existingAccountName: String
    ) : BackupResult()

    /**
     * SSAID file not found - need to start Monopoly GO to capture Android ID via hook.
     * Contains partial backup data that will be completed after SSAID capture.
     */
    data class NeedsSsaidFallback(
        val backupPath: String,
        val accountName: String,
        val prefix: String,
        val message: String = "Android ID nicht gefunden - nutze Fallback"
    ) : BackupResult()
}

/**
 * Result of a restore operation
 */
sealed class RestoreResult {
    data class Success(
        val accountName: String,
        val message: String = "Wiederherstellung erfolgreich"
    ) : RestoreResult()

    data class Failure(
        val error: String,
        val exception: Exception? = null
    ) : RestoreResult()
}
