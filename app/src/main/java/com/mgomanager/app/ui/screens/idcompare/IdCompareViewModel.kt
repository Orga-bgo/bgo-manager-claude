package com.mgomanager.app.ui.screens.idcompare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdGroup(
    val idValue: String,
    val accounts: List<Account>
)

data class IdCompareUiState(
    val ssaidGroups: List<IdGroup> = emptyList(),
    val gaidGroups: List<IdGroup> = emptyList(),
    val deviceTokenGroups: List<IdGroup> = emptyList(),
    val appSetIdGroups: List<IdGroup> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class IdCompareViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdCompareUiState())
    val uiState: StateFlow<IdCompareUiState> = _uiState.asStateFlow()

    init {
        loadIdComparison()
    }

    private fun loadIdComparison() {
        viewModelScope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                val ssaidGroups = groupById(accounts) { it.ssaid }
                val gaidGroups = groupById(accounts) { it.gaid }
                val deviceTokenGroups = groupById(accounts) { it.deviceToken }
                val appSetIdGroups = groupById(accounts) { it.appSetId }

                _uiState.update {
                    it.copy(
                        ssaidGroups = ssaidGroups,
                        gaidGroups = gaidGroups,
                        deviceTokenGroups = deviceTokenGroups,
                        appSetIdGroups = appSetIdGroups,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun groupById(accounts: List<Account>, idSelector: (Account) -> String): List<IdGroup> {
        return accounts
            .filter { idSelector(it) != "nicht vorhanden" }
            .groupBy { idSelector(it) }
            .map { (idValue, groupAccounts) ->
                IdGroup(idValue, groupAccounts)
            }
            .sortedByDescending { it.accounts.size }
    }
}
