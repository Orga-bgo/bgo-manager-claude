package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.SusLevel
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.IdGenerator
import com.mgomanager.app.domain.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Request data for creating a new account.
 */
data class CreateNewAccountRequest(
    val accountName: String,
    val prefix: String
)

/**
 * Progress step during account creation.
 */
data class CreateAccountProgress(
    val step: Int,
    val totalSteps: Int,
    val message: String
) {
    val percentage: Int get() = ((step.toFloat() / totalSteps) * 100).toInt()
}

/**
 * Result of creating a new account.
 */
sealed class CreateNewAccountResult {
    data class Success(val accountId: Long, val accountName: String) : CreateNewAccountResult()
    data class Failure(val error: String, val exception: Exception? = null) : CreateNewAccountResult()
    data class ValidationError(val error: String) : CreateNewAccountResult()
    data class Progress(val progress: CreateAccountProgress) : CreateNewAccountResult()
}

/**
 * UseCase for creating a completely new Monopoly GO account.
 *
 * This clears existing game data and generates fresh device identifiers
 * that will be spoofed by the LSPosed hooks when the game starts.
 *
 * Note: Android ID (SSAID) spoofing is handled entirely via LSPosed hooks.
 * No system settings_ssaid.xml modification is needed.
 */
class CreateNewAccountUseCase @Inject constructor(
    private val rootUtil: RootUtil,
    private val accountRepository: AccountRepository,
    private val logRepository: LogRepository
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

        // Progress steps
        private const val TOTAL_STEPS = 7
    }

    /**
     * Execute the "Create New Account" operation with progress reporting.
     *
     * Steps:
     * 1. Validate account name & check root
     * 2. Force-stop Monopoly Go
     * 3. Clear game data (shared_prefs and cache)
     * 4. Generate all new IDs
     * 5. Save account to database
     * 6. Write hook data file for LSPosed
     * 7. Start Monopoly Go
     */
    fun executeWithProgress(request: CreateNewAccountRequest): Flow<CreateNewAccountResult> = flow {
        try {
            val fullName = "${request.prefix}${request.accountName}"
            logRepository.logInfo("CREATE_NEW", "Starte Account-Erstellung für $fullName")

            // Step 1: Validate and check root
            emit(CreateNewAccountResult.Progress(CreateAccountProgress(1, TOTAL_STEPS, "Validiere Eingaben...")))

            val validationError = validateAccountName(request.accountName)
            if (validationError != null) {
                logRepository.logWarning("CREATE_NEW", "Validierungsfehler: $validationError")
                emit(CreateNewAccountResult.ValidationError(validationError))
                return@flow
            }

            val existingAccount = accountRepository.getAccountByName(fullName)
            if (existingAccount != null) {
                logRepository.logWarning("CREATE_NEW", "Account existiert bereits: $fullName")
                emit(CreateNewAccountResult.ValidationError("Account '$fullName' existiert bereits"))
                return@flow
            }

            if (!rootUtil.requestRootAccess()) {
                logRepository.logError("CREATE_NEW", "Root-Zugriff verweigert")
                emit(CreateNewAccountResult.Failure("Root-Zugriff erforderlich"))
                return@flow
            }
            logRepository.logInfo("CREATE_NEW", "Root-Zugriff bestätigt", fullName)

            // Step 2: Force stop Monopoly Go
            emit(CreateNewAccountResult.Progress(CreateAccountProgress(2, TOTAL_STEPS, "Stoppe Monopoly Go...")))
            rootUtil.forceStopMonopolyGo().getOrElse {
                logRepository.logWarning("CREATE_NEW", "Konnte Monopoly Go nicht stoppen: ${it.message}", fullName)
            }
            logRepository.logInfo("CREATE_NEW", "Monopoly Go gestoppt", fullName)

            // Step 3: Clear game data
            emit(CreateNewAccountResult.Progress(CreateAccountProgress(3, TOTAL_STEPS, "Loesche Spieldaten...")))
            clearGameData(fullName)
            logRepository.logInfo("CREATE_NEW", "Spieldaten gelöscht", fullName)

            // Step 4: Generate all new IDs
            emit(CreateNewAccountResult.Progress(CreateAccountProgress(4, TOTAL_STEPS, "Generiere neue IDs...")))
            val generatedIds = IdGenerator.generateAllIds(request.accountName)
            logRepository.logInfo("CREATE_NEW", "IDs generiert - AndroidId: ${generatedIds.androidId}, AppSetId: ${generatedIds.appSetIdApp}", fullName)

            // Step 5: Save account to database
            emit(CreateNewAccountResult.Progress(CreateAccountProgress(5, TOTAL_STEPS, "Speichere Account...")))
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

            // Step 6: Write hook data file
            emit(CreateNewAccountResult.Progress(CreateAccountProgress(6, TOTAL_STEPS, "Schreibe Hook-Daten...")))
            writeHookDataFile(generatedIds, request.accountName)
            logRepository.logInfo("CREATE_NEW", "Hook-Datei geschrieben", fullName)

            // Step 7: Start Monopoly Go
            emit(CreateNewAccountResult.Progress(CreateAccountProgress(7, TOTAL_STEPS, "Starte Monopoly Go...")))
            startMonopolyGo()
            logRepository.logInfo("CREATE_NEW", "Monopoly Go gestartet", fullName)

            logRepository.logInfo("CREATE_NEW", "Account-Erstellung erfolgreich abgeschlossen", fullName)
            emit(CreateNewAccountResult.Success(accountId, fullName))

        } catch (e: Exception) {
            logRepository.logError("CREATE_NEW", "Account-Erstellung fehlgeschlagen: ${e.message}", null, e)
            emit(CreateNewAccountResult.Failure("Account-Erstellung fehlgeschlagen: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

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
     *
     * The LSPosed hook reads this file and spoofs all device identifiers including:
     * - App Set ID
     * - Android ID (SSAID)
     * - Device Name
     * - GSF ID
     * - Google Advertising ID (GAID)
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
