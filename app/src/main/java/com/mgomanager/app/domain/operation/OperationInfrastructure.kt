package com.mgomanager.app.domain.operation

import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.domain.util.RootUtil
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsSessionProvider @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : OperationSessionProvider {
    override suspend fun currentSessionId(): String {
        return settingsDataStore.currentSessionId.first()
    }
}

@Singleton
class OperationLogWriter @Inject constructor(
    private val logRepository: LogRepository
) : OperationLogger {
    override suspend fun logOperationStart(type: OperationType, sessionId: String) {
        logRepository.logInfo(type.name, "OP_START sessionId=$sessionId")
    }

    override suspend fun logStepStart(stepId: OperationStepId) {
        logRepository.logInfo("WORKFLOW", "STEP_START ${stepId.name}")
    }

    override suspend fun logStepEnd(stepId: OperationStepId, success: Boolean, detail: String?) {
        val status = if (success) "success" else "fail"
        val message = buildString {
            append("STEP_END ${stepId.name} status=$status")
            if (!detail.isNullOrBlank()) {
                append(" detail=$detail")
            }
        }
        logRepository.logInfo("WORKFLOW", message)
    }

    override suspend fun logOperationEnd(type: OperationType, status: OperationOverallStatus) {
        logRepository.logInfo(type.name, "OP_END status=${status.name}")
    }
}

@Singleton
class DefaultOperationPreflightDependencies @Inject constructor(
    private val rootUtil: RootUtil,
    private val accountRepository: AccountRepository
) : OperationPreflightDependencies {
    override suspend fun requestRootAccess(): Boolean {
        return rootUtil.requestRootAccess()
    }

    override suspend fun isMonopolyGoInstalled(): Boolean {
        return rootUtil.isMonopolyGoInstalled()
    }

    override suspend fun isBackupPathWritable(path: String): Boolean {
        val escapedPath = path.replace("\"", "\\\"")
        return rootUtil.executeCommand("test -w \"$escapedPath\"").isSuccess
    }

    override suspend fun accountExistsByName(name: String): Boolean {
        return accountRepository.getAccountByName(name) != null
    }

    override suspend fun accountExistsById(id: Long): Boolean {
        return accountRepository.getAccountById(id) != null
    }

    override suspend fun getBackupPathForAccount(id: Long): String? {
        return accountRepository.getAccountById(id)?.backupPath
    }
}
