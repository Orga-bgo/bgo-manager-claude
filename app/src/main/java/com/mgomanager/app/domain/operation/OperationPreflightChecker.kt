package com.mgomanager.app.domain.operation

import com.mgomanager.app.domain.util.AccountNameValidator
import com.mgomanager.app.domain.usecase.BackupRequest
import com.mgomanager.app.domain.usecase.CreateNewAccountRequest
import java.io.File
import javax.inject.Inject

sealed class PreflightResult {
    data object Success : PreflightResult()
    data class Failure(val error: OperationError) : PreflightResult()
}

class OperationPreflightChecker @Inject constructor(
    private val dependencies: OperationPreflightDependencies
) {

    suspend fun checkBackup(request: BackupRequest): PreflightResult {
        if (!dependencies.requestRootAccess()) {
            return PreflightResult.Failure(
                OperationError(
                    code = OperationErrorCode.RootDenied,
                    suggestedActions = setOf(OperationActionHint.CHECK_ROOT)
                )
            )
        }

        if (!dependencies.isMonopolyGoInstalled()) {
            return PreflightResult.Failure(
                OperationError(code = OperationErrorCode.MonopolyNotInstalled)
            )
        }

        if (!dependencies.isBackupPathWritable(request.backupRootPath)) {
            return PreflightResult.Failure(
                OperationError(
                    code = OperationErrorCode.Unknown,
                    detail = "backup_path_not_writable",
                    suggestedActions = setOf(OperationActionHint.CHECK_BACKUP_PATH)
                )
            )
        }

        return PreflightResult.Success
    }

    suspend fun checkCreateAccount(request: CreateNewAccountRequest): PreflightResult {
        val validationCode = AccountNameValidator.validate(request.accountName)
        if (validationCode != null) {
            return PreflightResult.Failure(
                OperationError(
                    code = OperationErrorCode.Unknown,
                    detail = validationCode,
                    suggestedActions = setOf(OperationActionHint.RENAME_ACCOUNT)
                )
            )
        }

        val fullName = "${request.prefix}${request.accountName}"
        if (dependencies.accountExistsByName(fullName)) {
            return PreflightResult.Failure(
                OperationError(
                    code = OperationErrorCode.Unknown,
                    detail = "account_exists",
                    suggestedActions = setOf(OperationActionHint.RENAME_ACCOUNT)
                )
            )
        }

        if (!dependencies.requestRootAccess()) {
            return PreflightResult.Failure(
                OperationError(
                    code = OperationErrorCode.RootDenied,
                    suggestedActions = setOf(OperationActionHint.CHECK_ROOT)
                )
            )
        }

        return PreflightResult.Success
    }

    suspend fun checkRestore(accountId: Long): PreflightResult {
        if (!dependencies.requestRootAccess()) {
            return PreflightResult.Failure(
                OperationError(
                    code = OperationErrorCode.RootDenied,
                    suggestedActions = setOf(OperationActionHint.CHECK_ROOT)
                )
            )
        }

        if (!dependencies.accountExistsById(accountId)) {
            return PreflightResult.Failure(
                OperationError(code = OperationErrorCode.Unknown, detail = "account_missing")
            )
        }

        val backupPath = dependencies.getBackupPathForAccount(accountId)
        if (backupPath.isNullOrBlank() || !backupArtifactsExist(backupPath)) {
            return PreflightResult.Failure(
                OperationError(code = OperationErrorCode.MissingBackupArtifacts, detail = backupPath)
            )
        }

        return PreflightResult.Success
    }

    private fun backupArtifactsExist(path: String): Boolean {
        val diskCacheDir = File("${path}DiskBasedCacheDirectory/")
        val sharedPrefsDir = File("${path}shared_prefs/")
        return diskCacheDir.exists() && sharedPrefsDir.exists()
    }
}
