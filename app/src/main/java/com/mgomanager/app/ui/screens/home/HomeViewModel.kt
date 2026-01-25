package com.mgomanager.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.model.RestoreResult
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
    val backupResult: BackupResult? = null,
    val restoreResult: RestoreResult? = null,
    val showRestoreConfirm: Long? = null, // Account ID to restore
    val showRestoreSuccessDialog: Boolean = false,
    val duplicateUserIdDialog: DuplicateUserIdInfo? = null, // For duplicate check
    // Sorting
    val sortMode: String = "lastPlayed",
    val accountPrefix: String = "MGO_"
)

data class DuplicateUserIdInfo(
    val userId: String,
    val existingAccountName: String,
    val pendingRequest: BackupRequest
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
        loadSortSettings()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            combine(
                accountRepository.getAllAccounts(),
                settingsDataStore.sortMode,
                settingsDataStore.accountPrefix
            ) { accounts, sortMode, prefix ->
                Triple(accounts, sortMode, prefix)
            }.collect { (accounts, sortMode, prefix) ->
                val sortedAccounts = sortAccounts(accounts, sortMode, prefix)
                _uiState.update {
                    it.copy(
                        accounts = sortedAccounts,
                        sortMode = sortMode,
                        accountPrefix = prefix
                    )
                }
            }
        }
    }

    private fun loadSortSettings() {
        viewModelScope.launch {
            settingsDataStore.sortMode.collect { mode ->
                _uiState.update { it.copy(sortMode = mode) }
            }
        }
    }

    private fun sortAccounts(accounts: List<Account>, sortMode: String, prefix: String): List<Account> {
        return when (sortMode) {
            "name" -> accounts.sortedBy { it.fullName.lowercase() }
            "created" -> accounts.sortedByDescending { it.createdAt }
            "lastPlayed" -> accounts.sortedByDescending { it.lastPlayedAt }
            "prefixFirst" -> accounts.sortedWith(compareBy(
                { if (prefix.isNotBlank()) !it.fullName.startsWith(prefix) else false },
                { it.fullName.lowercase() }
            ))
            else -> accounts.sortedByDescending { it.lastPlayedAt }
        }
    }

    fun setSortMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.setSortMode(mode)
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
        fbTempMail: String? = null,
        forceDuplicate: Boolean = false
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

            val result = backupRepository.createBackup(request, forceDuplicate)

            // Check if duplicate user ID was found
            if (result is BackupResult.DuplicateUserId) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showBackupDialog = false,
                        duplicateUserIdDialog = DuplicateUserIdInfo(
                            userId = result.userId,
                            existingAccountName = result.existingAccountName,
                            pendingRequest = request
                        )
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        backupResult = result,
                        showBackupDialog = false
                    )
                }
            }
        }
    }

    fun confirmDuplicateBackup() {
        val info = _uiState.value.duplicateUserIdDialog ?: return
        _uiState.update { it.copy(duplicateUserIdDialog = null) }
        createBackup(
            accountName = info.pendingRequest.accountName,
            hasFacebookLink = info.pendingRequest.hasFacebookLink,
            fbUsername = info.pendingRequest.fbUsername,
            fbPassword = info.pendingRequest.fbPassword,
            fb2FA = info.pendingRequest.fb2FA,
            fbTempMail = info.pendingRequest.fbTempMail,
            forceDuplicate = true
        )
    }

    fun cancelDuplicateBackup() {
        _uiState.update { it.copy(duplicateUserIdDialog = null) }
    }

    fun clearBackupResult() {
        _uiState.update { it.copy(backupResult = null) }
    }

    fun showRestoreConfirm(accountId: Long) {
        _uiState.update { it.copy(showRestoreConfirm = accountId) }
    }

    fun hideRestoreConfirm() {
        _uiState.update { it.copy(showRestoreConfirm = null) }
    }

    fun restoreAccount(accountId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showRestoreConfirm = null) }

            val result = backupRepository.restoreBackup(accountId)
            val isSuccess = result is RestoreResult.Success
            _uiState.update {
                it.copy(
                    isLoading = false,
                    restoreResult = if (!isSuccess) result else null,
                    showRestoreSuccessDialog = isSuccess
                )
            }
        }
    }

    fun clearRestoreResult() {
        _uiState.update { it.copy(restoreResult = null) }
    }

    fun hideRestoreSuccessDialog() {
        _uiState.update { it.copy(showRestoreSuccessDialog = false) }
    }
}
