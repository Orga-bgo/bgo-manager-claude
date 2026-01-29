package com.mgomanager.app.domain.operation

data class BackupOperationResult(
    val accountName: String,
    val backupPath: String,
    val missingIds: List<String> = emptyList()
) : OperationResult

data class CreateAccountOperationResult(
    val accountId: Long,
    val accountName: String
) : OperationResult

data class RestoreOperationResult(
    val accountName: String
) : OperationResult

sealed interface OperationResult
