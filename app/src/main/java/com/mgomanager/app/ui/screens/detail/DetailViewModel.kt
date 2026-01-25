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
    val restoreResult: RestoreResult? = null,
    val showRestoreSuccessDialog: Boolean = false
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
            val isSuccess = result is RestoreResult.Success
            _uiState.update {
                it.copy(
                    isLoading = false,
                    restoreResult = if (!isSuccess) result else null,
                    showRestoreDialog = false,
                    showRestoreSuccessDialog = isSuccess
                )
            }
        }
    }

    fun hideRestoreSuccessDialog() {
        _uiState.update { it.copy(showRestoreSuccessDialog = false) }
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
