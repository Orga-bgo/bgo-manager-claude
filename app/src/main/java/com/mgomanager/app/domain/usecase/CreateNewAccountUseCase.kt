package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.SusLevel
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.IdGenerator
import com.mgomanager.app.domain.util.RootUtil
import com.mgomanager.app.domain.util.SsaidManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Request data for creating a new account.
 */
data class CreateNewAccountRequest(
    val accountName: String,
    val prefix: String
)

/**
 * Result of creating a new account.
 */
sealed class CreateNewAccountResult {
    data class Success(val accountId: Long, val accountName: String) : CreateNewAccountResult()
    data class Failure(val error: String, val exception: Exception? = null) : CreateNewAccountResult()
    data class ValidationError(val error: String) : CreateNewAccountResult()
}

/**
 * UseCase for creating a completely new Monopoly GO account.
 *
 * This clears existing game data and generates fresh device identifiers
 * that will be spoofed by the LSPosed hooks when the game starts.
 */
class CreateNewAccountUseCase @Inject constructor(
    private val rootUtil: RootUtil,
    private val accountRepository: AccountRepository,
    private val logRepository: LogRepository,
    private val ssaidManager: SsaidManager
) {

    companion object {
        const val MGO_PACKAGE = "com.scopely.monopolygo"
        const val MGO_DATA_PATH = "/data/data/$MGO_PACKAGE"
        const val MGO_PREFS_PATH = "$MGO_DATA_PATH/shared_prefs"
        const val MGO_CACHE_PATH = "$MGO_DATA_PATH/files/DiskBasedCacheDirectory"

        // Shared file for Xposed hook - world-readable location
        const val XPOSED_SHARED_FILE = "/data/local/tmp/mgo_current_appsetid.txt"

        // Validation constants
        const val MIN_NAME_LENGTH = 3
        const val MAX_NAME_LENGTH = 30
        val NAME_PATTERN = Regex("^[a-zA-Z0-9_-]+$")
    }

    /**
     * Execute the "Create New Account" operation.
     *
     * Steps:
     * 1. Validate account name
     * 2. Check root access
     * 3. Force-stop Monopoly Go
     * 4. Clear game data (shared_prefs and cache)
     * 5. Generate all new IDs
     * 6. Write SSAID to system settings
     * 7. Save account to database
     * 8. Write hook data file for LSPosed
     * 9. Start Monopoly Go
     */
    suspend fun execute(request: CreateNewAccountRequest): CreateNewAccountResult = withContext(Dispatchers.IO) {
        try {
            val fullName = "${request.prefix}${request.accountName}"
            logRepository.logInfo("CREATE_NEW", "Starte Account-Erstellung für $fullName")

            // Step 1: Validate account name
            val validationError = validateAccountName(request.accountName)
            if (validationError != null) {
                logRepository.logWarning("CREATE_NEW", "Validierungsfehler: $validationError")
                return@withContext CreateNewAccountResult.ValidationError(validationError)
            }

            // Check if account name already exists
            val existingAccount = accountRepository.getAccountByName(fullName)
            if (existingAccount != null) {
                logRepository.logWarning("CREATE_NEW", "Account existiert bereits: $fullName")
                return@withContext CreateNewAccountResult.ValidationError("Account '$fullName' existiert bereits")
            }

            // Step 2: Check root access
            if (!rootUtil.requestRootAccess()) {
                logRepository.logError("CREATE_NEW", "Root-Zugriff verweigert")
                return@withContext CreateNewAccountResult.Failure("Root-Zugriff erforderlich")
            }
            logRepository.logInfo("CREATE_NEW", "Root-Zugriff bestätigt", fullName)

            // Step 3: Force stop Monopoly Go
            rootUtil.forceStopMonopolyGo().getOrElse {
                logRepository.logWarning("CREATE_NEW", "Konnte Monopoly Go nicht stoppen: ${it.message}", fullName)
            }
            logRepository.logInfo("CREATE_NEW", "Monopoly Go gestoppt", fullName)

            // Step 4: Clear game data
            clearGameData(fullName)
            logRepository.logInfo("CREATE_NEW", "Spieldaten gelöscht", fullName)

            // Step 5: Generate all new IDs
            val generatedIds = IdGenerator.generateAllIds(request.accountName)
            logRepository.logInfo("CREATE_NEW", "IDs generiert - AndroidId: ${generatedIds.androidId}, AppSetId: ${generatedIds.appSetIdApp}", fullName)

            // Step 6: Write SSAID to system settings
            val ssaidSuccess = ssaidManager.setAndroidIdForPackage(
                packageName = MGO_PACKAGE,
                androidId = generatedIds.androidId
            )
            if (ssaidSuccess) {
                logRepository.logInfo("CREATE_NEW", "SSAID erfolgreich geschrieben: ${generatedIds.androidId}", fullName)
            } else {
                logRepository.logWarning("CREATE_NEW", "SSAID konnte nicht geschrieben werden, Hook wird verwendet", fullName)
            }

            // Step 7: Save account to database
            val now = System.currentTimeMillis()
            val account = Account(
                accountName = request.accountName,
                prefix = request.prefix,
                createdAt = now,
                lastPlayedAt = now,
                // For new accounts, we generate a placeholder userId until the game creates a real one
                userId = "NEW_${generatedIds.androidId.take(8)}",
                gaid = generatedIds.gaid,
                deviceToken = "nicht vorhanden",
                appSetId = generatedIds.appSetIdApp,
                ssaid = generatedIds.androidId,
                susLevel = SusLevel.NONE,
                hasError = false,
                hasFacebookLink = false,
                // New accounts don't have a backup path initially
                backupPath = "",
                fileOwner = "u0_a",
                fileGroup = "u0_a",
                filePermissions = "755",
                isLastRestored = true, // Mark as active for hooks
                // Extended IDs
                deviceName = generatedIds.deviceName,
                androidId = generatedIds.androidId,
                appSetIdApp = generatedIds.appSetIdApp,
                appSetIdDev = generatedIds.appSetIdDev,
                gsfId = generatedIds.gsfId,
                generatedGaid = generatedIds.gaid
            )

            val accountId = accountRepository.insertAccount(account)
            logRepository.logInfo("CREATE_NEW", "Account in Datenbank gespeichert mit ID: $accountId", fullName)

            // Mark this account as last restored (clears flag from others)
            accountRepository.markAsLastRestored(accountId)

            // Step 8: Write hook data file
            writeHookDataFile(generatedIds, request.accountName)
            logRepository.logInfo("CREATE_NEW", "Hook-Datei geschrieben", fullName)

            // Step 9: Start Monopoly Go
            startMonopolyGo()
            logRepository.logInfo("CREATE_NEW", "Monopoly Go gestartet", fullName)

            logRepository.logInfo("CREATE_NEW", "Account-Erstellung erfolgreich abgeschlossen", fullName)
            CreateNewAccountResult.Success(accountId, fullName)

        } catch (e: Exception) {
            logRepository.logError("CREATE_NEW", "Account-Erstellung fehlgeschlagen: ${e.message}", null, e)
            CreateNewAccountResult.Failure("Account-Erstellung fehlgeschlagen: ${e.message}", e)
        }
    }

    /**
     * Validate account name.
     * Returns error message if invalid, null if valid.
     */
    private fun validateAccountName(name: String): String? {
        return when {
            name.isBlank() -> "Account-Name darf nicht leer sein"
            name.length < MIN_NAME_LENGTH -> "Account-Name muss mindestens $MIN_NAME_LENGTH Zeichen haben"
            name.length > MAX_NAME_LENGTH -> "Account-Name darf maximal $MAX_NAME_LENGTH Zeichen haben"
            !NAME_PATTERN.matches(name) -> "Account-Name darf nur Buchstaben, Zahlen, _ und - enthalten"
            else -> null
        }
    }

    /**
     * Clear Monopoly Go game data (shared_prefs and cache directories).
     */
    private suspend fun clearGameData(accountName: String) {
        // Delete shared_prefs directory contents
        val prefsResult = rootUtil.executeCommand("rm -rf $MGO_PREFS_PATH/*")
        if (prefsResult.isFailure) {
            logRepository.logWarning("CREATE_NEW", "Fehler beim Löschen von shared_prefs: ${prefsResult.exceptionOrNull()?.message}", accountName)
        }

        // Delete cache directory contents
        val cacheResult = rootUtil.executeCommand("rm -rf $MGO_CACHE_PATH/*")
        if (cacheResult.isFailure) {
            logRepository.logWarning("CREATE_NEW", "Fehler beim Löschen des Cache: ${cacheResult.exceptionOrNull()?.message}", accountName)
        }
    }

    /**
     * Write the hook data file for LSPosed.
     *
     * Extended format: appSetId|ssaid|deviceName|gsfId|gaid|appSetIdDev|accountName|timestamp
     */
    private suspend fun writeHookDataFile(
        ids: com.mgomanager.app.domain.util.GeneratedIds,
        accountName: String
    ) {
        val timestamp = System.currentTimeMillis()
        val hookData = listOf(
            ids.appSetIdApp,
            ids.androidId,
            ids.deviceName,
            ids.gsfId,
            ids.gaid,
            ids.appSetIdDev,
            accountName,
            timestamp.toString()
        ).joinToString("|")

        // Write file using root
        rootUtil.executeCommand("echo '$hookData' > $XPOSED_SHARED_FILE")

        // Set permissions to world-readable (644)
        rootUtil.executeCommand("chmod 644 $XPOSED_SHARED_FILE")
    }

    /**
     * Start Monopoly Go app.
     */
    private suspend fun startMonopolyGo() {
        rootUtil.executeCommand("am start -n $MGO_PACKAGE/com.scopely.unity.ScopelyUnityActivity")
    }
}
