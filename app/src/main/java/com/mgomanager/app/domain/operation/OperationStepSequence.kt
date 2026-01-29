package com.mgomanager.app.domain.operation

object OperationStepSequence {
    fun stepsFor(type: OperationType): List<OperationStep> {
        val ids = when (type) {
            OperationType.BACKUP -> listOf(
                OperationStepId.PRECHECK,
                OperationStepId.START_LOG_SESSION,
                OperationStepId.STOP_APP,
                OperationStepId.PREPARE_TARGET,
                OperationStepId.COPY_DATA,
                OperationStepId.READ_IDS,
                OperationStepId.DB_UPDATE,
                OperationStepId.FINALIZE
            )
            OperationType.CREATE_ACCOUNT -> listOf(
                OperationStepId.PRECHECK,
                OperationStepId.START_LOG_SESSION,
                OperationStepId.STOP_APP,
                OperationStepId.PREPARE_TARGET,
                OperationStepId.GENERATE_IDS,
                OperationStepId.DB_UPDATE,
                OperationStepId.WRITE_SHARED_FILE,
                OperationStepId.FINALIZE
            )
            OperationType.RESTORE -> listOf(
                OperationStepId.PRECHECK,
                OperationStepId.START_LOG_SESSION,
                OperationStepId.STOP_APP,
                OperationStepId.PREPARE_TARGET,
                OperationStepId.COPY_DATA,
                OperationStepId.SET_PERMISSIONS,
                OperationStepId.WRITE_SHARED_FILE,
                OperationStepId.DB_UPDATE,
                OperationStepId.FINALIZE
            )
            OperationType.EXPORT -> listOf(
                OperationStepId.PRECHECK,
                OperationStepId.START_LOG_SESSION,
                OperationStepId.ZIP_BUILD,
                OperationStepId.ZIP_VALIDATE,
                OperationStepId.SSH_TEST,
                OperationStepId.SSH_UPLOAD,
                OperationStepId.FINALIZE
            )
            OperationType.IMPORT -> listOf(
                OperationStepId.PRECHECK,
                OperationStepId.START_LOG_SESSION,
                OperationStepId.ZIP_VALIDATE,
                OperationStepId.ZIP_EXTRACT,
                OperationStepId.DB_UPDATE,
                OperationStepId.COPY_DATA,
                OperationStepId.FINALIZE
            )
            OperationType.SSH_TEST -> listOf(
                OperationStepId.PRECHECK,
                OperationStepId.START_LOG_SESSION,
                OperationStepId.SSH_TEST,
                OperationStepId.SSH_LIST_REMOTE,
                OperationStepId.FINALIZE
            )
            OperationType.SSH_SYNC -> listOf(
                OperationStepId.PRECHECK,
                OperationStepId.START_LOG_SESSION,
                OperationStepId.SSH_TEST,
                OperationStepId.SSH_LIST_REMOTE,
                OperationStepId.SSH_DOWNLOAD,
                OperationStepId.ZIP_VALIDATE,
                OperationStepId.FINALIZE
            )
        }
        return ids.map { OperationStep(id = it) }
    }
}
