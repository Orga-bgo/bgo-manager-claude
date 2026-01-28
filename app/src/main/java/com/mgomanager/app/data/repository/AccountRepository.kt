package com.mgomanager.app.data.repository

import com.mgomanager.app.data.local.database.dao.AccountDao
import com.mgomanager.app.data.local.database.entities.AccountEntity
import com.mgomanager.app.data.local.database.entities.toDomain
import com.mgomanager.app.data.local.database.entities.toEntity
import com.mgomanager.app.data.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Account-related operations
 * Provides a clean API between ViewModels and data sources
 */
@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao
) {

    /**
     * Get all accounts as Flow (reactive)
     */
    fun getAllAccounts(): Flow<List<Account>> {
        return accountDao.getAllAccountsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get all accounts (one-time)
     */
    suspend fun getAllAccountsList(): List<Account> {
        return accountDao.getAllAccounts().map { it.toDomain() }
    }

    /**
     * Get account by ID
     */
    suspend fun getAccountById(id: Long): Account? {
        return accountDao.getAccountById(id)?.toDomain()
    }

    /**
     * Get account by ID as Flow
     */
    fun getAccountByIdFlow(id: Long): Flow<Account?> {
        return accountDao.getAccountByIdFlow(id).map { it?.toDomain() }
    }

    /**
     * Get account by name
     */
    suspend fun getAccountByName(name: String): Account? {
        return accountDao.getAccountByName(name)?.toDomain()
    }

    /**
     * Get account by User ID
     */
    suspend fun getAccountByUserId(userId: String): Account? {
        return accountDao.getAccountByUserId(userId)?.toDomain()
    }

    /**
     * Insert new account
     * @return ID of inserted account
     */
    suspend fun insertAccount(account: Account): Long {
        return accountDao.insertAccount(account.toEntity())
    }

    /**
     * Update existing account
     */
    suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(account.toEntity())
    }

    /**
     * Delete account
     */
    suspend fun deleteAccount(account: Account) {
        accountDao.deleteAccount(account.toEntity())
    }

    /**
     * Delete account by ID
     */
    suspend fun deleteAccountById(id: Long) {
        accountDao.deleteAccountById(id)
    }

    /**
     * Update last played timestamp
     */
    suspend fun updateLastPlayedTimestamp(id: Long, timestamp: Long = System.currentTimeMillis()) {
        accountDao.updateLastPlayedTimestamp(id, timestamp)
    }

    /**
     * Update sus level
     */
    suspend fun updateSusLevel(id: Long, susLevel: Int) {
        accountDao.updateSusLevel(id, susLevel)
    }

    /**
     * Update error status
     */
    suspend fun updateErrorStatus(id: Long, hasError: Boolean) {
        accountDao.updateErrorStatus(id, hasError)
    }

    /**
     * Get statistics
     */
    fun getAccountCount(): Flow<Int> = accountDao.getAccountCount()
    fun getErrorCount(): Flow<Int> = accountDao.getErrorAccountCount()
    fun getSusCount(): Flow<Int> = accountDao.getSusAccountCount()

    // ============================================================
    // Xposed Hook Support Methods
    // ============================================================

    /**
     * Mark an account as the last restored.
     * Clears the flag from all other accounts first.
     * Used by Xposed hook to determine which App Set ID to return.
     */
    suspend fun markAsLastRestored(accountId: Long) {
        accountDao.clearLastRestored()
        accountDao.markAsLastRestored(accountId)
    }

    /**
     * Get the last restored account.
     */
    suspend fun getLastRestoredAccount(): Account? {
        return accountDao.getLastRestoredAccount()?.toDomain()
    }
}
