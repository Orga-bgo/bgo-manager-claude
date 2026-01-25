package com.mgomanager.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import com.mgomanager.app.ui.theme.StatusGreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var prefixInput by remember { mutableStateOf("") }
    var pathInput by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        prefixInput = uiState.accountPrefix
        pathInput = uiState.backupRootPath
    }

    // Refresh root status when screen is loaded
    LaunchedEffect(Unit) {
        viewModel.refreshRootStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Backup-Konfiguration", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = prefixInput,
                onValueChange = {
                    prefixInput = it
                    viewModel.resetPrefixSaved()
                },
                label = { Text("Accountname-Präfix") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { viewModel.updatePrefix(prefixInput) }) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Speichern",
                            tint = if (uiState.prefixSaved) StatusGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )

            OutlinedTextField(
                value = pathInput,
                onValueChange = {
                    pathInput = it
                    viewModel.resetPathSaved()
                },
                label = { Text("Backup-Pfad") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { viewModel.updateBackupPath(pathInput) }) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Speichern",
                            tint = if (uiState.pathSaved) StatusGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )

            Divider()

            Text("System", style = MaterialTheme.typography.titleMedium)

            OutlinedButton(
                onClick = { navController.navigate(Screen.Logs.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logs anzeigen")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isRootAvailable) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Root-Status")
                    Text(if (uiState.isRootAvailable) "✓ Verfügbar" else "✗ Nicht verfügbar")
                }
            }

            Divider()

            Text("Über", style = MaterialTheme.typography.titleMedium)
            Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium)
            Text("App-Starts: ${uiState.appStartCount}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
