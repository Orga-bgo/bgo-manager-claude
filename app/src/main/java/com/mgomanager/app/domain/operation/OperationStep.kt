package com.mgomanager.app.domain.operation

enum class OperationStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAIL,
    CANCELED
}

data class OperationStep(
    val id: OperationStepId,
    val status: OperationStepStatus = OperationStepStatus.PENDING,
    val detail: String? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null
)
