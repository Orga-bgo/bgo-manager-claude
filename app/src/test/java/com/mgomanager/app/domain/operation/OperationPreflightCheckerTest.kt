package com.mgomanager.app.domain.operation

import com.mgomanager.app.domain.usecase.BackupRequest
import com.mgomanager.app.domain.usecase.CreateNewAccountRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationPreflightCheckerTest {

    @Test
    fun `backup preflight fails when root denied`() = runBlocking {
        val checker = OperationPreflightChecker(
            FakePreflightDependencies(rootAccess = false)
        )
        val request = BackupRequest(
            accountName = "Test",
            prefix = "MGO_",
            backupRootPath = "/tmp/",
            hasFacebookLink = false
        )

        val result = checker.checkBackup(request) as PreflightResult.Failure
        assertEquals(OperationErrorCode.RootDenied, result.error.code)
        assertTrue(result.error.suggestedActions.contains(OperationActionHint.CHECK_ROOT))
    }

    @Test
    fun `create account preflight fails on invalid name`() = runBlocking {
        val checker = OperationPreflightChecker(FakePreflightDependencies())
        val request = CreateNewAccountRequest(accountName = "", prefix = "MGO_")

        val result = checker.checkCreateAccount(request) as PreflightResult.Failure
        assertEquals(OperationErrorCode.Unknown, result.error.code)
        assertEquals("name_blank", result.error.detail)
    }

    @Test
    fun `backup path writable validation returns unknown error`() = runBlocking {
        val checker = OperationPreflightChecker(
            FakePreflightDependencies(pathWritable = false)
        )
        val request = BackupRequest(
            accountName = "Test",
            prefix = "MGO_",
            backupRootPath = "/tmp/",
            hasFacebookLink = false
        )

        val result = checker.checkBackup(request) as PreflightResult.Failure
        assertEquals(OperationErrorCode.Unknown, result.error.code)
        assertEquals("backup_path_not_writable", result.error.detail)
    }

    private class FakePreflightDependencies(
        private val rootAccess: Boolean = true,
        private val packageInstalled: Boolean = true,
        private val pathWritable: Boolean = true,
        private val nameExists: Boolean = false,
        private val accountExists: Boolean = true,
        private val backupPath: String? = "/tmp/"
    ) : OperationPreflightDependencies {
        override suspend fun requestRootAccess(): Boolean = rootAccess
        override suspend fun isMonopolyGoInstalled(): Boolean = packageInstalled
        override suspend fun isBackupPathWritable(path: String): Boolean = pathWritable
        override suspend fun accountExistsByName(name: String): Boolean = nameExists
        override suspend fun accountExistsById(id: Long): Boolean = accountExists
        override suspend fun getBackupPathForAccount(id: Long): String? = backupPath
    }
}
