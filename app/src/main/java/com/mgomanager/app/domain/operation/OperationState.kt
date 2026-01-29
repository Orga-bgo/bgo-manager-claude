package com.mgomanager.app.domain.operation

enum class OperationOverallStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILURE,
    CANCELED
}

data class OperationState(
    val type: OperationType,
    val steps: List<OperationStep>,
    val overall: OperationOverallStatus = OperationOverallStatus.IDLE,
    val result: OperationResult? = null,
    val error: OperationError? = null,
    val canCancel: Boolean = false,
    val logSessionId: String? = null
) {
    companion object {
        fun initial(type: OperationType, steps: List<OperationStep>): OperationState {
            return OperationState(type = type, steps = steps)
        }
    }
}
