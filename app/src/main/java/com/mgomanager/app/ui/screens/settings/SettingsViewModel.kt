package com.mgomanager.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.domain.usecase.ExportImportUseCase
import com.mgomanager.app.domain.util.RootUtil
import com.mgomanager.app.domain.util.SSHSyncService
import com.mgomanager.app.domain.util.SSHOperationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val accountPrefix: String = "MGO_",
    val backupRootPath: String = "/storage/emulated/0/mgo/backups/",
    val isRootAvailable: Boolean = false,
    val appStartCount: Int = 0,
    val prefixSaved: Boolean = false,
    val pathSaved: Boolean = false,
    val exportResult: String? = null,
    val importResult: String? = null,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    // SSH settings
    val sshPrivateKeyPath: String = "/storage/emulated/0/.ssh/id_ed25519",
    val sshServer: String = "",
    val sshBackupPath: String = "/home/user/monopolygo/backups/",
    val sshPassword: String = "",
    val sshAuthMethod: String = "key_only", // key_only, password_only, try_both
    val sshAutoCheckOnStart: Boolean = false,
    val sshAutoUploadOnExport: Boolean = false,
    val sshLastSyncTimestamp: Long = 0L,
    val sshKeyPathSaved: Boolean = false,
    val sshServerSaved: Boolean = false,
    val sshBackupPathSaved: Boolean = false,
    val sshPasswordSaved: Boolean = false,
    val sshTestResult: String? = null,
    val isSshTesting: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val rootUtil: RootUtil,
    private val exportImportUseCase: ExportImportUseCase,
    private val sshSyncService: SSHSyncService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // Load basic settings from DataStore
            combine(
                settingsDataStore.accountPrefix,
                settingsDataStore.backupRootPath,
                settingsDataStore.appStartCount
            ) { prefix, path, count ->
                _uiState.update {
                    it.copy(
                        accountPrefix = prefix,
                        backupRootPath = path,
                        appStartCount = count
                    )
                }
            }.collect { }
        }

        // Load SSH settings (split into separate collectors due to combine limit of 5 flows)
        viewModelScope.launch {
            combine(
                settingsDataStore.sshPrivateKeyPath,
                settingsDataStore.sshServer,
                settingsDataStore.sshBackupPath
            ) { keyPath, server, backupPath ->
                _uiState.update {
                    it.copy(
                        sshPrivateKeyPath = keyPath,
                        sshServer = server,
                        sshBackupPath = backupPath
                    )
                }
            }.collect { }
        }

        viewModelScope.launch {
            combine(
                settingsDataStore.sshPassword,
                settingsDataStore.sshAuthMethod,
                settingsDataStore.sshLastSyncTimestamp
            ) { password, authMethod, lastSync ->
                _uiState.update {
                    it.copy(
                        sshPassword = password,
                        sshAuthMethod = authMethod,
                        sshLastSyncTimestamp = lastSync
                    )
                }
            }.collect { }
        }

        viewModelScope.launch {
            combine(
                settingsDataStore.sshAutoCheckOnStart,
                settingsDataStore.sshAutoUploadOnExport
            ) { autoCheck, autoUpload ->
                _uiState.update {
                    it.copy(
                        sshAutoCheckOnStart = autoCheck,
                        sshAutoUploadOnExport = autoUpload
                    )
                }
            }.collect { }
        }

        // Check root status separately
        viewModelScope.launch {
            val isRooted = rootUtil.isRooted()
            _uiState.update { it.copy(isRootAvailable = isRooted) }
        }
    }

    fun refreshRootStatus() {
        viewModelScope.launch {
            val isRooted = rootUtil.isRooted()
            _uiState.update { it.copy(isRootAvailable = isRooted) }
        }
    }

    fun updatePrefix(prefix: String) {
        viewModelScope.launch {
            settingsDataStore.setAccountPrefix(prefix)
            _uiState.update { it.copy(prefixSaved = true) }
        }
    }

    fun updateBackupPath(path: String) {
        viewModelScope.launch {
            settingsDataStore.setBackupRootPath(path)
            _uiState.update { it.copy(pathSaved = true) }
        }
    }

    fun resetPrefixSaved() {
        _uiState.update { it.copy(prefixSaved = false) }
    }

    fun resetPathSaved() {
        _uiState.update { it.copy(pathSaved = false) }
    }

    fun exportData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val result = exportImportUseCase.exportData(context)
            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportResult = result.getOrElse { e -> "Export fehlgeschlagen: ${e.message}" }
                )
            }
        }
    }

    fun importData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val result = exportImportUseCase.importData(context)
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importResult = if (result.isSuccess) "Import erfolgreich!" else "Import fehlgeschlagen: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun clearExportResult() {
        _uiState.update { it.copy(exportResult = null) }
    }

    fun clearImportResult() {
        _uiState.update { it.copy(importResult = null) }
    }

    // ========== SSH Settings Functions ==========

    fun updateSshPrivateKeyPath(path: String) {
        viewModelScope.launch {
            settingsDataStore.setSshPrivateKeyPath(path)
            _uiState.update { it.copy(sshKeyPathSaved = true) }
        }
    }

    fun resetSshKeyPathSaved() {
        _uiState.update { it.copy(sshKeyPathSaved = false) }
    }

    fun updateSshServer(server: String) {
        viewModelScope.launch {
            settingsDataStore.setSshServer(server)
            _uiState.update { it.copy(sshServerSaved = true) }
        }
    }

    fun resetSshServerSaved() {
        _uiState.update { it.copy(sshServerSaved = false) }
    }

    fun updateSshBackupPath(path: String) {
        viewModelScope.launch {
            settingsDataStore.setSshBackupPath(path)
            _uiState.update { it.copy(sshBackupPathSaved = true) }
        }
    }

    fun resetSshBackupPathSaved() {
        _uiState.update { it.copy(sshBackupPathSaved = false) }
    }

    fun updateSshPassword(password: String) {
        viewModelScope.launch {
            settingsDataStore.setSshPassword(password)
            _uiState.update { it.copy(sshPasswordSaved = true) }
        }
    }

    fun resetSshPasswordSaved() {
        _uiState.update { it.copy(sshPasswordSaved = false) }
    }

    fun updateSshAuthMethod(method: String) {
        viewModelScope.launch {
            settingsDataStore.setSshAuthMethod(method)
        }
    }

    fun updateSshAutoCheckOnStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSshAutoCheckOnStart(enabled)
        }
    }

    fun updateSshAutoUploadOnExport(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSshAutoUploadOnExport(enabled)
        }
    }

    fun testSshConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSshTesting = true, sshTestResult = null) }
            val result = sshSyncService.testConnection()
            val message = when (result) {
                is SSHOperationResult.Success -> result.message
                is SSHOperationResult.Error -> result.message
            }
            _uiState.update { it.copy(isSshTesting = false, sshTestResult = message) }
        }
    }

    fun clearSshTestResult() {
        _uiState.update { it.copy(sshTestResult = null) }
    }

    fun formatLastSyncTime(): String {
        return sshSyncService.formatTimestamp(_uiState.value.sshLastSyncTimestamp)
    }
}
