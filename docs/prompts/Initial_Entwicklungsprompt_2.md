# Initial Development Prompt 2: Data Model & Database Implementation

## Übersicht
In dieser Phase werden alle Datenmodelle, Room Entities, DAOs, die Datenbank selbst und das Repository-Pattern implementiert. Zusätzlich wird DataStore für App-Einstellungen konfiguriert.

## Voraussetzungen
- Erfolgreicher Abschluss von Prompt 1
- Funktionierende Hilt Dependency Injection
- CLAUDE.md zur Hand

---

## Aufgaben

### 1. Data Models (Domain Layer)

#### 1.1 Sus Level Enum
**Datei:** `data/model/SusLevel.kt`

```kotlin
package com.mgomanager.app.data.model

import androidx.compose.ui.graphics.Color
import com.mgomanager.app.ui.theme.StatusGreen
import com.mgomanager.app.ui.theme.StatusLightOrange
import com.mgomanager.app.ui.theme.StatusOrange
import com.mgomanager.app.ui.theme.StatusRed

/**
 * Represents the suspicious account level
 * 0 = Clean, 3 = Warning Level 3, 7 = Warning Level 7, 99 = Permanent Ban
 */
enum class SusLevel(val value: Int, val displayName: String) {
    NONE(0, "Keine"),
    LEVEL_3(3, "Level 3"),
    LEVEL_7(7, "Level 7"),
    PERMANENT(99, "Permanent");
    
    companion object {
        fun fromValue(value: Int): SusLevel {
            return values().find { it.value == value } ?: NONE
        }
        
        fun getColor(susLevel: SusLevel): Color {
            return when (susLevel) {
                NONE -> StatusGreen
                LEVEL_3 -> StatusOrange
                LEVEL_7 -> StatusLightOrange
                PERMANENT -> StatusRed
            }
        }
    }
    
    fun getColor(): Color = getColor(this)
}
```

#### 1.2 Account Model
**Datei:** `data/model/Account.kt`

```kotlin
package com.mgomanager.app.data.model

import java.text.SimpleDateFormat
import java.util.*

/**
 * Domain model for MGO account
 * This is the business logic representation (not database entity)
 */
data class Account(
    val id: Long = 0,
    val accountName: String,
    val prefix: String = "",
    val createdAt: Long,
    val lastPlayedAt: Long,
    
    // Extracted IDs
    val userId: String,
    val gaid: String = "nicht vorhanden",
    val deviceToken: String = "nicht vorhanden",
    val appSetId: String = "nicht vorhanden",
    val ssaid: String = "nicht vorhanden",
    
    // Status flags
    val susLevel: SusLevel = SusLevel.NONE,
    val hasError: Boolean = false,
    
    // Facebook data
    val hasFacebookLink: Boolean = false,
    val fbUsername: String? = null,
    val fbPassword: String? = null,
    val fb2FA: String? = null,
    val fbTempMail: String? = null,
    
    // File system metadata
    val backupPath: String,
    val fileOwner: String,
    val fileGroup: String,
    val filePermissions: String
) {
    val fullName: String
        get() = if (prefix.isNotEmpty()) "$prefix$accountName" else accountName
    
    val shortUserId: String
        get() = if (userId.length > 4) "...${userId.takeLast(4)}" else userId
    
    fun getFormattedCreatedAt(): String {
        return formatTimestamp(createdAt)
    }
    
    fun getFormattedLastPlayedAt(): String {
        return formatTimestamp(lastPlayedAt)
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN)
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Get border color based on status
     * Priority: Error > Sus Level
     */
    fun getBorderColor(): androidx.compose.ui.graphics.Color {
        return if (hasError) {
            com.mgomanager.app.ui.theme.StatusRed
        } else {
            susLevel.getColor()
        }
    }
}
```

#### 1.3 Backup Result Model
**Datei:** `data/model/BackupResult.kt`

```kotlin
package com.mgomanager.app.data.model

/**
 * Result of a backup operation
 */
sealed class BackupResult {
    data class Success(
        val account: Account,
        val message: String = "Backup erfolgreich erstellt"
    ) : BackupResult()
    
    data class Failure(
        val error: String,
        val exception: Exception? = null
    ) : BackupResult()
    
    data class PartialSuccess(
        val account: Account,
        val missingIds: List<String>,
        val message: String = "Backup erstellt, aber einige IDs fehlen"
    ) : BackupResult()
}

/**
 * Result of a restore operation
 */
sealed class RestoreResult {
    data class Success(
        val accountName: String,
        val message: String = "Wiederherstellung erfolgreich"
    ) : RestoreResult()
    
    data class Failure(
        val error: String,
        val exception: Exception? = null
    ) : RestoreResult()
}
```

### 2. Room Database Entities

#### 2.1 Account Entity
**Datei:** `data/local/database/entities/AccountEntity.kt`

```kotlin
package com.mgomanager.app.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.SusLevel

/**
 * Room entity for storing account data
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // User-defined
    val accountName: String,
    val prefix: String = "",
    
    // Timestamps (Unix milliseconds)
    val createdAt: Long,
    val lastPlayedAt: Long,
    
    // Extracted IDs
    val userId: String,
    val gaid: String = "nicht vorhanden",
    val deviceToken: String = "nicht vorhanden",
    val appSetId: String = "nicht vorhanden",
    val ssaid: String = "nicht vorhanden",
    
    // Status flags (stored as Int and Boolean for Room)
    val susLevelValue: Int = 0, // 0, 3, 7, 99
    val hasError: Boolean = false,
    
    // Facebook data
    val hasFacebookLink: Boolean = false,
    val fbUsername: String? = null,
    val fbPassword: String? = null,
    val fb2FA: String? = null,
    val fbTempMail: String? = null,
    
    // File system metadata
    val backupPath: String,
    val fileOwner: String,
    val fileGroup: String,
    val filePermissions: String
)

/**
 * Extension functions for conversion between Entity and Domain Model
 */
fun AccountEntity.toDomain(): Account {
    return Account(
        id = id,
        accountName = accountName,
        prefix = prefix,
        createdAt = createdAt,
        lastPlayedAt = lastPlayedAt,
        userId = userId,
        gaid = gaid,
        deviceToken = deviceToken,
        appSetId = appSetId,
        ssaid = ssaid,
        susLevel = SusLevel.fromValue(susLevelValue),
        hasError = hasError,
        hasFacebookLink = hasFacebookLink,
        fbUsername = fbUsername,
        fbPassword = fbPassword,
        fb2FA = fb2FA,
        fbTempMail = fbTempMail,
        backupPath = backupPath,
        fileOwner = fileOwner,
        fileGroup = fileGroup,
        filePermissions = filePermissions
    )
}

fun Account.toEntity(): AccountEntity {
    return AccountEntity(
        id = id,
        accountName = accountName,
        prefix = prefix,
        createdAt = createdAt,
        lastPlayedAt = lastPlayedAt,
        userId = userId,
        gaid = gaid,
        deviceToken = deviceToken,
        appSetId = appSetId,
        ssaid = ssaid,
        susLevelValue = susLevel.value,
        hasError = hasError,
        hasFacebookLink = hasFacebookLink,
        fbUsername = fbUsername,
        fbPassword = fbPassword,
        fb2FA = fb2FA,
        fbTempMail = fbTempMail,
        backupPath = backupPath,
        fileOwner = fileOwner,
        fileGroup = fileGroup,
        filePermissions = filePermissions
    )
}
```

#### 2.2 Log Entity
**Datei:** `data/local/database/entities/LogEntity.kt`

```kotlin
package com.mgomanager.app.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

/**
 * Room entity for storing application logs
 */
@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val sessionId: String, // UUID for each app start
    val timestamp: Long,   // Unix milliseconds
    val level: String,     // "INFO", "WARNING", "ERROR"
    val operation: String, // "BACKUP", "RESTORE", "ERROR", "APP_START"
    val accountName: String? = null,
    val message: String,
    val stackTrace: String? = null
)

/**
 * Log level enum for type safety
 */
enum class LogLevel {
    INFO,
    WARNING,
    ERROR;
    
    companion object {
        fun fromString(value: String): LogLevel {
            return values().find { it.name == value } ?: INFO
        }
    }
}

/**
 * Extension function to format timestamp
 */
fun LogEntity.getFormattedTimestamp(): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.GERMAN)
    return sdf.format(Date(timestamp))
}

/**
 * Extension function to get color based on level
 */
fun LogEntity.getLevelColor(): androidx.compose.ui.graphics.Color {
    return when (LogLevel.fromString(level)) {
        LogLevel.INFO -> androidx.compose.ui.graphics.Color.Gray
        LogLevel.WARNING -> com.mgomanager.app.ui.theme.StatusOrange
        LogLevel.ERROR -> com.mgomanager.app.ui.theme.StatusRed
    }
}
```

### 3. Room DAOs

#### 3.1 Account DAO
**Datei:** `data/local/database/dao/AccountDao.kt`

```kotlin
package com.mgomanager.app.data.local.database.dao

import androidx.room.*
import com.mgomanager.app.data.local.database.entities.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Account operations
 */
@Dao
interface AccountDao {
    
    @Query("SELECT * FROM accounts ORDER BY lastPlayedAt DESC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>
    
    @Query("SELECT * FROM accounts ORDER BY lastPlayedAt DESC")
    suspend fun getAllAccounts(): List<AccountEntity>
    
    @Query("SELECT * FROM accounts WHERE id = :accountId")
    suspend fun getAccountById(accountId: Long): AccountEntity?
    
    @Query("SELECT * FROM accounts WHERE id = :accountId")
    fun getAccountByIdFlow(accountId: Long): Flow<AccountEntity?>
    
    @Query("SELECT * FROM accounts WHERE accountName = :name")
    suspend fun getAccountByName(name: String): AccountEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long
    
    @Update
    suspend fun updateAccount(account: AccountEntity)
    
    @Delete
    suspend fun deleteAccount(account: AccountEntity)
    
    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccountById(accountId: Long)
    
    @Query("SELECT COUNT(*) FROM accounts")
    fun getAccountCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM accounts WHERE hasError = 1")
    fun getErrorAccountCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM accounts WHERE susLevelValue > 0")
    fun getSusAccountCount(): Flow<Int>
    
    @Query("UPDATE accounts SET lastPlayedAt = :timestamp WHERE id = :accountId")
    suspend fun updateLastPlayedTimestamp(accountId: Long, timestamp: Long)
    
    @Query("UPDATE accounts SET susLevelValue = :susLevel WHERE id = :accountId")
    suspend fun updateSusLevel(accountId: Long, susLevel: Int)
    
    @Query("UPDATE accounts SET hasError = :hasError WHERE id = :accountId")
    suspend fun updateErrorStatus(accountId: Long, hasError: Boolean)
}
```

#### 3.2 Log DAO
**Datei:** `data/local/database/dao/LogDao.kt`

```kotlin
package com.mgomanager.app.data.local.database.dao

import androidx.room.*
import com.mgomanager.app.data.local.database.entities.LogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Log operations
 */
@Dao
interface LogDao {
    
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<LogEntity>>
    
    @Query("SELECT * FROM logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getLogsBySession(sessionId: String): List<LogEntity>
    
    @Query("SELECT DISTINCT sessionId FROM logs ORDER BY timestamp DESC LIMIT 5")
    suspend fun getLastFiveSessions(): List<String>
    
    @Query("""
        SELECT * FROM logs 
        WHERE sessionId IN (
            SELECT DISTINCT sessionId FROM logs 
            ORDER BY timestamp DESC 
            LIMIT 5
        )
        ORDER BY timestamp DESC
    """)
    fun getLastFiveSessionsLogsFlow(): Flow<List<LogEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<LogEntity>)
    
    @Query("DELETE FROM logs WHERE sessionId NOT IN (SELECT DISTINCT sessionId FROM logs ORDER BY timestamp DESC LIMIT 5)")
    suspend fun deleteOldSessions()
    
    @Query("DELETE FROM logs")
    suspend fun deleteAllLogs()
    
    @Query("SELECT COUNT(DISTINCT sessionId) FROM logs")
    suspend fun getSessionCount(): Int
}
```

### 4. Room Database

#### 4.1 App Database
**Datei:** `data/local/database/AppDatabase.kt`

```kotlin
package com.mgomanager.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mgomanager.app.data.local.database.dao.AccountDao
import com.mgomanager.app.data.local.database.dao.LogDao
import com.mgomanager.app.data.local.database.entities.AccountEntity
import com.mgomanager.app.data.local.database.entities.LogEntity

/**
 * Main Room Database for MGO Manager
 */
@Database(
    entities = [AccountEntity::class, LogEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun logDao(): LogDao
    
    companion object {
        const val DATABASE_NAME = "mgo_manager.db"
    }
}
```

### 5. DataStore for Settings

#### 5.1 Settings Data Store
**Datei:** `data/local/preferences/SettingsDataStore.kt`

```kotlin
package com.mgomanager.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        
        const val DEFAULT_PREFIX = "MGO_"
        const val DEFAULT_BACKUP_PATH = "/storage/emulated/0/mgo/backups/"
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
}
```

### 6. Repositories

#### 6.1 Account Repository
**Datei:** `data/repository/AccountRepository.kt`

```kotlin
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
}
```

#### 6.2 Log Repository
**Datei:** `data/repository/LogRepository.kt`

```kotlin
package com.mgomanager.app.data.repository

import com.mgomanager.app.data.local.database.dao.LogDao
import com.mgomanager.app.data.local.database.entities.LogEntity
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Log-related operations
 */
@Singleton
class LogRepository @Inject constructor(
    private val logDao: LogDao,
    private val settingsDataStore: SettingsDataStore
) {
    
    /**
     * Get all logs
     */
    fun getAllLogs(): Flow<List<LogEntity>> {
        return logDao.getAllLogsFlow()
    }
    
    /**
     * Get logs from last 5 sessions
     */
    fun getLastFiveSessionsLogs(): Flow<List<LogEntity>> {
        return logDao.getLastFiveSessionsLogsFlow()
    }
    
    /**
     * Get logs for specific session
     */
    suspend fun getLogsBySession(sessionId: String): List<LogEntity> {
        return logDao.getLogsBySession(sessionId)
    }
    
    /**
     * Add a new log entry
     */
    suspend fun addLog(
        level: String,
        operation: String,
        message: String,
        accountName: String? = null,
        stackTrace: String? = null
    ) {
        val sessionId = settingsDataStore.currentSessionId.first()
        val log = LogEntity(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            level = level,
            operation = operation,
            accountName = accountName,
            message = message,
            stackTrace = stackTrace
        )
        logDao.insertLog(log)
    }
    
    /**
     * Add info log
     */
    suspend fun logInfo(operation: String, message: String, accountName: String? = null) {
        addLog("INFO", operation, message, accountName)
    }
    
    /**
     * Add warning log
     */
    suspend fun logWarning(operation: String, message: String, accountName: String? = null) {
        addLog("WARNING", operation, message, accountName)
    }
    
    /**
     * Add error log
     */
    suspend fun logError(
        operation: String, 
        message: String, 
        accountName: String? = null,
        exception: Exception? = null
    ) {
        addLog("ERROR", operation, message, accountName, exception?.stackTraceToString())
    }
    
    /**
     * Clean up old sessions (keep only last 5)
     */
    suspend fun cleanupOldSessions() {
        logDao.deleteOldSessions()
    }
    
    /**
     * Delete all logs
     */
    suspend fun deleteAllLogs() {
        logDao.deleteAllLogs()
    }
}
```

### 7. Hilt Modules

#### 7.1 Database Module
**Datei:** `di/DatabaseModule.kt`

```kotlin
package com.mgomanager.app.di

import android.content.Context
import androidx.room.Room
import com.mgomanager.app.data.local.database.AppDatabase
import com.mgomanager.app.data.local.database.dao.AccountDao
import com.mgomanager.app.data.local.database.dao.LogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // For development, remove in production
            .build()
    }
    
    @Provides
    @Singleton
    fun provideAccountDao(database: AppDatabase): AccountDao {
        return database.accountDao()
    }
    
    @Provides
    @Singleton
    fun provideLogDao(database: AppDatabase): LogDao {
        return database.logDao()
    }
}
```

#### 7.2 Repository Module
**Datei:** `di/RepositoryModule.kt`

```kotlin
package com.mgomanager.app.di

import com.mgomanager.app.data.local.database.dao.AccountDao
import com.mgomanager.app.data.local.database.dao.LogDao
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideAccountRepository(
        accountDao: AccountDao
    ): AccountRepository {
        return AccountRepository(accountDao)
    }
    
    @Provides
    @Singleton
    fun provideLogRepository(
        logDao: LogDao,
        settingsDataStore: SettingsDataStore
    ): LogRepository {
        return LogRepository(logDao, settingsDataStore)
    }
}
```

### 8. Session Management

#### 8.1 Update Application Class
**Datei:** `MGOApplication.kt` (Update)

Füge am Ende der `onCreate()` Methode hinzu:

```kotlin
// Session management (add to existing onCreate)
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MGOApplication : Application() {
    
    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    
    @Inject
    lateinit var logRepository: LogRepository
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        // ... existing code ...
        
        // Initialize new session
        applicationScope.launch {
            val sessionId = settingsDataStore.generateNewSession()
            settingsDataStore.incrementAppStartCount()
            
            logRepository.addLog(
                level = "INFO",
                operation = "APP_START",
                message = "MGO Manager gestartet (Session: ${sessionId.take(8)}...)"
            )
            
            // Cleanup old sessions
            logRepository.cleanupOldSessions()
        }
    }
}
```

---

## Testing

### Unit Tests

#### Test 1: SusLevel Conversion
**Datei:** `test/java/com/mgomanager/app/SusLevelTest.kt`

```kotlin
package com.mgomanager.app

import com.mgomanager.app.data.model.SusLevel
import org.junit.Test
import org.junit.Assert.*

class SusLevelTest {
    
    @Test
    fun testFromValue() {
        assertEquals(SusLevel.NONE, SusLevel.fromValue(0))
        assertEquals(SusLevel.LEVEL_3, SusLevel.fromValue(3))
        assertEquals(SusLevel.LEVEL_7, SusLevel.fromValue(7))
        assertEquals(SusLevel.PERMANENT, SusLevel.fromValue(99))
    }
    
    @Test
    fun testInvalidValue() {
        assertEquals(SusLevel.NONE, SusLevel.fromValue(999))
    }
    
    @Test
    fun testDisplayNames() {
        assertEquals("Keine", SusLevel.NONE.displayName)
        assertEquals("Level 3", SusLevel.LEVEL_3.displayName)
        assertEquals("Level 7", SusLevel.LEVEL_7.displayName)
        assertEquals("Permanent", SusLevel.PERMANENT.displayName)
    }
}
```

#### Test 2: Entity to Domain Conversion
**Datei:** `test/java/com/mgomanager/app/AccountConversionTest.kt`

```kotlin
package com.mgomanager.app

import com.mgomanager.app.data.local.database.entities.AccountEntity
import com.mgomanager.app.data.local.database.entities.toDomain
import com.mgomanager.app.data.local.database.entities.toEntity
import com.mgomanager.app.data.model.SusLevel
import org.junit.Test
import org.junit.Assert.*

class AccountConversionTest {
    
    @Test
    fun testEntityToDomainConversion() {
        val entity = AccountEntity(
            id = 1L,
            accountName = "TestAccount",
            prefix = "MGO_",
            createdAt = System.currentTimeMillis(),
            lastPlayedAt = System.currentTimeMillis(),
            userId = "123456",
            susLevelValue = 3,
            hasError = false,
            hasFacebookLink = false,
            backupPath = "/test/path",
            fileOwner = "u0_a123",
            fileGroup = "u0_a123",
            filePermissions = "755"
        )
        
        val domain = entity.toDomain()
        
        assertEquals(entity.accountName, domain.accountName)
        assertEquals(SusLevel.LEVEL_3, domain.susLevel)
        assertEquals("MGO_TestAccount", domain.fullName)
    }
    
    @Test
    fun testDomainToEntityConversion() {
        val domain = com.mgomanager.app.data.model.Account(
            id = 1L,
            accountName = "TestAccount",
            prefix = "MGO_",
            createdAt = System.currentTimeMillis(),
            lastPlayedAt = System.currentTimeMillis(),
            userId = "123456",
            susLevel = SusLevel.LEVEL_7,
            hasError = true,
            hasFacebookLink = false,
            backupPath = "/test/path",
            fileOwner = "u0_a123",
            fileGroup = "u0_a123",
            filePermissions = "755"
        )
        
        val entity = domain.toEntity()
        
        assertEquals(domain.accountName, entity.accountName)
        assertEquals(7, entity.susLevelValue)
        assertTrue(entity.hasError)
    }
}
```

### Integration Tests

#### Database Test
**Datei:** `androidTest/java/com/mgomanager/app/DatabaseTest.kt`

```kotlin
package com.mgomanager.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mgomanager.app.data.local.database.AppDatabase
import com.mgomanager.app.data.local.database.dao.AccountDao
import com.mgomanager.app.data.local.database.entities.AccountEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    
    private lateinit var database: AppDatabase
    private lateinit var accountDao: AccountDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        accountDao = database.accountDao()
    }
    
    @After
    fun cleanup() {
        database.close()
    }
    
    @Test
    fun testInsertAndRetrieveAccount() = runBlocking {
        val account = AccountEntity(
            accountName = "TestAccount",
            prefix = "MGO_",
            createdAt = System.currentTimeMillis(),
            lastPlayedAt = System.currentTimeMillis(),
            userId = "123456",
            susLevelValue = 0,
            hasError = false,
            hasFacebookLink = false,
            backupPath = "/test/path",
            fileOwner = "u0_a123",
            fileGroup = "u0_a123",
            filePermissions = "755"
        )
        
        val id = accountDao.insertAccount(account)
        val retrieved = accountDao.getAccountById(id)
        
        assertNotNull(retrieved)
        assertEquals(account.accountName, retrieved?.accountName)
        assertEquals(account.userId, retrieved?.userId)
    }
    
    @Test
    fun testUpdateAccount() = runBlocking {
        val account = AccountEntity(
            accountName = "TestAccount",
            prefix = "MGO_",
            createdAt = System.currentTimeMillis(),
            lastPlayedAt = System.currentTimeMillis(),
            userId = "123456",
            susLevelValue = 0,
            hasError = false,
            hasFacebookLink = false,
            backupPath = "/test/path",
            fileOwner = "u0_a123",
            fileGroup = "u0_a123",
            filePermissions = "755"
        )
        
        val id = accountDao.insertAccount(account)
        accountDao.updateSusLevel(id, 3)
        
        val updated = accountDao.getAccountById(id)
        assertEquals(3, updated?.susLevelValue)
    }
}
```

### Manual Testing Checklist

**Database Operations:**
- [ ] App startet ohne Crash
- [ ] Datenbank wird erstellt (`/data/data/com.mgomanager.app/databases/mgo_manager.db`)
- [ ] Test-Account kann eingefügt werden
- [ ] Test-Account kann abgerufen werden
- [ ] Account-Update funktioniert
- [ ] Account-Löschung funktioniert

**DataStore:**
- [ ] Settings werden gespeichert
- [ ] Settings bleiben nach App-Neustart erhalten
- [ ] Session-ID wird bei jedem Start neu generiert

**Logging:**
- [ ] App-Start wird geloggt
- [ ] Session-ID wird korrekt generiert
- [ ] Alte Sessions werden gelöscht (> 5)

**Build:**
- [ ] Keine Compile-Fehler
- [ ] Keine Hilt-Injection-Fehler
- [ ] Room Schema wird generiert

---

## Troubleshooting

### Problem: Room Schema Export Error
**Symptom:** `Cannot find annotation processor 'androidx.room.RoomProcessor'`  
**Lösung:**
1. Stelle sicher dass `ksp` Plugin aktiviert ist
2. Use `ksp("androidx.room:room-compiler:...")` statt `kapt`
3. Clean + Rebuild

### Problem: Hilt Injection für DataStore fehlschlägt
**Symptom:** `SettingsDataStore` kann nicht injected werden  
**Lösung:**
1. Prüfe `@Singleton` Annotation
2. Stelle sicher dass `@ApplicationContext` verwendet wird
3. Rebuild Projekt

### Problem: Flow emissions kommen nicht an
**Symptom:** UI updates nicht wenn Datenbank sich ändert  
**Lösung:**
1. Prüfe dass DAO `Flow<T>` zurückgibt
2. Verwende `collectAsState()` in Compose
3. Stelle sicher dass Updates in IO Coroutine laufen

### Problem: "Cannot access database on main thread"
**Symptom:** App crasht bei DB-Zugriff  
**Lösung:**
1. Alle DB-Operationen in `suspend` Funktionen
2. Aufrufe in `viewModelScope.launch` oder `withContext(Dispatchers.IO)`
3. Niemals `.allowMainThreadQueries()` verwenden

---

## Abschlusskriterien

Bevor du mit Prompt 3 fortfährst, müssen ALLE folgenden Punkte erfüllt sein:

- [ ] Alle Entities sind erstellt und kompilieren
- [ ] Alle DAOs sind implementiert
- [ ] Room Database erstellt und funktioniert
- [ ] DataStore für Settings funktioniert
- [ ] Beide Repositories implementiert
- [ ] Hilt Module für Database + Repository konfiguriert
- [ ] Session-Management funktioniert (neue Session bei App-Start)
- [ ] Logs werden in Datenbank geschrieben
- [ ] Unit Tests bestehen
- [ ] Integration Tests (Database) bestehen
- [ ] Test-Account kann in Datenbank eingefügt und abgerufen werden
- [ ] App startet ohne Fehler

---

## Nächste Schritte

**Wenn alle Aufgaben abgeschlossen wurden und alle Tests erfolgreich bestanden wurden, lese `Initial_Entwicklungsprompt_3.md` und setze die Aufgaben um.**

---

*Entwicklungsprompt 2/7 - Erstellt für MGO Manager Projekt*
