package com.mgomanager.app.data.model

/**
 * Generic progress state for long-running operations like export and import.
 * Used to provide visual feedback to users during these operations.
 */
data class OperationProgress(
    val currentStep: Int,
    val totalSteps: Int,
    val stepDescription: String,
    val percentComplete: Float,  // 0.0 to 1.0
    val isIndeterminate: Boolean = false,
    val currentFile: String? = null,
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0
)

/**
 * Progress state for export operations.
 */
sealed class ExportProgress {
    data class InProgress(val progress: OperationProgress) : ExportProgress()
    data class Success(val filePath: String) : ExportProgress()
    data class Error(val message: String) : ExportProgress()
}

/**
 * Progress state for import operations.
 */
sealed class ImportProgress {
    data class InProgress(val progress: OperationProgress) : ImportProgress()
    data class Success(val message: String) : ImportProgress()
    data class Error(val message: String) : ImportProgress()
}
