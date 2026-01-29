package com.mgomanager.app.domain.operation

import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.di.IoDispatcher
import com.mgomanager.app.domain.usecase.BackupRequest
import com.mgomanager.app.domain.usecase.CreateNewAccountRequest
import com.mgomanager.app.domain.usecase.CreateNewAccountResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationCoordinator @Inject constructor(
    private val backupRunner: BackupRunner,
    private val createAccountRunner: CreateAccountRunner,
    private val restoreRunner: RestoreRunner,
    private val preflightChecker: OperationPreflightChecker,
    private val sessionProvider: OperationSessionProvider,
    private val logger: OperationLogger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val updater = OperationStateUpdater()
    @Volatile private var cancelRequested: Boolean = false

    fun start(type: OperationType, params: OperationParams): Flow<OperationState> = flow {
        cancelRequested = false
        var state = OperationState.initial(type, OperationStepSequence.stepsFor(type))
        val emitState: StateEmitter = { updated -> emit(updated) }
        emitState(state)

        val sessionId = sessionProvider.currentSessionId()
        state = updater.startOperation(state, sessionId)
        logger.logOperationStart(type, sessionId)
        emitState(state)

        state = when (type) {
            OperationType.BACKUP -> executeBackup(state, params as OperationParams.Backup, emitState)
            OperationType.CREATE_ACCOUNT -> executeCreateAccount(state, params as OperationParams.CreateAccount, emitState)
            OperationType.RESTORE -> executeRestore(state, params as OperationParams.Restore, emitState)
            else -> failUnsupported(state, type, emitState)
        }

        emitState(state)
    }.flowOn(ioDispatcher)

    fun cancel() {
        cancelRequested = true
    }

    private suspend fun executeBackup(
        state: OperationState,
        params: OperationParams.Backup,
        emitState: StateEmitter
    ): OperationState {
        var updated = state

        updated = startAndCheckPreflight(updated, params.request, emitState)
            ?: return finalizeCanceled(updated, OperationErrorCode.Canceled, emitState)

        updated = startAndCompleteStep(updated, OperationStepId.START_LOG_SESSION, emitState)
        if (cancelRequested) return finalizeCanceled(updated, OperationErrorCode.Canceled, emitState)

        updated = startStep(updated, OperationStepId.STOP_APP, emitState)
        val result = backupRunner.run(params.request, params.forceDuplicate)

        return when (result) {
            is BackupResult.Success -> handleBackupSuccess(
                updated,
                result.account.fullName,
                result.account.backupPath,
                emptyList(),
                emitState
            )
            is BackupResult.PartialSuccess -> handleBackupSuccess(
                updated,
                result.account.fullName,
                result.account.backupPath,
                result.missingIds,
                emitState
            )
            is BackupResult.DuplicateUserId -> handleDuplicateUserId(updated, result, emitState)
            is BackupResult.Failure -> handleBackupFailure(updated, result, emitState)
        }
    }

    private suspend fun executeCreateAccount(
        state: OperationState,
        params: OperationParams.CreateAccount,
        emitState: StateEmitter
    ): OperationState {
        var updated = state

        val preflightResult = preflightChecker.checkCreateAccount(params.request)
        updated = handlePreflightResult(updated, preflightResult, emitState)
            ?: return finalizeCanceled(updated, OperationErrorCode.Canceled, emitState)

        updated = startAndCompleteStep(updated, OperationStepId.START_LOG_SESSION, emitState)
        if (cancelRequested) return finalizeCanceled(updated, OperationErrorCode.Canceled, emitState)

        val stepMap = mapOf(
            1 to OperationStepId.PRECHECK,
            2 to OperationStepId.STOP_APP,
            3 to OperationStepId.PREPARE_TARGET,
            4 to OperationStepId.GENERATE_IDS,
            5 to OperationStepId.DB_UPDATE,
            6 to OperationStepId.WRITE_SHARED_FILE
        )

        createAccountRunner.run(params.request).collect { result ->
            updated = when (result) {
                is CreateNewAccountResult.Progress -> {
                    val stepId = stepMap[result.progress.step]
                    if (stepId != null) {
                        updated = startStep(updated, stepId, emitState)
                        updated = completeStep(updated, stepId, emitState)
                    }
                    updated
                }
                is CreateNewAccountResult.Prepared -> {
                    updated = startAndCompleteStep(updated, OperationStepId.FINALIZE, emitState)
                    val finalResult = CreateAccountOperationResult(result.accountId, result.accountName)
                    val finalState = updater.finalize(updated, OperationOverallStatus.SUCCESS, result = finalResult)
                    logger.logOperationEnd(state.type, OperationOverallStatus.SUCCESS)
                    emitState(finalState)
                    finalState
                }
                is CreateNewAccountResult.ValidationError -> {
                    val error = OperationError(
                        code = OperationErrorCode.Unknown,
                        detail = "validation_error",
                        suggestedActions = setOf(OperationActionHint.RENAME_ACCOUNT)
                    )
                    handleFailure(updated, OperationStepId.PRECHECK, error, emitState)
                }
                is CreateNewAccountResult.Failure -> {
                    val error = OperationError(
                        code = OperationErrorCode.Unknown,
                        detail = result.error
                    )
                    handleFailure(updated, OperationStepId.FINALIZE, error, emitState)
                }
            }
        }

        return updated
    }

    private suspend fun executeRestore(
        state: OperationState,
        params: OperationParams.Restore,
        emitState: StateEmitter
    ): OperationState {
        var updated = state

        val preflightResult = preflightChecker.checkRestore(params.accountId)
        updated = handlePreflightResult(updated, preflightResult, emitState)
            ?: return finalizeCanceled(updated, OperationErrorCode.Canceled, emitState)

        updated = startAndCompleteStep(updated, OperationStepId.START_LOG_SESSION, emitState)
        if (cancelRequested) return finalizeCanceled(updated, OperationErrorCode.Canceled, emitState)

        updated = startStep(updated, OperationStepId.STOP_APP, emitState)
        val result = restoreRunner.run(params.accountId)

        return when (result) {
            is RestoreResult.Success -> {
                val steps = listOf(
                    OperationStepId.STOP_APP,
                    OperationStepId.PREPARE_TARGET,
                    OperationStepId.COPY_DATA,
                    OperationStepId.SET_PERMISSIONS,
                    OperationStepId.WRITE_SHARED_FILE,
                    OperationStepId.DB_UPDATE
                )
                updated = completeSteps(updated, steps, emitState)
                updated = startAndCompleteStep(updated, OperationStepId.FINALIZE, emitState)
                updater.finalize(
                    updated,
                    OperationOverallStatus.SUCCESS,
                    result = RestoreOperationResult(result.accountName)
                )
            }
            is RestoreResult.Failure -> {
                val error = OperationError(
                    code = OperationErrorCode.Unknown,
                    detail = result.error
                )
                handleFailure(updated, OperationStepId.STOP_APP, error, emitState)
            }
        }
    }

    private suspend fun failUnsupported(
        state: OperationState,
        type: OperationType,
        emitState: StateEmitter
    ): OperationState {
        val error = OperationError(
            code = OperationErrorCode.Unknown,
            detail = "unsupported_operation_${type.name.lowercase()}"
        )
        val updated = updater.finalize(state, OperationOverallStatus.FAILURE, error = error)
        logger.logOperationEnd(type, OperationOverallStatus.FAILURE)
        emitState(updated)
        return updated
    }

    private suspend fun startAndCheckPreflight(
        state: OperationState,
        request: BackupRequest,
        emitState: StateEmitter
    ): OperationState? {
        var updated = startStep(state, OperationStepId.PRECHECK, emitState)
        val preflightResult = preflightChecker.checkBackup(request)
        return handlePreflightResult(updated, preflightResult, emitState)
    }

    private suspend fun handlePreflightResult(
        state: OperationState,
        result: PreflightResult,
        emitState: StateEmitter
    ): OperationState? {
        return when (result) {
            PreflightResult.Success -> {
                val completed = completeStep(state, OperationStepId.PRECHECK, emitState)
                if (cancelRequested) {
                    finalizeCanceled(completed, OperationErrorCode.Canceled, emitState)
                } else {
                    completed
                }
            }
            is PreflightResult.Failure -> {
                val failed = failStep(state, OperationStepId.PRECHECK, result.error.detail, emitState)
                val canceled = updater.cancelPendingSteps(failed)
                val finalState = updater.finalize(canceled, OperationOverallStatus.FAILURE, error = result.error)
                logger.logOperationEnd(state.type, OperationOverallStatus.FAILURE)
                emitState(finalState)
                finalState
            }
        }
    }

    private suspend fun handleBackupSuccess(
        state: OperationState,
        accountName: String,
        backupPath: String,
        missingIds: List<String>,
        emitState: StateEmitter
    ): OperationState {
        var updated = state
        val steps = listOf(
            OperationStepId.STOP_APP,
            OperationStepId.PREPARE_TARGET,
            OperationStepId.COPY_DATA,
            OperationStepId.READ_IDS,
            OperationStepId.DB_UPDATE
        )
        updated = completeSteps(updated, steps, emitState)
        updated = startAndCompleteStep(updated, OperationStepId.FINALIZE, emitState)
        val overall = if (missingIds.isEmpty()) OperationOverallStatus.SUCCESS else OperationOverallStatus.PARTIAL
        val result = BackupOperationResult(accountName, backupPath, missingIds)
        val finalState = updater.finalize(updated, overall, result = result)
        logger.logOperationEnd(state.type, overall)
        emitState(finalState)
        return finalState
    }

    private suspend fun handleDuplicateUserId(
        state: OperationState,
        result: BackupResult.DuplicateUserId,
        emitState: StateEmitter
    ): OperationState {
        var updated = state
        val successSteps = listOf(
            OperationStepId.STOP_APP,
            OperationStepId.PREPARE_TARGET,
            OperationStepId.COPY_DATA,
            OperationStepId.READ_IDS
        )
        updated = completeSteps(updated, successSteps, emitState)
        updated = failStep(updated, OperationStepId.DB_UPDATE, "duplicate_user_id", emitState)
        updated = updater.cancelPendingSteps(updated)
        val error = OperationError(
            code = OperationErrorCode.DuplicateUserId,
            metadata = mapOf(
                "userId" to result.userId,
                "existingAccountName" to result.existingAccountName
            ),
            suggestedActions = setOf(OperationActionHint.RENAME_ACCOUNT, OperationActionHint.ABORT)
        )
        val finalState = updater.finalize(updated, OperationOverallStatus.FAILURE, error = error)
        logger.logOperationEnd(state.type, OperationOverallStatus.FAILURE)
        emitState(finalState)
        return finalState
    }

    private suspend fun handleBackupFailure(
        state: OperationState,
        result: BackupResult.Failure,
        emitState: StateEmitter
    ): OperationState {
        val error = OperationError(
            code = OperationErrorCode.Unknown,
            detail = result.error
        )
        return handleFailure(state, OperationStepId.STOP_APP, error, emitState)
    }

    private suspend fun handleFailure(
        state: OperationState,
        stepId: OperationStepId,
        error: OperationError,
        emitState: StateEmitter
    ): OperationState {
        var updated = failStep(state, stepId, error.detail, emitState)
        updated = updater.cancelPendingSteps(updated)
        val finalState = updater.finalize(updated, OperationOverallStatus.FAILURE, error = error)
        logger.logOperationEnd(state.type, OperationOverallStatus.FAILURE)
        emitState(finalState)
        return finalState
    }

    private suspend fun startStep(
        state: OperationState,
        stepId: OperationStepId,
        emitState: StateEmitter
    ): OperationState {
        val updated = updater.startStep(state, stepId)
        logger.logStepStart(stepId)
        emitState(updated)
        return updated
    }

    private suspend fun completeStep(
        state: OperationState,
        stepId: OperationStepId,
        emitState: StateEmitter,
        detail: String? = null
    ): OperationState {
        val updated = updater.completeStep(state, stepId, detail)
        logger.logStepEnd(stepId, success = true, detail = detail)
        emitState(updated)
        return updated
    }

    private suspend fun failStep(
        state: OperationState,
        stepId: OperationStepId,
        emitState: StateEmitter,
        detail: String? = null
    ): OperationState {
        val updated = updater.failStep(state, stepId, detail)
        logger.logStepEnd(stepId, success = false, detail = detail)
        emitState(updated)
        return updated
    }

    private suspend fun startAndCompleteStep(
        state: OperationState,
        stepId: OperationStepId,
        emitState: StateEmitter
    ): OperationState {
        val started = startStep(state, stepId, emitState)
        return completeStep(started, stepId, emitState)
    }

    private suspend fun completeSteps(
        state: OperationState,
        steps: List<OperationStepId>,
        emitState: StateEmitter
    ): OperationState {
        var updated = state
        steps.forEach { stepId ->
            updated = completeStep(updated, stepId, emitState)
        }
        return updated
    }

    private suspend fun finalizeCanceled(
        state: OperationState,
        code: OperationErrorCode,
        emitState: StateEmitter
    ): OperationState {
        val canceled = updater.cancelRunningSteps(state)
        val pendingCanceled = updater.cancelPendingSteps(canceled)
        val error = OperationError(code = code)
        val finalState = updater.finalize(pendingCanceled, OperationOverallStatus.CANCELED, error = error)
        logger.logOperationEnd(state.type, OperationOverallStatus.CANCELED)
        emitState(finalState)
        return finalState
    }
}

private typealias StateEmitter = suspend (OperationState) -> Unit

sealed class OperationParams {
    data class Backup(
        val request: BackupRequest,
        val forceDuplicate: Boolean = false
    ) : OperationParams()

    data class CreateAccount(
        val request: CreateNewAccountRequest
    ) : OperationParams()

    data class Restore(
        val accountId: Long
    ) : OperationParams()
}
