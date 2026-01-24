package com.mgomanager.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.ui.components.AccountCard
import com.mgomanager.app.ui.components.BackupDialog
import com.mgomanager.app.ui.components.StatisticsCard
import com.mgomanager.app.ui.navigation.Screen
import com.mgomanager.app.ui.theme.StatusGreen
import com.mgomanager.app.ui.theme.StatusOrange
import com.mgomanager.app.ui.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MGO Manager") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showBackupDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "Neues Backup")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
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

            // Account list (full width)
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.accounts) { account ->
                    AccountCard(
                        account = account,
                        onCardClick = {
                            navController.navigate(Screen.Detail.createRoute(account.id))
                        },
                        onRestoreClick = {
                            viewModel.showRestoreConfirm(account.id)
                        }
                    )
                }
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
        }
    }

    // Show restore confirmation
    uiState.showRestoreConfirm?.let { accountId ->
        val account = uiState.accounts.find { it.id == accountId }
        AlertDialog(
            onDismissRequest = { viewModel.hideRestoreConfirm() },
            title = { Text("Wiederherstellung") },
            text = { Text("Account '${account?.fullName}' wiederherstellen?") },
            confirmButton = {
                TextButton(onClick = { viewModel.restoreAccount(accountId) }) {
                    Text("Ja")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRestoreConfirm() }) {
                    Text("Nein")
                }
            }
        )
    }

    // Show restore result
    uiState.restoreResult?.let { result ->
        when (result) {
            is com.mgomanager.app.data.model.RestoreResult.Success -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearRestoreResult() },
                    title = { Text("Wiederherstellung erfolgreich!") },
                    text = { Text("Account '${result.accountName}' wurde erfolgreich wiederhergestellt.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearRestoreResult() }) {
                            Text("OK")
                        }
                    }
                )
            }
            is com.mgomanager.app.data.model.RestoreResult.Failure -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearRestoreResult() },
                    title = { Text("Wiederherstellung fehlgeschlagen") },
                    text = { Text(result.error) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearRestoreResult() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}
