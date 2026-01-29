package com.mgomanager.app.data.repository

import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.domain.usecase.BackupRequest
import com.mgomanager.app.domain.usecase.CreateBackupUseCase
import com.mgomanager.app.domain.usecase.RestoreBackupUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val createBackupUseCase: CreateBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase
) {

    suspend fun createBackup(request: BackupRequest, forceDuplicate: Boolean = false): BackupResult {
        return createBackupUseCase.execute(request, forceDuplicate)
    }

    suspend fun restoreBackup(accountId: Long): RestoreResult {
        return restoreBackupUseCase.execute(accountId)
    }
}
