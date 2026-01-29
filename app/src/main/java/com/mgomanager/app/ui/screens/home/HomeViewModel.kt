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
import com.mgomanager.app.domain.usecase.CreateAccountProgress
import com.mgomanager.app.domain.usecase.CreateNewAccountRequest
import com.mgomanager.app.domain.usecase.CreateNewAccountResult
import com.mgomanager.app.domain.usecase.CreateNewAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    // SSAID Fallback
    val ssaidFallbackState: SsaidFallbackState? = null,
    // Sorting
    val sortMode: String = "lastPlayed",
    val accountPrefix: String = "MGO_",
    // Create New Account
    val showCreateAccountDialog: Boolean = false,
    val createAccountResult: CreateNewAccountResult? = null,
    val isCreatingAccount: Boolean = false,
    val createAccountError: String? = null,
    val createAccountProgress: CreateAccountProgress? = null
)

data class DuplicateUserIdInfo(
    val userId: String,
    val existingAccountName: String,
    val pendingRequest: BackupRequest
)

/**
 * State for SSAID fallback countdown dialog.
 * Shown when settings_ssaid.xml is missing during backup.
 */
data class SsaidFallbackState(
    val countdown: Int = 5,
    val isCapturing: Boolean = false,
    val capturedSsaid: String? = null,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository,
    private val settingsDataStore: SettingsDataStore,
    private val createNewAccountUseCase: CreateNewAccountUseCase
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

    // ============================================================
    // SSAID Fallback (when settings_ssaid.xml is missing)
    // ============================================================

    /**
     * Start the SSAID fallback flow with countdown.
     * Shows dialog: "Android ID nicht gefunden - nutze Fallback. Monopoly Go startet in 5 4 3 2 1..."
     */
    fun startSsaidFallback() {
        viewModelScope.launch {
            // Show countdown dialog
            _uiState.update { it.copy(ssaidFallbackState = SsaidFallbackState(countdown = 5)) }

            // Enable capture mode
            backupRepository.enableSsaidCaptureMode()

            // Countdown from 5 to 1
            for (i in 5 downTo 1) {
                _uiState.update {
                    it.copy(ssaidFallbackState = it.ssaidFallbackState?.copy(countdown = i))
                }
                delay(1000)
            }

            // Start Monopoly GO
            _uiState.update {
                it.copy(ssaidFallbackState = it.ssaidFallbackState?.copy(isCapturing = true))
            }

            backupRepository.startMonopolyGo()

            // Wait a bit for the app to start and hook to capture
            delay(3000)

            // Try to read the captured SSAID
            val capturedSsaid = backupRepository.readCapturedSsaid()

            if (capturedSsaid != null) {
                _uiState.update {
                    it.copy(ssaidFallbackState = it.ssaidFallbackState?.copy(
                        isCapturing = false,
                        capturedSsaid = capturedSsaid
                    ))
                }
            } else {
                // Retry a few times
                var retries = 5
                var ssaid: String? = null
                while (retries > 0 && ssaid == null) {
                    delay(2000)
                    ssaid = backupRepository.readCapturedSsaid()
                    retries--
                }

                if (ssaid != null) {
                    _uiState.update {
                        it.copy(ssaidFallbackState = it.ssaidFallbackState?.copy(
                            isCapturing = false,
                            capturedSsaid = ssaid
                        ))
                    }
                } else {
                    _uiState.update {
                        it.copy(ssaidFallbackState = it.ssaidFallbackState?.copy(
                            isCapturing = false,
                            error = "Android ID konnte nicht erfasst werden"
                        ))
                    }
                }
            }

            // Disable capture mode
            backupRepository.disableSsaidCaptureMode()
        }
    }

    /**
     * Dismiss the SSAID fallback dialog.
     */
    fun dismissSsaidFallback() {
        viewModelScope.launch {
            backupRepository.disableSsaidCaptureMode()
        }
        _uiState.update { it.copy(ssaidFallbackState = null) }
    }

    /**
     * Get the captured SSAID value (for use when completing backup).
     */
    fun getCapturedSsaid(): String? {
        return _uiState.value.ssaidFallbackState?.capturedSsaid
    }

    // ============================================================
    // Create New Account
    // ============================================================

    /**
     * Show the create account dialog.
     */
    fun showCreateAccountDialog() {
        _uiState.update {
            it.copy(
                showCreateAccountDialog = true,
                createAccountError = null
            )
        }
    }

    /**
     * Hide the create account dialog.
     */
    fun hideCreateAccountDialog() {
        _uiState.update {
            it.copy(
                showCreateAccountDialog = false,
                createAccountError = null,
                isCreatingAccount = false
            )
        }
    }

    /**
     * Create a new account with the given name.
     * This clears existing game data and generates new device identifiers.
     */
    fun createNewAccount(accountName: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreatingAccount = true,
                    createAccountError = null,
                    createAccountProgress = null
                )
            }

            val prefix = settingsDataStore.accountPrefix.first()
            val request = CreateNewAccountRequest(
                accountName = accountName,
                prefix = prefix
            )

            createNewAccountUseCase.executeWithProgress(request).collect { result ->
                when (result) {
                    is CreateNewAccountResult.Progress -> {
                        _uiState.update {
                            it.copy(createAccountProgress = result.progress)
                        }
                    }
                    is CreateNewAccountResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isCreatingAccount = false,
                                showCreateAccountDialog = false,
                                createAccountResult = result,
                                createAccountProgress = null
                            )
                        }
                    }
                    is CreateNewAccountResult.ValidationError -> {
                        _uiState.update {
                            it.copy(
                                isCreatingAccount = false,
                                createAccountError = result.error,
                                createAccountProgress = null
                            )
                        }
                    }
                    is CreateNewAccountResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isCreatingAccount = false,
                                createAccountError = result.error,
                                createAccountProgress = null
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Clear the create account result after showing success message.
     */
    fun clearCreateAccountResult() {
        _uiState.update { it.copy(createAccountResult = null) }
    }
}
