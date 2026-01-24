# Initial Development Prompt 6: UI - Detail Screen & Dialoge

## Übersicht
Implementation der Detail-Ansicht, Backup-Dialog, Restore-Confirmation und Edit-Funktionalität.

## Voraussetzungen
- Erfolgreicher Abschluss von Prompt 5
- Funktionierende Home Screen UI
- CLAUDE.md zur Hand

---

## Aufgaben

### 1. Detail ViewModel

**Datei:** `ui/screens/detail/DetailViewModel.kt`

```kotlin
package com.mgomanager.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.model.SusLevel
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val account: Account? = null,
    val isLoading: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showRestoreDialog: Boolean = false,
    val restoreResult: RestoreResult? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
    
    fun loadAccount(accountId: Long) {
        viewModelScope.launch {
            accountRepository.getAccountByIdFlow(accountId).collect { account ->
                _uiState.update { it.copy(account = account) }
            }
        }
    }
    
    fun showRestoreDialog() {
        _uiState.update { it.copy(showRestoreDialog = true) }
    }
    
    fun hideRestoreDialog() {
        _uiState.update { it.copy(showRestoreDialog = false, restoreResult = null) }
    }
    
    fun restoreAccount() {
        viewModelScope.launch {
            val account = _uiState.value.account ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            
            val result = backupRepository.restoreBackup(account.id)
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    restoreResult = result,
                    showRestoreDialog = false
                ) 
            }
        }
    }
    
    fun showEditDialog() {
        _uiState.update { it.copy(showEditDialog = true) }
    }
    
    fun hideEditDialog() {
        _uiState.update { it.copy(showEditDialog = false) }
    }
    
    fun updateAccount(
        name: String,
        susLevel: SusLevel,
        hasError: Boolean,
        fbUsername: String?,
        fbPassword: String?,
        fb2FA: String?,
        fbTempMail: String?
    ) {
        viewModelScope.launch {
            val account = _uiState.value.account ?: return@launch
            val updated = account.copy(
                accountName = name,
                susLevel = susLevel,
                hasError = hasError,
                fbUsername = fbUsername,
                fbPassword = fbPassword,
                fb2FA = fb2FA,
                fbTempMail = fbTempMail
            )
            accountRepository.updateAccount(updated)
            _uiState.update { it.copy(showEditDialog = false) }
        }
    }
    
    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }
    
    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }
    
    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val account = _uiState.value.account ?: return@launch
            accountRepository.deleteAccount(account)
            onDeleted()
        }
    }
}
```

### 2. Backup Dialog

**Datei:** `ui/components/BackupDialog.kt`

```kotlin
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
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Facebook-Verbindung:", modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                        RadioButton(
                            selected = hasFacebookLink,
                            onClick = { hasFacebookLink = true }
                        )
                        Text("Ja", modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                        RadioButton(
                            selected = !hasFacebookLink,
                            onClick = { hasFacebookLink = false }
                        )
                        Text("Nein", modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
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
```

### 3. Detail Screen

**Datei:** `ui/screens/detail/DetailScreen.kt`

```kotlin
package com.mgomanager.app.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavController,
    accountId: Long,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(accountId) {
        viewModel.loadAccount(accountId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.account?.fullName ?: "Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { paddingValues ->
        uiState.account?.let { account ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Allgemeine Informationen
                Text("Allgemeine Informationen", style = MaterialTheme.typography.titleMedium)
                DetailInfoItem("Name", account.fullName)
                DetailInfoItem("Erstellt am", account.getFormattedCreatedAt())
                DetailInfoItem("Zuletzt gespielt", account.getFormattedLastPlayedAt())
                
                Divider()
                
                // Section: IDs
                Text("IDs", style = MaterialTheme.typography.titleMedium)
                DetailInfoItem("User ID", account.userId)
                DetailInfoItem("GAID", account.gaid)
                DetailInfoItem("Device Token", account.deviceToken)
                DetailInfoItem("App Set ID", account.appSetId)
                DetailInfoItem("SSAID", account.ssaid)
                
                Divider()
                
                // Section: Status
                Text("Status", style = MaterialTheme.typography.titleMedium)
                DetailInfoItem("Sus Level", account.susLevel.displayName)
                DetailInfoItem("Error", if (account.hasError) "Ja" else "Nein")
                
                if (account.hasFacebookLink) {
                    Divider()
                    Text("Facebook Verbindung", style = MaterialTheme.typography.titleMedium)
                    DetailInfoItem("Username", account.fbUsername ?: "")
                    DetailInfoItem("Passwort", account.fbPassword ?: "")
                    DetailInfoItem("2FA", account.fb2FA ?: "")
                    DetailInfoItem("Temp-Mail", account.fbTempMail ?: "")
                }
                
                Divider()
                
                // Section: Dateisystem
                Text("Dateisystem", style = MaterialTheme.typography.titleMedium)
                DetailInfoItem("Backup-Pfad", account.backupPath)
                DetailInfoItem("Eigentümer", "${account.fileOwner}:${account.fileGroup}")
                DetailInfoItem("Berechtigungen", account.filePermissions)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.showRestoreDialog() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("RESTORE")
                    }
                    OutlinedButton(
                        onClick = { viewModel.showEditDialog() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("BEARBEITEN")
                    }
                    OutlinedButton(
                        onClick = { viewModel.showDeleteDialog() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("LÖSCHEN")
                    }
                }
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
}

@Composable
fun DetailInfoItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
```

### 4. Update Home Screen (add BackupDialog)

**In `ui/screens/home/HomeScreen.kt` am Ende hinzufügen:**

```kotlin
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
```

---

## Testing

### Manual Tests
- [ ] Detail Screen zeigt alle Account-Daten
- [ ] Restore-Dialog öffnet und funktioniert
- [ ] Delete-Dialog öffnet und löscht Account
- [ ] Edit-Dialog öffnet (Placeholder)
- [ ] Backup-Dialog zeigt Step 1 und Step 2
- [ ] Backup erstellt Account erfolgreich
- [ ] Result-Dialoge zeigen korrekte Meldungen

---

## Abschlusskriterien

- [ ] Detail Screen vollständig implementiert
- [ ] Backup-Dialog mit 2 Steps funktioniert
- [ ] Restore-Confirmation Dialog funktioniert
- [ ] Delete-Confirmation Dialog funktioniert
- [ ] Alle Dialoge schließen korrekt
- [ ] Navigation zurück funktioniert

**Wenn alle Aufgaben abgeschlossen wurden und alle Tests erfolgreich bestanden wurden, lese `Initial_Entwicklungsprompt_7.md` und setze die Aufgaben um.**

---

*Entwicklungsprompt 6/7 - Erstellt für MGO Manager Projekt*
