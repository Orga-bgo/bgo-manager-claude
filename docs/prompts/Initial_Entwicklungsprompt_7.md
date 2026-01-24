# Initial Development Prompt 7: Settings & Logging System

## Übersicht
Implementation des Settings-Screens mit DataStore-Integration und Log-Display mit Session-Gruppierung.

## Voraussetzungen
- Erfolgreicher Abschluss von Prompt 6
- Funktionierende Detail Screen und Dialoge
- CLAUDE.md zur Hand

---

## Aufgaben

### 1. Settings ViewModel

**Datei:** `ui/screens/settings/SettingsViewModel.kt`

```kotlin
package com.mgomanager.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.domain.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val accountPrefix: String = "MGO_",
    val backupRootPath: String = "/storage/emulated/0/mgo/backups/",
    val isRootAvailable: Boolean = false,
    val appStartCount: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val rootUtil: RootUtil
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                settingsDataStore.accountPrefix,
                settingsDataStore.backupRootPath,
                settingsDataStore.appStartCount
            ) { prefix, path, count ->
                Triple(prefix, path, count)
            }.collect { (prefix, path, count) ->
                _uiState.update {
                    it.copy(
                        accountPrefix = prefix,
                        backupRootPath = path,
                        appStartCount = count
                    )
                }
            }
            
            val isRooted = rootUtil.isRooted()
            _uiState.update { it.copy(isRootAvailable = isRooted) }
        }
    }
    
    fun updatePrefix(prefix: String) {
        viewModelScope.launch {
            settingsDataStore.setAccountPrefix(prefix)
        }
    }
    
    fun updateBackupPath(path: String) {
        viewModelScope.launch {
            settingsDataStore.setBackupRootPath(path)
        }
    }
}
```

### 2. Settings Screen

**Datei:** `ui/screens/settings/SettingsScreen.kt`

```kotlin
package com.mgomanager.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
                onValueChange = { prefixInput = it },
                label = { Text("Accountname-Präfix") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { viewModel.updatePrefix(prefixInput) }) {
                        Icon(Icons.Default.Check, contentDescription = "Speichern")
                    }
                }
            )
            
            OutlinedTextField(
                value = pathInput,
                onValueChange = { pathInput = it },
                label = { Text("Backup-Pfad") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { viewModel.updateBackupPath(pathInput) }) {
                        Icon(Icons.Default.Check, contentDescription = "Speichern")
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
```

### 3. Log ViewModel

**Datei:** `ui/screens/logs/LogViewModel.kt`

```kotlin
package com.mgomanager.app.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.local.database.entities.LogEntity
import com.mgomanager.app.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionLogs(
    val sessionId: String,
    val logs: List<LogEntity>
)

data class LogUiState(
    val sessionLogs: List<SessionLogs> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()
    
    init {
        loadLogs()
    }
    
    private fun loadLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            logRepository.getLastFiveSessionsLogs()
                .map { logs ->
                    // Group logs by session
                    logs.groupBy { it.sessionId }
                        .map { (sessionId, sessionLogs) ->
                            SessionLogs(sessionId, sessionLogs.sortedBy { it.timestamp })
                        }
                        .sortedByDescending { it.logs.firstOrNull()?.timestamp ?: 0 }
                }
                .collect { sessionLogs ->
                    _uiState.update { it.copy(sessionLogs = sessionLogs, isLoading = false) }
                }
        }
    }
    
    fun clearAllLogs() {
        viewModelScope.launch {
            logRepository.deleteAllLogs()
        }
    }
}
```

### 4. Log Screen

**Datei:** `ui/screens/logs/LogScreen.kt`

```kotlin
package com.mgomanager.app.ui.screens.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.data.local.database.entities.getLevelColor
import com.mgomanager.app.data.local.database.entities.getFormattedTimestamp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    navController: NavController,
    viewModel: LogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Alle löschen")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState.sessionLogs) { sessionLog ->
                SessionLogCard(sessionLog)
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Alle Logs löschen?") },
            text = { Text("Diese Aktion kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllLogs()
                    showDeleteDialog = false
                }) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
fun SessionLogCard(sessionLog: SessionLogs) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Session header
            val firstLog = sessionLog.logs.firstOrNull()
            val sessionTime = firstLog?.let {
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
                sdf.format(Date(it.timestamp))
            } ?: "Unbekannt"
            
            Text(
                text = "Session: $sessionTime",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${sessionLog.logs.size} Einträge",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                
                // Log entries
                sessionLog.logs.forEach { log ->
                    LogEntry(log)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun LogEntry(log: com.mgomanager.app.data.local.database.entities.LogEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = log.getFormattedTimestamp(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = "[${log.level}]",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = log.getLevelColor()
        )
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}
```

---

## Testing

### Manual Tests
- [ ] Settings Screen zeigt aktuelle Einstellungen
- [ ] Prefix kann geändert werden
- [ ] Backup-Pfad kann geändert werden
- [ ] Root-Status wird korrekt angezeigt
- [ ] Navigation zu Logs funktioniert
- [ ] Log Screen zeigt letzte 5 Sessions
- [ ] Logs können erweitert/eingeklappt werden
- [ ] Log-Level werden farblich dargestellt
- [ ] Alle Logs können gelöscht werden

---

## Abschlusskriterien

- [ ] Settings Screen vollständig implementiert
- [ ] DataStore-Integration funktioniert
- [ ] Log Screen zeigt gruppierte Sessions
- [ ] Logs können gelöscht werden
- [ ] Root-Status wird angezeigt
- [ ] Navigation funktioniert
- [ ] App ist vollständig funktionsfähig

---

## Final Testing Checklist

**Kompletter App-Flow:**
1. [ ] App startet ohne Fehler
2. [ ] Root-Check funktioniert
3. [ ] Permissions werden korrekt abgefragt
4. [ ] Home Screen zeigt Statistics
5. [ ] Neues Backup erstellen funktioniert komplett
6. [ ] Account erscheint in Liste
7. [ ] Detail-Ansicht zeigt alle Daten
8. [ ] Restore funktioniert komplett
9. [ ] Edit funktioniert
10. [ ] Delete funktioniert
11. [ ] Settings können geändert werden
12. [ ] Logs werden korrekt angezeigt
13. [ ] App läuft stabil auf Android 9, 12, 15

**Gratulation! Die MGO Manager App ist fertig!**

---

*Entwicklungsprompt 7/7 - Erstellt für MGO Manager Projekt*
