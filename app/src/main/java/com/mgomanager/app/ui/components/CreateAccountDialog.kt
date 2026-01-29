package com.mgomanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dialog for creating a new Monopoly GO account.
 * Shows account name input with validation and a warning about data deletion.
 */
@Composable
fun CreateAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (accountName: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var accountName by remember { mutableStateOf("") }
    var hasAcknowledgedWarning by remember { mutableStateOf(false) }
    var showValidationError by remember { mutableStateOf(false) }

    val nameError = when {
        accountName.isBlank() -> "Account-Name darf nicht leer sein"
        accountName.length < 3 -> "Mindestens 3 Zeichen erforderlich"
        accountName.length > 30 -> "Maximal 30 Zeichen erlaubt"
        !accountName.matches(Regex("^[a-zA-Z0-9_-]+$")) -> "Nur Buchstaben, Zahlen, _ und - erlaubt"
        else -> null
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = "Neuen Account erstellen",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Warning card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "WARNUNG",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Alle aktuellen Monopoly Go Spieldaten werden unwiderruflich geloescht! Erstelle vorher ein Backup, falls noetig.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Acknowledgment checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = hasAcknowledgedWarning,
                        onCheckedChange = { hasAcknowledgedWarning = it },
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ich habe verstanden",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Account name input
                OutlinedTextField(
                    value = accountName,
                    onValueChange = {
                        accountName = it
                        showValidationError = false
                    },
                    label = { Text("Account-Name") },
                    placeholder = { Text("z.B. Hauptaccount") },
                    singleLine = true,
                    isError = (showValidationError && nameError != null) || errorMessage != null,
                    supportingText = {
                        when {
                            errorMessage != null -> Text(errorMessage)
                            showValidationError && nameError != null -> Text(nameError)
                            else -> Text("${accountName.length}/30 Zeichen")
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                // Loading indicator
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Account wird erstellt...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nameError != null) {
                        showValidationError = true
                    } else if (hasAcknowledgedWarning) {
                        onConfirm(accountName)
                    }
                },
                enabled = !isLoading && hasAcknowledgedWarning && accountName.isNotBlank()
            ) {
                Text("Erstellen")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Abbrechen")
            }
        }
    )
}
