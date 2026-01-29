package com.mgomanager.app.domain.operation

import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.domain.usecase.BackupRequest
import com.mgomanager.app.domain.usecase.CreateNewAccountRequest
import com.mgomanager.app.domain.usecase.CreateNewAccountResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationCoordinatorTest {

    @Test
    fun `duplicate user id maps to failure with suggested actions`() = runBlocking {
        val coordinator = OperationCoordinator(
            backupRunner = FakeBackupRunner(
                BackupResult.DuplicateUserId("user-1", "Existing")
            ),
            createAccountRunner = FakeCreateRunner(),
            restoreRunner = FakeRestoreRunner(),
            preflightChecker = OperationPreflightChecker(FakePreflightDependencies()),
            sessionProvider = FakeSessionProvider(),
            logger = FakeLogger(),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )

        val request = BackupRequest(
            accountName = "Test",
            prefix = "MGO_",
            backupRootPath = "/tmp/",
            hasFacebookLink = false
        )

        val states = coordinator.start(
            OperationType.BACKUP,
            OperationParams.Backup(request)
        ).toList()

        val finalState = states.last()
        assertEquals(OperationOverallStatus.FAILURE, finalState.overall)
        assertEquals(OperationErrorCode.DuplicateUserId, finalState.error?.code)
        assertTrue(finalState.error?.suggestedActions?.contains(OperationActionHint.RENAME_ACCOUNT) == true)
        assertTrue(finalState.error?.suggestedActions?.contains(OperationActionHint.ABORT) == true)
    }

    private class FakeBackupRunner(
        private val result: BackupResult
    ) : BackupRunner {
        override suspend fun run(request: BackupRequest, forceDuplicate: Boolean): BackupResult = result
    }

    private class FakeCreateRunner : CreateAccountRunner {
        override fun run(request: CreateNewAccountRequest): Flow<CreateNewAccountResult> = flow { }
    }

    private class FakeRestoreRunner : RestoreRunner {
        override suspend fun run(accountId: Long): RestoreResult {
            return RestoreResult.Success("Account")
        }
    }

    private class FakeSessionProvider : OperationSessionProvider {
        override suspend fun currentSessionId(): String = "session"
    }

    private class FakeLogger : OperationLogger {
        override suspend fun logOperationStart(type: OperationType, sessionId: String) = Unit
        override suspend fun logStepStart(stepId: OperationStepId) = Unit
        override suspend fun logStepEnd(stepId: OperationStepId, success: Boolean, detail: String?) = Unit
        override suspend fun logOperationEnd(type: OperationType, status: OperationOverallStatus) = Unit
    }

    private class FakePreflightDependencies : OperationPreflightDependencies {
        override suspend fun requestRootAccess(): Boolean = true
        override suspend fun isMonopolyGoInstalled(): Boolean = true
        override suspend fun isBackupPathWritable(path: String): Boolean = true
        override suspend fun accountExistsByName(name: String): Boolean = false
        override suspend fun accountExistsById(id: Long): Boolean = true
        override suspend fun getBackupPathForAccount(id: Long): String? = "/tmp/"
    }
}
