# Initial Development Prompt 5: UI - Home Screen & Navigation

## Übersicht
Implementation der Home Screen UI mit Jetpack Compose: Statistics Cards, Account Grid, Navigation, FAB und grundlegende Dialoge.

## Voraussetzungen
- Erfolgreicher Abschluss von Prompt 4
- Funktionierende Backup/Restore-Logik
- CLAUDE.md zur Hand

---

## Aufgaben

### 1. Navigation Graph

**Datei:** `ui/navigation/NavGraph.kt`

```kotlin
package com.mgomanager.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mgomanager.app.ui.screens.detail.DetailScreen
import com.mgomanager.app.ui.screens.home.HomeScreen
import com.mgomanager.app.ui.screens.settings.SettingsScreen
import com.mgomanager.app.ui.screens.logs.LogScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Detail : Screen("detail/{accountId}") {
        fun createRoute(accountId: Long) = "detail/$accountId"
    }
    object Settings : Screen("settings")
    object Logs : Screen("logs")
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId") ?: return@composable
            DetailScreen(navController = navController, accountId = accountId)
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        
        composable(Screen.Logs.route) {
            LogScreen(navController = navController)
        }
    }
}
```

### 2. Home ViewModel

**Datei:** `ui/screens/home/HomeViewModel.kt`

```kotlin
package com.mgomanager.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.BackupRepository
import com.mgomanager.app.domain.usecase.BackupRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val accounts: List<Account> = emptyList(),
    val totalCount: Int = 0,
    val errorCount: Int = 0,
    val susCount: Int = 0,
    val isLoading: Boolean = false,
    val showBackupDialog: Boolean = false,
    val backupResult: BackupResult? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadAccounts()
        loadStatistics()
    }
    
    private fun loadAccounts() {
        viewModelScope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.update { it.copy(accounts = accounts) }
            }
        }
    }
    
    private fun loadStatistics() {
        viewModelScope.launch {
            combine(
                accountRepository.getAccountCount(),
                accountRepository.getErrorCount(),
                accountRepository.getSusCount()
            ) { total, error, sus ->
                Triple(total, error, sus)
            }.collect { (total, error, sus) ->
                _uiState.update {
                    it.copy(
                        totalCount = total,
                        errorCount = error,
                        susCount = sus
                    )
                }
            }
        }
    }
    
    fun showBackupDialog() {
        _uiState.update { it.copy(showBackupDialog = true) }
    }
    
    fun hideBackupDialog() {
        _uiState.update { it.copy(showBackupDialog = false, backupResult = null) }
    }
    
    fun createBackup(
        accountName: String,
        hasFacebookLink: Boolean,
        fbUsername: String? = null,
        fbPassword: String? = null,
        fb2FA: String? = null,
        fbTempMail: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val prefix = settingsDataStore.accountPrefix.first()
            val backupPath = settingsDataStore.backupRootPath.first()
            
            val request = BackupRequest(
                accountName = accountName,
                prefix = prefix,
                backupRootPath = backupPath,
                hasFacebookLink = hasFacebookLink,
                fbUsername = fbUsername,
                fbPassword = fbPassword,
                fb2FA = fb2FA,
                fbTempMail = fbTempMail
            )
            
            val result = backupRepository.createBackup(request)
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    backupResult = result,
                    showBackupDialog = false
                ) 
            }
        }
    }
    
    fun clearBackupResult() {
        _uiState.update { it.copy(backupResult = null) }
    }
}
```

### 3. Statistics Card Component

**Datei:** `ui/components/StatisticsCard.kt`

```kotlin
package com.mgomanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatisticsCard(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                color = color
            )
        }
    }
}
```

### 4. Account Card Component

**Datei:** `ui/components/AccountCard.kt`

```kotlin
package com.mgomanager.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mgomanager.app.data.model.Account

@Composable
fun AccountCard(
    account: Account,
    onCardClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onCardClick,
        modifier = modifier,
        border = BorderStroke(3.dp, account.getBorderColor()),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Account name
            Text(
                text = account.fullName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // User ID
            Text(
                text = "ID: ${account.shortUserId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Timestamps
            Text(
                text = "⏱ ${account.getFormattedLastPlayedAt()}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "💾 ${account.getFormattedCreatedAt()}",
                style = MaterialTheme.typography.bodySmall
            )
            
            // Error message if present
            if (account.hasError) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ERROR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Restore button
            Button(
                onClick = onRestoreClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("RESTORE")
            }
        }
    }
}
```

### 5. Home Screen

**Datei:** `ui/screens/home/HomeScreen.kt`

```kotlin
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
import com.mgomanager.app.ui.components.AccountCard
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
            
            // Account grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.accounts) { account ->
                    AccountCard(
                        account = account,
                        onCardClick = { 
                            navController.navigate(Screen.Detail.createRoute(account.id))
                        },
                        onRestoreClick = { /* TODO: Show restore confirmation */ }
                    )
                }
            }
        }
    }
    
    // Show backup dialog
    if (uiState.showBackupDialog) {
        // TODO: Implement BackupDialog
    }
}
```

### 6. Update MainActivity

**Datei:** `MainActivity.kt` (Replace with Navigation)

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var rootUtil: RootUtil
    
    @Inject
    lateinit var permissionManager: PermissionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MGOManagerTheme {
                val navController = rememberNavController()
                
                var isReady by remember { mutableStateOf(false) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                
                LaunchedEffect(Unit) {
                    // Check prerequisites
                    val isRooted = rootUtil.requestRootAccess()
                    val hasPermissions = permissionManager.hasStoragePermissions()
                    val isMGOInstalled = rootUtil.isMonopolyGoInstalled()
                    
                    when {
                        !isRooted -> errorMessage = "Root-Zugriff erforderlich"
                        !hasPermissions -> errorMessage = "Speicher-Berechtigungen erforderlich"
                        !isMGOInstalled -> errorMessage = "Monopoly Go nicht installiert"
                        else -> isReady = true
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        errorMessage != null -> {
                            // Show error dialog
                            AlertDialog(
                                onDismissRequest = { finish() },
                                title = { Text("Fehler") },
                                text = { Text(errorMessage!!) },
                                confirmButton = {
                                    TextButton(onClick = { finish() }) {
                                        Text("Schließen")
                                    }
                                }
                            )
                        }
                        isReady -> AppNavGraph(navController)
                        else -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
```

---

## Testing

### Manual Tests
- [ ] App startet und zeigt Home Screen
- [ ] Statistiken werden korrekt angezeigt
- [ ] Account-Karten werden in 2-Spalten-Grid angezeigt
- [ ] Border-Farben entsprechen Status (grün/orange/rot)
- [ ] FAB öffnet Backup-Dialog (Placeholder)
- [ ] Navigation zu Settings funktioniert
- [ ] Click auf Account-Karte navigiert zu Detail (Placeholder)

---

## Abschlusskriterien

- [ ] Home Screen zeigt Statistics Cards
- [ ] Account Grid mit 2 Spalten funktioniert
- [ ] Navigation ist konfiguriert
- [ ] FAB ist vorhanden
- [ ] Theme wird korrekt angewendet
- [ ] Keine UI-Fehler oder Crashes

**Wenn alle Aufgaben abgeschlossen wurden und alle Tests erfolgreich bestanden wurden, lese `Initial_Entwicklungsprompt_6.md` und setze die Aufgaben um.**

---

*Entwicklungsprompt 5/7 - Erstellt für MGO Manager Projekt*
