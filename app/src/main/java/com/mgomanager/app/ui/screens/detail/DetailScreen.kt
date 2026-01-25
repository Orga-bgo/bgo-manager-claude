package com.mgomanager.app.ui.screens.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.data.model.SusLevel
import com.mgomanager.app.ui.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavController,
    accountId: Long,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(accountId) {
        viewModel.loadAccount(accountId)
    }

    uiState.account?.let { account ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Blue header section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(16.dp)
            ) {
                Column {
                    // Account name (large, italic style)
                    Text(
                        text = account.fullName,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // User ID
                    Text(
                        text = "MoGo User ID: ${account.userId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Last played
                    Text(
                        text = "Zuletzt gespielt:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                    Text(
                        text = account.getFormattedLastPlayedAt(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Restore button (blue)
                    Button(
                        onClick = { viewModel.showRestoreDialog() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "WIEDERHERS\nTELLEN",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 2
                        )
                    }

                    // Edit button (gray)
                    Button(
                        onClick = { viewModel.showEditDialog() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Gray
                        )
                    ) {
                        Text("EDIT")
                    }

                    // Delete button (red)
                    Button(
                        onClick = { viewModel.showDeleteDialog() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusRed
                        )
                    ) {
                        Text("LÖSCHEN")
                    }
                }

                // Friendship link card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "FREUNDSCHAFTSLINK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Nicht verfügbar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "FREUNDSCHAFTSCODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "---",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Status card (Suspension / Error)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Suspension",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = account.susLevel.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = if (account.hasError) "ja" else "nein",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Device IDs card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Geräte-IDs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "SSAID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = if (account.ssaid == "nicht vorhanden") "Nicht verfügbar" else account.ssaid,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "GAID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = if (account.gaid == "nicht vorhanden") "Nicht verfügbar" else account.gaid,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "DEVICE ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = if (account.deviceToken == "nicht vorhanden") "Nicht verfügbar" else account.deviceToken,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "APP SET ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = if (account.appSetId == "nicht vorhanden") "Nicht verfügbar" else account.appSetId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Facebook card (only if linked)
                if (account.hasFacebookLink) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Facebook-Verbindung",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "USERNAME",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = account.fbUsername ?: "---",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "PASSWORT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = account.fbPassword ?: "---",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "2FA CODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = account.fb2FA ?: "---",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "TEMP-MAIL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = account.fbTempMail ?: "---",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Backup-Info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Backup-Info",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "ERSTELLT AM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = account.getFormattedCreatedAt(),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "BACKUP-PFAD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = account.backupPath,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "DATEI-EIGENTÜMER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${account.fileOwner}:${account.fileGroup}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "BERECHTIGUNGEN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = account.filePermissions,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Back to list link
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "← Zurück zur Liste",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { navController.popBackStack() }
                        .padding(8.dp)
                )
            }
        }
    }

    // Dialogs
    if (uiState.showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideRestoreDialog() },
            title = { Text("Wiederherstellung") },
            text = { Text("Account '${uiState.account?.fullName}' wiederherstellen?") },
            confirmButton = {
                TextButton(onClick = { viewModel.restoreAccount() }) {
                    Text("Ja")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRestoreDialog() }) {
                    Text("Nein")
                }
            }
        )
    }

    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Löschen") },
            text = { Text("Account '${uiState.account?.fullName}' wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount { navController.popBackStack() }
                }) {
                    Text("Ja")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("Nein")
                }
            }
        )
    }

    // Edit dialog
    if (uiState.showEditDialog) {
        uiState.account?.let { account ->
            var editName by remember { mutableStateOf(account.accountName) }
            var editSusLevel by remember { mutableStateOf(account.susLevel) }
            var editHasError by remember { mutableStateOf(account.hasError) }
            var editFbUsername by remember { mutableStateOf(account.fbUsername ?: "") }
            var editFbPassword by remember { mutableStateOf(account.fbPassword ?: "") }
            var editFb2FA by remember { mutableStateOf(account.fb2FA ?: "") }
            var editFbTempMail by remember { mutableStateOf(account.fbTempMail ?: "") }
            var susDropdownExpanded by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { viewModel.hideEditDialog() },
                title = { Text("Account bearbeiten") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Sus Level dropdown
                        ExposedDropdownMenuBox(
                            expanded = susDropdownExpanded,
                            onExpandedChange = { susDropdownExpanded = !susDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = editSusLevel.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sus Level") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = susDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = susDropdownExpanded,
                                onDismissRequest = { susDropdownExpanded = false }
                            ) {
                                SusLevel.values().forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level.displayName) },
                                        onClick = {
                                            editSusLevel = level
                                            susDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Error checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = editHasError,
                                onCheckedChange = { editHasError = it }
                            )
                            Text("Hat Error")
                        }

                        if (account.hasFacebookLink) {
                            Divider()
                            Text("Facebook", style = MaterialTheme.typography.labelMedium)

                            OutlinedTextField(
                                value = editFbUsername,
                                onValueChange = { editFbUsername = it },
                                label = { Text("Username") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editFbPassword,
                                onValueChange = { editFbPassword = it },
                                label = { Text("Passwort") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editFb2FA,
                                onValueChange = { editFb2FA = it },
                                label = { Text("2FA") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editFbTempMail,
                                onValueChange = { editFbTempMail = it },
                                label = { Text("Temp-Mail") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateAccount(
                            name = editName,
                            susLevel = editSusLevel,
                            hasError = editHasError,
                            fbUsername = if (account.hasFacebookLink) editFbUsername.ifBlank { null } else null,
                            fbPassword = if (account.hasFacebookLink) editFbPassword.ifBlank { null } else null,
                            fb2FA = if (account.hasFacebookLink) editFb2FA.ifBlank { null } else null,
                            fbTempMail = if (account.hasFacebookLink) editFbTempMail.ifBlank { null } else null
                        )
                    }) {
                        Text("Speichern")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideEditDialog() }) {
                        Text("Abbrechen")
                    }
                }
            )
        }
    }

    // Restore success dialog with app launch option
    if (uiState.showRestoreSuccessDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideRestoreSuccessDialog() },
            title = { Text("Wiederherstellung erfolgreich!") },
            text = { Text("Monopoly Go jetzt starten?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.hideRestoreSuccessDialog()
                    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.scopely.monopolygo")
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    }
                }) {
                    Text("Ja")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRestoreSuccessDialog() }) {
                    Text("Nein")
                }
            }
        )
    }

    // Show restore failure dialog
    uiState.restoreResult?.let { result ->
        if (result is com.mgomanager.app.data.model.RestoreResult.Failure) {
            AlertDialog(
                onDismissRequest = { viewModel.hideRestoreDialog() },
                title = { Text("Wiederherstellung fehlgeschlagen") },
                text = { Text(result.error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.hideRestoreDialog() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
