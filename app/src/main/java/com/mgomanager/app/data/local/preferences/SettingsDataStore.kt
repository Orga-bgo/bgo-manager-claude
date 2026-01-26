package com.mgomanager.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore wrapper for app settings
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val ACCOUNT_PREFIX = stringPreferencesKey("account_prefix")
        private val BACKUP_ROOT_PATH = stringPreferencesKey("backup_root_path")
        private val CURRENT_SESSION_ID = stringPreferencesKey("current_session_id")
        private val APP_START_COUNT = intPreferencesKey("app_start_count")

        // SSH Server Sync settings
        private val SSH_PRIVATE_KEY_PATH = stringPreferencesKey("ssh_private_key_path")
        private val SSH_SERVER = stringPreferencesKey("ssh_server")
        private val SSH_BACKUP_PATH = stringPreferencesKey("ssh_backup_path")
        private val SSH_PASSWORD = stringPreferencesKey("ssh_password")
        private val SSH_AUTH_METHOD = stringPreferencesKey("ssh_auth_method")
        private val SSH_AUTO_CHECK_ON_START = booleanPreferencesKey("ssh_auto_check_on_start")
        private val SSH_AUTO_UPLOAD_ON_EXPORT = booleanPreferencesKey("ssh_auto_upload_on_export")
        private val SSH_LAST_SYNC_TIMESTAMP = longPreferencesKey("ssh_last_sync_timestamp")

        // Account sorting settings
        private val SORT_MODE = stringPreferencesKey("sort_mode")

        const val DEFAULT_PREFIX = "MGO_"
        const val DEFAULT_BACKUP_PATH = "/storage/emulated/0/mgo/backups/"
        const val DEFAULT_SSH_KEY_PATH = "/storage/emulated/0/.ssh/id_ed25519"
        const val DEFAULT_SSH_BACKUP_PATH = "/home/user/monopolygo/backups/"
        const val DEFAULT_SORT_MODE = "lastPlayed"
        const val DEFAULT_SSH_AUTH_METHOD = "key_only" // key_only, password_only, try_both
    }

    /**
     * Get account prefix
     */
    val accountPrefix: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ACCOUNT_PREFIX] ?: DEFAULT_PREFIX
    }

    /**
     * Set account prefix
     */
    suspend fun setAccountPrefix(prefix: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCOUNT_PREFIX] = prefix
        }
    }

    /**
     * Get backup root path
     */
    val backupRootPath: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[BACKUP_ROOT_PATH] ?: DEFAULT_BACKUP_PATH
    }

    /**
     * Set backup root path
     */
    suspend fun setBackupRootPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[BACKUP_ROOT_PATH] = path
        }
    }

    /**
     * Get current session ID
     */
    val currentSessionId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_SESSION_ID] ?: generateNewSessionId()
    }

    /**
     * Generate and save new session ID
     */
    suspend fun generateNewSession(): String {
        val newSessionId = UUID.randomUUID().toString()
        context.dataStore.edit { preferences ->
            preferences[CURRENT_SESSION_ID] = newSessionId
        }
        return newSessionId
    }

    /**
     * Get app start count
     */
    val appStartCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[APP_START_COUNT] ?: 0
    }

    /**
     * Increment app start count
     */
    suspend fun incrementAppStartCount() {
        context.dataStore.edit { preferences ->
            val currentCount = preferences[APP_START_COUNT] ?: 0
            preferences[APP_START_COUNT] = currentCount + 1
        }
    }

    private fun generateNewSessionId(): String = UUID.randomUUID().toString()

    // ========== SSH Server Sync Settings ==========

    /**
     * Get SSH private key path
     */
    val sshPrivateKeyPath: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SSH_PRIVATE_KEY_PATH] ?: DEFAULT_SSH_KEY_PATH
    }

    /**
     * Set SSH private key path
     */
    suspend fun setSshPrivateKeyPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[SSH_PRIVATE_KEY_PATH] = path
        }
    }

    /**
     * Get SSH server (format: user@host or user@host:port)
     */
    val sshServer: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SSH_SERVER] ?: ""
    }

    /**
     * Set SSH server
     */
    suspend fun setSshServer(server: String) {
        context.dataStore.edit { preferences ->
            preferences[SSH_SERVER] = server
        }
    }

    /**
     * Get SSH backup path on server
     */
    val sshBackupPath: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SSH_BACKUP_PATH] ?: DEFAULT_SSH_BACKUP_PATH
    }

    /**
     * Set SSH backup path on server
     */
    suspend fun setSshBackupPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[SSH_BACKUP_PATH] = path
        }
    }

    /**
     * Get SSH password (stored securely)
     */
    val sshPassword: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SSH_PASSWORD] ?: ""
    }

    /**
     * Set SSH password
     */
    suspend fun setSshPassword(password: String) {
        context.dataStore.edit { preferences ->
            preferences[SSH_PASSWORD] = password
        }
    }

    /**
     * Get SSH authentication method (key_only, password_only, try_both)
     */
    val sshAuthMethod: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SSH_AUTH_METHOD] ?: DEFAULT_SSH_AUTH_METHOD
    }

    /**
     * Set SSH authentication method
     */
    suspend fun setSshAuthMethod(method: String) {
        context.dataStore.edit { preferences ->
            preferences[SSH_AUTH_METHOD] = method
        }
    }

    /**
     * Get auto-check on app start setting
     */
    val sshAutoCheckOnStart: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SSH_AUTO_CHECK_ON_START] ?: false
    }

    /**
     * Set auto-check on app start
     */
    suspend fun setSshAutoCheckOnStart(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SSH_AUTO_CHECK_ON_START] = enabled
        }
    }

    /**
     * Get auto-upload on export setting
     */
    val sshAutoUploadOnExport: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SSH_AUTO_UPLOAD_ON_EXPORT] ?: false
    }

    /**
     * Set auto-upload on export
     */
    suspend fun setSshAutoUploadOnExport(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SSH_AUTO_UPLOAD_ON_EXPORT] = enabled
        }
    }

    /**
     * Get last sync timestamp
     */
    val sshLastSyncTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[SSH_LAST_SYNC_TIMESTAMP] ?: 0L
    }

    /**
     * Set last sync timestamp
     */
    suspend fun setSshLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[SSH_LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    // ========== Account Sorting Settings ==========

    /**
     * Get current sort mode (name, created, lastPlayed, prefixFirst)
     */
    val sortMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SORT_MODE] ?: DEFAULT_SORT_MODE
    }

    /**
     * Set sort mode
     */
    suspend fun setSortMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[SORT_MODE] = mode
        }
    }
}
