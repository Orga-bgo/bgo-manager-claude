package com.mgomanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BackupDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        accountName: String,
        hasFacebookLink: Boolean,
        fbUsername: String?,
        fbPassword: String?,
        fb2FA: String?,
        fbTempMail: String?
    ) -> Unit
) {
    var accountName by remember { mutableStateOf("") }
    var hasFacebookLink by remember { mutableStateOf(false) }
    var fbUsername by remember { mutableStateOf("") }
    var fbPassword by remember { mutableStateOf("") }
    var fb2FA by remember { mutableStateOf("") }
    var fbTempMail by remember { mutableStateOf("") }
    var showFbFields by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Backup erstellen") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!showFbFields) {
                    // Step 1: Basic info
                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("Accountname") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Facebook-Verbindung:")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            RadioButton(
                                selected = hasFacebookLink,
                                onClick = { hasFacebookLink = true }
                            )
                            Text("Ja")
                        }
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            RadioButton(
                                selected = !hasFacebookLink,
                                onClick = { hasFacebookLink = false }
                            )
                            Text("Nein")
                        }
                    }
                } else {
                    // Step 2: Facebook details
                    OutlinedTextField(
                        value = fbUsername,
                        onValueChange = { fbUsername = it },
                        label = { Text("Nutzername") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fbPassword,
                        onValueChange = { fbPassword = it },
                        label = { Text("Passwort") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fb2FA,
                        onValueChange = { fb2FA = it },
                        label = { Text("2FA-Code") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fbTempMail,
                        onValueChange = { fbTempMail = it },
                        label = { Text("Temp-Mail") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!showFbFields && hasFacebookLink) {
                        showFbFields = true
                    } else {
                        onConfirm(
                            accountName,
                            hasFacebookLink,
                            if (hasFacebookLink) fbUsername else null,
                            if (hasFacebookLink) fbPassword else null,
                            if (hasFacebookLink) fb2FA else null,
                            if (hasFacebookLink) fbTempMail else null
                        )
                    }
                },
                enabled = accountName.isNotBlank()
            ) {
                Text(if (!showFbFields && hasFacebookLink) "Weiter" else "Backup starten")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (showFbFields) {
                    showFbFields = false
                } else {
                    onDismiss()
                }
            }) {
                Text(if (showFbFields) "Zurück" else "Abbrechen")
            }
        }
    )
}
