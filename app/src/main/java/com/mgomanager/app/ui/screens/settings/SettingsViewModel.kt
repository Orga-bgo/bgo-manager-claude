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
    val appStartCount: Int = 0,
    val prefixSaved: Boolean = false,
    val pathSaved: Boolean = false
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
            // Load settings from DataStore
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
}
