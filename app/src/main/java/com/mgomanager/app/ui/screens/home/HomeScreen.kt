package com.mgomanager.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.ui.components.BackupDialog
import com.mgomanager.app.ui.components.StatisticsCard
import com.mgomanager.app.ui.navigation.Screen
import com.mgomanager.app.ui.theme.StatusGreen
import com.mgomanager.app.ui.theme.StatusOrange
import com.mgomanager.app.ui.theme.StatusRed

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var sortDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Blue header section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MGO Manager",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column {
                // Statistics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatisticsCard(
                        title = "GESAMT",
                        count = uiState.totalCount,
                        color = StatusGreen,
                        modifier = Modifier.weight(1f)
                    )
                    StatisticsCard(
                        title = "ERROR",
                        count = uiState.errorCount,
                        color = StatusRed,
                        modifier = Modifier.weight(1f)
                    )
                    StatisticsCard(
                        title = "SUS",
                        count = uiState.susCount,
                        color = StatusOrange,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sort dropdown row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Accounts (${uiState.totalCount})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Box {
                        OutlinedButton(
                            onClick = { sortDropdownExpanded = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = getSortLabel(uiState.sortMode),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Sortierung",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = sortDropdownExpanded,
                            onDismissRequest = { sortDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Name (A-Z)") },
                                onClick = {
                                    viewModel.setSortMode("name")
                                    sortDropdownExpanded = false
                                },
                                leadingIcon = {
                                    if (uiState.sortMode == "name") {
                                        Icon(Icons.Default.Settings, "Selected", modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Erstellt am") },
                                onClick = {
                                    viewModel.setSortMode("created")
                                    sortDropdownExpanded = false
                                },
                                leadingIcon = {
                                    if (uiState.sortMode == "created") {
                                        Icon(Icons.Default.Settings, "Selected", modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Zuletzt gespielt") },
                                onClick = {
                                    viewModel.setSortMode("lastPlayed")
                                    sortDropdownExpanded = false
                                },
                                leadingIcon = {
                                    if (uiState.sortMode == "lastPlayed") {
                                        Icon(Icons.Default.Settings, "Selected", modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Präfix zuerst") },
                                onClick = {
                                    viewModel.setSortMode("prefixFirst")
                                    sortDropdownExpanded = false
                                },
                                leadingIcon = {
                                    if (uiState.sortMode == "prefixFirst") {
                                        Icon(Icons.Default.Settings, "Selected", modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                        }
                    }
                }

                // Account list with header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column {
                        // Table header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Name",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(2f)
                            )
                            Text(
                                text = "Zuletzt gespielt",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(2f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Err",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Sus",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }

                        Divider()

                        // Account rows
                        LazyColumn {
                            items(uiState.accounts) { account ->
                                AccountListItem(
                                    account = account,
                                    onClick = {
                                        navController.navigate(Screen.Detail.createRoute(account.id))
                                    }
                                )
                                Divider()
                            }
                        }
                    }
                }
            }

            // FAB
            FloatingActionButton(
                onClick = { viewModel.showBackupDialog() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Neues Backup")
            }
        }
    }

    // Show backup dialog
    if (uiState.showBackupDialog) {
        BackupDialog(
            onDismiss = { viewModel.hideBackupDialog() },
            onConfirm = { name, hasFb, fbUser, fbPass, fb2fa, fbMail ->
                viewModel.createBackup(name, hasFb, fbUser, fbPass, fb2fa, fbMail)
            }
        )
    }

    // Show backup result
    uiState.backupResult?.let { result ->
        when (result) {
            is BackupResult.Success -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearBackupResult() },
                    title = { Text("Backup erfolgreich!") },
                    text = { Text("Account '${result.account.fullName}' wurde erfolgreich gesichert.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearBackupResult() }) {
                            Text("OK")
                        }
                    }
                )
            }
            is BackupResult.Failure -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearBackupResult() },
                    title = { Text("Backup fehlgeschlagen") },
                    text = { Text(result.error) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearBackupResult() }) {
                            Text("OK")
                        }
                    }
                )
            }
            is BackupResult.PartialSuccess -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearBackupResult() },
                    title = { Text("Backup teilweise erfolgreich") },
                    text = {
                        Text("Backup erstellt, aber folgende IDs fehlen:\n${result.missingIds.joinToString(", ")}")
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearBackupResult() }) {
                            Text("OK")
                        }
                    }
                )
            }
            is BackupResult.DuplicateUserId -> {
                // This case is handled by duplicateUserIdDialog
            }
        }
    }

    // Show duplicate User ID dialog
    uiState.duplicateUserIdDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDuplicateBackup() },
            title = { Text("Doppelte User ID") },
            text = { Text("User ID bereits als '${info.existingAccountName}' vorhanden.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDuplicateBackup() }) {
                    Text("Fortfahren")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDuplicateBackup() }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
fun AccountListItem(
    account: Account,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name column with full User ID
        Column(modifier = Modifier.weight(2f)) {
            Text(
                text = account.fullName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = account.userId,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // Last played column
        Text(
            text = account.getFormattedLastPlayedAt(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.Center
        )

        // Error column
        Text(
            text = if (account.hasError) "Ja" else "Nein",
            style = MaterialTheme.typography.bodySmall,
            color = if (account.hasError) StatusRed else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        // Sus column
        Text(
            text = account.susLevel.value.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = account.susLevel.getColor(),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Get display label for sort mode
 */
private fun getSortLabel(sortMode: String): String = when (sortMode) {
    "name" -> "Name (A-Z)"
    "created" -> "Erstellt am"
    "lastPlayed" -> "Zuletzt gespielt"
    "prefixFirst" -> "Präfix zuerst"
    else -> "Standard"
}
