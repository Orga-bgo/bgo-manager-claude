package com.mgomanager.app.domain.operation

class OperationStateUpdater {

    fun startOperation(state: OperationState, logSessionId: String): OperationState {
        return state.copy(
            overall = OperationOverallStatus.RUNNING,
            canCancel = true,
            logSessionId = logSessionId
        )
    }

    fun startStep(state: OperationState, stepId: OperationStepId, detail: String? = null): OperationState {
        return updateStep(state, stepId) { step ->
            step.copy(
                status = OperationStepStatus.RUNNING,
                detail = detail ?: step.detail,
                startedAt = step.startedAt ?: System.currentTimeMillis()
            )
        }
    }

    fun completeStep(state: OperationState, stepId: OperationStepId, detail: String? = null): OperationState {
        return updateStep(state, stepId) { step ->
            step.copy(
                status = OperationStepStatus.SUCCESS,
                detail = detail ?: step.detail,
                endedAt = System.currentTimeMillis()
            )
        }
    }

    fun failStep(state: OperationState, stepId: OperationStepId, detail: String? = null): OperationState {
        return updateStep(state, stepId) { step ->
            step.copy(
                status = OperationStepStatus.FAIL,
                detail = detail ?: step.detail,
                endedAt = System.currentTimeMillis()
            )
        }
    }

    fun cancelRunningSteps(state: OperationState): OperationState {
        return state.copy(
            steps = state.steps.map { step ->
                if (step.status == OperationStepStatus.RUNNING) {
                    step.copy(status = OperationStepStatus.CANCELED, endedAt = System.currentTimeMillis())
                } else {
                    step
                }
            }
        )
    }

    fun cancelPendingSteps(state: OperationState): OperationState {
        return state.copy(
            steps = state.steps.map { step ->
                if (step.status == OperationStepStatus.PENDING) {
                    step.copy(status = OperationStepStatus.CANCELED)
                } else {
                    step
                }
            }
        )
    }

    fun finalize(
        state: OperationState,
        overall: OperationOverallStatus,
        result: OperationResult? = null,
        error: OperationError? = null
    ): OperationState {
        return state.copy(
            overall = overall,
            result = result,
            error = error,
            canCancel = false
        )
    }

    private fun updateStep(
        state: OperationState,
        stepId: OperationStepId,
        update: (OperationStep) -> OperationStep
    ): OperationState {
        return state.copy(
            steps = state.steps.map { step ->
                if (step.id == stepId) {
                    update(step)
                } else {
                    step
                }
            }
        )
    }
}
