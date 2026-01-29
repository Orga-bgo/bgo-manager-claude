package com.mgomanager.app.domain.operation

enum class OperationErrorCode {
    RootDenied,
    MonopolyNotInstalled,
    MissingBackupArtifacts,
    DuplicateUserId,
    PermissionRestoreFailed,
    CopyFailed,
    ZipFailed,
    ZipInvalid,
    DbWriteFailed,
    SshConfigMissing,
    SshAuthFailed,
    SshHostUnreachable,
    SshUploadFailed,
    SshDownloadFailed,
    Canceled,
    Unknown
}

enum class OperationActionHint {
    RENAME_ACCOUNT,
    ABORT,
    CHECK_ROOT,
    CHECK_BACKUP_PATH,
    CHECK_SSH_CONFIG,
    RETRY
}

data class OperationError(
    val code: OperationErrorCode,
    val detail: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val suggestedActions: Set<OperationActionHint> = emptySet()
)
