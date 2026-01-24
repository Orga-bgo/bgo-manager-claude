# Initial Development Prompt 4: Restore Logic Implementation

## Übersicht
Implementation der Restore-Logik: File-Wiederherstellung mit Root, Permission-Restauration, Game-Prozess-Management und Timestamp-Updates.

## Voraussetzungen
- Erfolgreicher Abschluss von Prompt 3
- Funktionierende Backup-Logik
- CLAUDE.md zur Hand

---

## Aufgaben

### 1. Restore Use Case

**Datei:** `domain/usecase/RestoreBackupUseCase.kt`

```kotlin
package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FilePermissionManager
import com.mgomanager.app.domain.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class RestoreBackupUseCase @Inject constructor(
    private val rootUtil: RootUtil,
    private val permissionManager: FilePermissionManager,
    private val accountRepository: AccountRepository,
    private val logRepository: LogRepository
) {
    
    companion object {
        const val MGO_DATA_PATH = "/data/data/com.scopely.monopolygo"
        const val MGO_FILES_PATH = "$MGO_DATA_PATH/files"
        const val MGO_PREFS_PATH = "$MGO_DATA_PATH/shared_prefs"
        const val SSAID_PATH = "/data/system/users/0/settings_ssaid.xml"
    }
    
    suspend fun execute(accountId: Long): RestoreResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: Get account from database
            val account = accountRepository.getAccountById(accountId)
                ?: return@withContext RestoreResult.Failure("Account nicht gefunden")
            
            logRepository.logInfo("RESTORE", "Starte Restore für ${account.fullName}")
            
            // Step 2: Validate backup files exist
            val backupPath = account.backupPath
            if (!validateBackupFiles(backupPath)) {
                logRepository.logError("RESTORE", "Backup-Dateien fehlen", account.fullName)
                return@withContext RestoreResult.Failure("Backup-Dateien fehlen oder sind beschädigt")
            }
            
            // Step 3: Force stop Monopoly Go
            rootUtil.forceStopMonopolyGo().getOrThrow()
            logRepository.logInfo("RESTORE", "Monopoly Go gestoppt", account.fullName)
            
            // Step 4: Copy directories back
            copyBackupDirectory("${backupPath}DiskBasedCacheDirectory/", "$MGO_FILES_PATH/DiskBasedCacheDirectory/", account.fullName)
            copyBackupDirectory("${backupPath}shared_prefs/", MGO_PREFS_PATH, account.fullName)
            
            // Step 5: Copy SSAID file back
            val ssaidFile = File("${backupPath}settings_ssaid.xml")
            if (ssaidFile.exists()) {
                rootUtil.executeCommand("cp ${backupPath}settings_ssaid.xml $SSAID_PATH").getOrThrow()
                logRepository.logInfo("RESTORE", "SSAID wiederhergestellt", account.fullName)
            }
            
            // Step 6: Restore permissions
            permissionManager.setFileOwnership(
                MGO_FILES_PATH,
                account.fileOwner,
                account.fileGroup
            ).getOrThrow()
            
            permissionManager.setFileOwnership(
                MGO_PREFS_PATH,
                account.fileOwner,
                account.fileGroup
            ).getOrThrow()
            
            permissionManager.setFilePermissions(MGO_FILES_PATH, account.filePermissions).getOrThrow()
            permissionManager.setFilePermissions(MGO_PREFS_PATH, account.filePermissions).getOrThrow()
            
            logRepository.logInfo("RESTORE", "Berechtigungen wiederhergestellt", account.fullName)
            
            // Step 7: Update lastPlayedAt timestamp
            accountRepository.updateLastPlayedTimestamp(accountId)
            
            logRepository.logInfo("RESTORE", "Restore erfolgreich abgeschlossen", account.fullName)
            RestoreResult.Success(account.fullName)
            
        } catch (e: Exception) {
            logRepository.logError("RESTORE", "Restore fehlgeschlagen: ${e.message}", null, e)
            RestoreResult.Failure("Restore fehlgeschlagen: ${e.message}", e)
        }
    }
    
    private fun validateBackupFiles(backupPath: String): Boolean {
        val diskCacheDir = File("${backupPath}DiskBasedCacheDirectory/")
        val sharedPrefsDir = File("${backupPath}shared_prefs/")
        
        return diskCacheDir.exists() && sharedPrefsDir.exists()
    }
    
    private suspend fun copyBackupDirectory(source: String, destination: String, accountName: String) {
        val result = rootUtil.executeCommand("cp -r $source $destination")
        if (result.isSuccess) {
            logRepository.logInfo("RESTORE", "Verzeichnis wiederhergestellt: $source -> $destination", accountName)
        } else {
            logRepository.logError("RESTORE", "Fehler beim Wiederherstellen: $source", accountName)
            throw Exception("Verzeichnis konnte nicht wiederhergestellt werden: $source")
        }
    }
}
```

### 2. Update RootUtil

**Datei:** `domain/util/RootUtil.kt` (Update - add launch method)

```kotlin
/**
 * Launch Monopoly Go
 */
suspend fun launchMonopolyGo(): Result<Unit> = withContext(Dispatchers.IO) {
    executeCommand("monkey -p com.scopely.monopolygo -c android.intent.category.LAUNCHER 1").map { }
}
```

### 3. Update Backup Repository

**Datei:** `data/repository/BackupRepository.kt` (Update - add restore)

```kotlin
@Singleton
class BackupRepository @Inject constructor(
    private val createBackupUseCase: CreateBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase
) {
    
    suspend fun createBackup(request: BackupRequest): BackupResult {
        return createBackupUseCase.execute(request)
    }
    
    suspend fun restoreBackup(accountId: Long): RestoreResult {
        return restoreBackupUseCase.execute(accountId)
    }
}
```

---

## Testing

### Manual Tests

**Test 1: Backup Validation**
```kotlin
val backupPath = "/storage/emulated/0/mgo/backups/MGO_Test_01/"
val diskCache = File("${backupPath}DiskBasedCacheDirectory/")
val sharedPrefs = File("${backupPath}shared_prefs/")
println("Backup valid: ${diskCache.exists() && sharedPrefs.exists()}")
```

**Test 2: Complete Restore Flow**
```kotlin
scope.launch {
    val result = backupRepository.restoreBackup(accountId = 1L)
    when (result) {
        is RestoreResult.Success -> println("Restore erfolgreich")
        is RestoreResult.Failure -> println("Fehler: ${result.error}")
    }
}
```

**Test 3: Game Launch**
```kotlin
scope.launch {
    val result = rootUtil.launchMonopolyGo()
    println("Launch result: $result")
}
```

### Checklist
- [ ] Backup-Dateien werden validiert
- [ ] Monopoly Go wird gestoppt
- [ ] Dateien werden zurückkopiert
- [ ] SSAID wird wiederhergestellt
- [ ] Permissions werden korrekt gesetzt
- [ ] lastPlayedAt wird aktualisiert
- [ ] Logs werden geschrieben
- [ ] Game-Launch funktioniert

---

## Troubleshooting

### Problem: "File not found" bei Restore
**Lösung:** Prüfe ob Backup-Pfad korrekt in DB gespeichert, validiere Backup-Dateien

### Problem: Permission-Fehler nach Restore
**Lösung:** Prüfe dass owner/group/permissions korrekt aus DB gelesen werden

### Problem: Game startet nicht nach Restore
**Lösung:** Prüfe dass alle Dateien kopiert wurden, teste manuellen Start

---

## Abschlusskriterien

- [ ] Kompletter Restore-Flow funktioniert
- [ ] Dateien werden korrekt wiederhergestellt
- [ ] Permissions werden restauriert
- [ ] Timestamp wird aktualisiert
- [ ] Game kann gestartet werden
- [ ] Error-Handling funktioniert

**Wenn alle Aufgaben abgeschlossen wurden und alle Tests erfolgreich bestanden wurden, lese `Initial_Entwicklungsprompt_5.md` und setze die Aufgaben um.**

---

*Entwicklungsprompt 4/7 - Erstellt für MGO Manager Projekt*
