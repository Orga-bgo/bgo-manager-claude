package com.mgomanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.domain.util.PermissionManager
import com.mgomanager.app.domain.util.RootUtil
import com.mgomanager.app.domain.util.SSHSyncService
import com.mgomanager.app.domain.util.ServerBackupCheckResult
import com.mgomanager.app.ui.navigation.AppNavGraph
import com.mgomanager.app.ui.theme.MGOManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var rootUtil: RootUtil

    @Inject
    lateinit var permissionManager: PermissionManager

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var sshSyncService: SSHSyncService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MGOManagerTheme {
                val navController = rememberNavController()

                var isReady by remember { mutableStateOf(false) }
                var showPermissionDialog by remember { mutableStateOf(false) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var retryCounter by remember { mutableIntStateOf(0) }

                // SSH server backup check state
                var showServerBackupDialog by remember { mutableStateOf(false) }
                var serverBackupDate by remember { mutableStateOf("") }

                // Function to check prerequisites
                suspend fun checkPrerequisites() {
                    errorMessage = null
                    isReady = false

                    val isRooted = rootUtil.requestRootAccess()
                    val hasPermissions = permissionManager.hasStoragePermissions()
                    val isMGOInstalled = if (isRooted) rootUtil.isMonopolyGoInstalled() else false

                    when {
                        !isRooted -> errorMessage = "Root-Zugriff erforderlich"
                        !hasPermissions -> showPermissionDialog = true
                        !isMGOInstalled -> errorMessage = "Monopoly Go nicht installiert"
                        else -> {
                            isReady = true

                            // Check for newer server backup if auto-check is enabled
                            val autoCheckEnabled = settingsDataStore.sshAutoCheckOnStart.first()
                            if (autoCheckEnabled) {
                                val serverResult = sshSyncService.checkLatestServerBackup()
                                if (serverResult is ServerBackupCheckResult.Found) {
                                    val localDate = sshSyncService.getLatestLocalBackupDate()
                                    if (localDate == null || serverResult.date.after(localDate)) {
                                        serverBackupDate = sshSyncService.formatDate(serverResult.date)
                                        showServerBackupDialog = true
                                    }
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(retryCounter) {
                    checkPrerequisites()
                }

                // Recheck permissions when resuming from settings
                DisposableEffect(Unit) {
                    val listener = object : android.app.Application.ActivityLifecycleCallbacks {
                        override fun onActivityResumed(activity: android.app.Activity) {
                            if (activity == this@MainActivity && showPermissionDialog) {
                                if (permissionManager.hasStoragePermissions()) {
                                    showPermissionDialog = false
                                    if (errorMessage == null) {
                                        isReady = true
                                    }
                                }
                            }
                        }
                        override fun onActivityPaused(activity: android.app.Activity) {}
                        override fun onActivityStarted(activity: android.app.Activity) {}
                        override fun onActivityStopped(activity: android.app.Activity) {}
                        override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
                        override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
                        override fun onActivityDestroyed(activity: android.app.Activity) {}
                    }
                    application.registerActivityLifecycleCallbacks(listener)
                    onDispose {
                        application.unregisterActivityLifecycleCallbacks(listener)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        errorMessage != null -> {
                            // Show error dialog with retry option
                            AlertDialog(
                                onDismissRequest = { },
                                title = { Text("Fehler") },
                                text = { Text(errorMessage!!) },
                                confirmButton = {
                                    TextButton(onClick = { retryCounter++ }) {
                                        Text("Erneut versuchen")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { finish() }) {
                                        Text("Schließen")
                                    }
                                }
                            )
                        }
                        showPermissionDialog -> {
                            // Show permission request dialog
                            AlertDialog(
                                onDismissRequest = { },
                                title = { Text("Berechtigungen erforderlich") },
                                text = {
                                    Text("Diese App benötigt Zugriff auf den Speicher, um Backups zu erstellen. Bitte erteile die erforderlichen Berechtigungen in den Einstellungen.")
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        permissionManager.requestStoragePermissions(this@MainActivity)
                                    }) {
                                        Text("Berechtigungen erteilen")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { finish() }) {
                                        Text("Abbrechen")
                                    }
                                }
                            )
                        }
                        isReady -> {
                            AppNavGraph(navController)

                            // Show server backup dialog if needed
                            if (showServerBackupDialog) {
                                AlertDialog(
                                    onDismissRequest = { showServerBackupDialog = false },
                                    title = { Text("Server-Backup verfügbar") },
                                    text = {
                                        Text("Ein neueres Backup wurde auf dem Server gefunden ($serverBackupDate).\n\nMöchtest du es importieren?")
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showServerBackupDialog = false
                                            // Navigate to settings where import can be triggered
                                            navController.navigate("settings")
                                        }) {
                                            Text("Zu Einstellungen")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showServerBackupDialog = false }) {
                                            Text("Später")
                                        }
                                    }
                                )
                            }
                        }
                        else -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
