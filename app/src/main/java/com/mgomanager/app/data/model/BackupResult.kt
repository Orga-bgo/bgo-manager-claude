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
