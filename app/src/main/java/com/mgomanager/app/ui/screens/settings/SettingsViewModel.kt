package com.mgomanager.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.data.model.ExportProgress
import com.mgomanager.app.data.model.ImportProgress
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
    val exportProgress: ExportProgress? = null,
    val importProgress: ImportProgress? = null,
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
            exportImportUseCase.exportData(context)
                .collect { progress ->
                    _uiState.update { it.copy(exportProgress = progress) }

                    // When complete or error, also set the result
                    when (progress) {
                        is ExportProgress.Success -> {
                            _uiState.update {
                                it.copy(
                                    exportProgress = null,
                                    exportResult = progress.filePath
                                )
                            }
                        }
                        is ExportProgress.Error -> {
                            _uiState.update {
                                it.copy(
                                    exportProgress = null,
                                    exportResult = progress.message
                                )
                            }
                        }
                        is ExportProgress.InProgress -> {
                            // Progress is already updated above
                        }
                    }
                }
        }
    }

    fun importData() {
        viewModelScope.launch {
            exportImportUseCase.importData(context)
                .collect { progress ->
                    _uiState.update { it.copy(importProgress = progress) }

                    // When complete or error, also set the result
                    when (progress) {
                        is ImportProgress.Success -> {
                            _uiState.update {
                                it.copy(
                                    importProgress = null,
                                    importResult = progress.message
                                )
                            }
                        }
                        is ImportProgress.Error -> {
                            _uiState.update {
                                it.copy(
                                    importProgress = null,
                                    importResult = progress.message
                                )
                            }
                        }
                        is ImportProgress.InProgress -> {
                            // Progress is already updated above
                        }
                    }
                }
        }
    }

    fun resetExportProgress() {
        _uiState.update { it.copy(exportProgress = null) }
    }

    fun resetImportProgress() {
        _uiState.update { it.copy(importProgress = null) }
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
