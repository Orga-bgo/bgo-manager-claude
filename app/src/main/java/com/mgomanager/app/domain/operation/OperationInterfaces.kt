package com.mgomanager.app.domain.operation

import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.domain.usecase.BackupRequest
import com.mgomanager.app.domain.usecase.CreateNewAccountRequest
import com.mgomanager.app.domain.usecase.CreateNewAccountResult
import kotlinx.coroutines.flow.Flow

interface BackupRunner {
    suspend fun run(request: BackupRequest, forceDuplicate: Boolean): BackupResult
}

interface CreateAccountRunner {
    fun run(request: CreateNewAccountRequest): Flow<CreateNewAccountResult>
}

interface RestoreRunner {
    suspend fun run(accountId: Long): RestoreResult
}

interface OperationSessionProvider {
    suspend fun currentSessionId(): String
}

interface OperationLogger {
    suspend fun logOperationStart(type: OperationType, sessionId: String)
    suspend fun logStepStart(stepId: OperationStepId)
    suspend fun logStepEnd(stepId: OperationStepId, success: Boolean, detail: String? = null)
    suspend fun logOperationEnd(type: OperationType, status: OperationOverallStatus)
}

interface OperationPreflightDependencies {
    suspend fun requestRootAccess(): Boolean
    suspend fun isMonopolyGoInstalled(): Boolean
    suspend fun isBackupPathWritable(path: String): Boolean
    suspend fun accountExistsByName(name: String): Boolean
    suspend fun accountExistsById(id: Long): Boolean
    suspend fun getBackupPathForAccount(id: Long): String?
}
