package com.mgomanager.app.domain.operation

enum class OperationStepId {
    PRECHECK,
    START_LOG_SESSION,
    STOP_APP,
    PREPARE_TARGET,
    READ_IDS,
    GENERATE_IDS,
    COPY_DATA,
    SET_PERMISSIONS,
    DB_UPDATE,
    WRITE_SHARED_FILE,
    ZIP_BUILD,
    ZIP_VALIDATE,
    ZIP_EXTRACT,
    SSH_TEST,
    SSH_LIST_REMOTE,
    SSH_UPLOAD,
    SSH_DOWNLOAD,
    FINALIZE
}
