package com.mgomanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mgomanager.app.data.model.OperationProgress

/**
 * Dialog that displays progress for long-running operations like export and import.
 * Shows step counter, current operation, progress bar, and file details.
 */
@Composable
fun OperationProgressDialog(
    title: String,
    progress: OperationProgress,
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismissal during operation */ },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Step counter
                Text(
                    text = "Schritt ${progress.currentStep} von ${progress.totalSteps}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Current operation description
                Text(
                    text = progress.stepDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                // Progress bar
                if (progress.isIndeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        progress = progress.percentComplete,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Percentage
                if (!progress.isIndeterminate) {
                    Text(
                        text = "${(progress.percentComplete * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.End)
                    )
                }

                // File progress details (if available)
                if (progress.totalFiles > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Dateien:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${progress.filesProcessed} / ${progress.totalFiles}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            // Current file name
                            progress.currentFile?.let { fileName ->
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // No button during operation - prevents accidental dismissal
        }
    )
}
