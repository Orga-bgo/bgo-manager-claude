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

    // ============================================================
    // SSAID Capture Mode (Fallback when settings_ssaid.xml is missing)
    // ============================================================

    /**
     * Check if SSAID file exists.
     */
    fun isSsaidAvailable(): Boolean {
        return createBackupUseCase.isSsaidAvailable()
    }

    /**
     * Enable capture mode for SSAID fallback.
     */
    suspend fun enableSsaidCaptureMode(): Result<Unit> {
        return createBackupUseCase.enableCaptureMode()
    }

    /**
     * Disable capture mode.
     */
    suspend fun disableSsaidCaptureMode(): Result<Unit> {
        return createBackupUseCase.disableCaptureMode()
    }

    /**
     * Start Monopoly GO for SSAID capture.
     */
    suspend fun startMonopolyGo(): Result<Unit> {
        return createBackupUseCase.startMonopolyGo()
    }

    /**
     * Read captured SSAID from the capture file.
     */
    suspend fun readCapturedSsaid(): String? {
        return createBackupUseCase.readCapturedSsaid()
    }

    /**
     * Complete the backup with captured SSAID.
     */
    suspend fun completeSsaidCapture(accountId: Long, capturedSsaid: String): Result<Unit> {
        return createBackupUseCase.completeSsaidCapture(accountId, capturedSsaid)
    }
}
