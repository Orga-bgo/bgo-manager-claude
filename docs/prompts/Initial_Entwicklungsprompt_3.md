# Initial Development Prompt 3: Backup Logic Implementation

## Übersicht
In dieser Phase wird die komplette Backup-Logik implementiert: File-Operationen mit Root, ID-Extraktion aus XML, SSAID-Regex-Extraktion, Permission-Management und Backup-Directory-Verwaltung.

## Voraussetzungen
- Erfolgreicher Abschluss von Prompt 2
- Funktionierende Datenbank und Repositories
- Root-Zugriff funktioniert
- CLAUDE.md zur Hand

---

## Aufgaben

### 1. ID Extractor Utility

**Datei:** `domain/util/IdExtractor.kt`

```kotlin
package com.mgomanager.app.domain.util

import org.w3c.dom.Element
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

data class ExtractedIds(
    val userId: String,
    val gaid: String = "nicht vorhanden",
    val deviceToken: String = "nicht vorhanden",
    val appSetId: String = "nicht vorhanden",
    val ssaid: String = "nicht vorhanden"
)

@Singleton
class IdExtractor @Inject constructor() {
    
    /**
     * Extract IDs from playerprefs XML file
     */
    fun extractIdsFromPlayerPrefs(xmlFile: File): Result<ExtractedIds> {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xmlFile)
            
            val userId = extractStringValue(doc, "Scopely.Attribution.UserId")
            val gaid = extractStringValue(doc, "GoogleAdId") ?: "nicht vorhanden"
            val deviceToken = extractStringValue(doc, "LastOpenedDeviceToken") ?: "nicht vorhanden"
            val appSetId = extractStringValue(doc, "AppSetId") ?: "nicht vorhanden"
            
            if (userId == null) {
                Result.failure(Exception("User ID nicht gefunden (MANDATORY)"))
            } else {
                Result.success(ExtractedIds(userId, gaid, deviceToken, appSetId))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Extract SSAID from settings_ssaid.xml using regex
     */
    fun extractSsaid(xmlFile: File): String {
        return try {
            val content = xmlFile.readText()
            val regex = Regex("""com\.scopely\.monopolygo[^/]*/[^/]*/[^/]*/([0-9a-f]{16})""")
            val match = regex.find(content)
            match?.groupValues?.get(1) ?: "nicht vorhanden"
        } catch (e: Exception) {
            "nicht vorhanden"
        }
    }
    
    private fun extractStringValue(doc: org.w3c.dom.Document, name: String): String? {
        val nodeList = doc.getElementsByTagName("string")
        for (i in 0 until nodeList.length) {
            val element = nodeList.item(i) as Element
            if (element.getAttribute("name") == name) {
                return element.textContent
            }
        }
        return null
    }
}
```

### 2. File Permission Manager

**Datei:** `domain/util/FilePermissionManager.kt`

```kotlin
package com.mgomanager.app.domain.util

import javax.inject.Inject
import javax.inject.Singleton

data class FilePermissions(
    val owner: String,
    val group: String,
    val permissions: String
)

@Singleton
class FilePermissionManager @Inject constructor(
    private val rootUtil: RootUtil
) {
    
    /**
     * Read file ownership and permissions
     */
    suspend fun getFilePermissions(path: String): Result<FilePermissions> {
        val result = rootUtil.executeCommand("stat -c '%U:%G %a' $path")
        
        return result.mapCatching { output ->
            // Expected format: "u0_a123:u0_a123 755"
            val parts = output.trim().split(" ")
            if (parts.size != 2) {
                throw Exception("Unexpected stat output: $output")
            }
            
            val ownerGroup = parts[0].split(":")
            if (ownerGroup.size != 2) {
                throw Exception("Unexpected owner:group format: ${parts[0]}")
            }
            
            FilePermissions(
                owner = ownerGroup[0],
                group = ownerGroup[1],
                permissions = parts[1]
            )
        }
    }
    
    /**
     * Set file ownership
     */
    suspend fun setFileOwnership(path: String, owner: String, group: String): Result<Unit> {
        return rootUtil.executeCommand("chown -R $owner:$group $path").map { }
    }
    
    /**
     * Set file permissions
     */
    suspend fun setFilePermissions(path: String, permissions: String): Result<Unit> {
        return rootUtil.executeCommand("chmod -R $permissions $path").map { }
    }
}
```

### 3. Backup Use Case

**Datei:** `domain/usecase/CreateBackupUseCase.kt`

```kotlin
package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FilePermissionManager
import com.mgomanager.app.domain.util.IdExtractor
import com.mgomanager.app.domain.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class BackupRequest(
    val accountName: String,
    val prefix: String,
    val backupRootPath: String,
    val hasFacebookLink: Boolean,
    val fbUsername: String? = null,
    val fbPassword: String? = null,
    val fb2FA: String? = null,
    val fbTempMail: String? = null
)

class CreateBackupUseCase @Inject constructor(
    private val rootUtil: RootUtil,
    private val idExtractor: IdExtractor,
    private val permissionManager: FilePermissionManager,
    private val accountRepository: AccountRepository,
    private val logRepository: LogRepository
) {
    
    companion object {
        const val MGO_DATA_PATH = "/data/data/com.scopely.monopolygo"
        const val MGO_FILES_PATH = "$MGO_DATA_PATH/files/DiskBasedCacheDirectory"
        const val MGO_PREFS_PATH = "$MGO_DATA_PATH/shared_prefs"
        const val SSAID_PATH = "/data/system/users/0/settings_ssaid.xml"
        const val PLAYER_PREFS_FILE = "com.scopely.monopolygo.v2.playerprefs.xml"
    }
    
    suspend fun execute(request: BackupRequest): BackupResult = withContext(Dispatchers.IO) {
        try {
            logRepository.logInfo("BACKUP", "Starte Backup für ${request.accountName}")
            
            // Step 1: Force stop Monopoly Go
            rootUtil.forceStopMonopolyGo().getOrThrow()
            logRepository.logInfo("BACKUP", "Monopoly Go gestoppt", request.accountName)
            
            // Step 2: Read file permissions
            val permissions = permissionManager.getFilePermissions(MGO_FILES_PATH).getOrElse {
                logRepository.logError("BACKUP", "Fehler beim Lesen der Berechtigungen", request.accountName, it as? Exception)
                throw it
            }
            
            // Step 3: Create backup directory
            val backupPath = "${request.backupRootPath}${request.prefix}${request.accountName}/"
            val backupDir = File(backupPath)
            
            val createDirResult = rootUtil.executeCommand("mkdir -p $backupPath")
            if (createDirResult.isFailure) {
                throw Exception("Backup-Verzeichnis konnte nicht erstellt werden")
            }
            logRepository.logInfo("BACKUP", "Backup-Verzeichnis erstellt: $backupPath", request.accountName)
            
            // Step 4: Copy directories
            copyDirectory(MGO_FILES_PATH, "$backupPath/DiskBasedCacheDirectory/", request.accountName)
            copyDirectory(MGO_PREFS_PATH, "$backupPath/shared_prefs/", request.accountName)
            
            // Step 5: Copy SSAID file
            val copyResult = rootUtil.executeCommand("cp $SSAID_PATH $backupPath/settings_ssaid.xml")
            if (copyResult.isFailure) {
                logRepository.logWarning("BACKUP", "SSAID-Datei konnte nicht kopiert werden", request.accountName)
            }
            
            // Step 6: Extract IDs
            val playerPrefsFile = File("$backupPath/shared_prefs/$PLAYER_PREFS_FILE")
            val extractedIds = idExtractor.extractIdsFromPlayerPrefs(playerPrefsFile).getOrElse {
                logRepository.logError("BACKUP", "ID-Extraktion fehlgeschlagen", request.accountName, it as? Exception)
                throw Exception("User ID konnte nicht extrahiert werden (MANDATORY)")
            }
            
            // Step 7: Extract SSAID
            val ssaidFile = File("$backupPath/settings_ssaid.xml")
            val ssaid = if (ssaidFile.exists()) {
                idExtractor.extractSsaid(ssaidFile)
            } else {
                "nicht vorhanden"
            }
            
            // Step 8: Create Account object
            val now = System.currentTimeMillis()
            val account = Account(
                accountName = request.accountName,
                prefix = request.prefix,
                createdAt = now,
                lastPlayedAt = now,
                userId = extractedIds.userId,
                gaid = extractedIds.gaid,
                deviceToken = extractedIds.deviceToken,
                appSetId = extractedIds.appSetId,
                ssaid = ssaid,
                hasFacebookLink = request.hasFacebookLink,
                fbUsername = request.fbUsername,
                fbPassword = request.fbPassword,
                fb2FA = request.fb2FA,
                fbTempMail = request.fbTempMail,
                backupPath = backupPath,
                fileOwner = permissions.owner,
                fileGroup = permissions.group,
                filePermissions = permissions.permissions
            )
            
            // Step 9: Save to database
            accountRepository.insertAccount(account)
            
            logRepository.logInfo(
                "BACKUP",
                "Backup erfolgreich abgeschlossen für ${request.accountName}",
                request.accountName
            )
            
            // Check if any IDs are missing
            val missingIds = mutableListOf<String>()
            if (extractedIds.gaid == "nicht vorhanden") missingIds.add("GAID")
            if (extractedIds.deviceToken == "nicht vorhanden") missingIds.add("Device Token")
            if (extractedIds.appSetId == "nicht vorhanden") missingIds.add("App Set ID")
            if (ssaid == "nicht vorhanden") missingIds.add("SSAID")
            
            if (missingIds.isNotEmpty()) {
                BackupResult.PartialSuccess(account, missingIds)
            } else {
                BackupResult.Success(account)
            }
            
        } catch (e: Exception) {
            logRepository.logError("BACKUP", "Backup fehlgeschlagen: ${e.message}", request.accountName, e)
            BackupResult.Failure("Backup fehlgeschlagen: ${e.message}", e)
        }
    }
    
    private suspend fun copyDirectory(source: String, destination: String, accountName: String) {
        val result = rootUtil.executeCommand("cp -r $source $destination")
        if (result.isSuccess) {
            logRepository.logInfo("BACKUP", "Verzeichnis kopiert: $source -> $destination", accountName)
        } else {
            logRepository.logError("BACKUP", "Fehler beim Kopieren: $source", accountName)
            throw Exception("Verzeichnis konnte nicht kopiert werden: $source")
        }
    }
}
```

### 4. Backup Repository

**Datei:** `data/repository/BackupRepository.kt`

```kotlin
package com.mgomanager.app.data.repository

import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.domain.usecase.BackupRequest
import com.mgomanager.app.domain.usecase.CreateBackupUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val createBackupUseCase: CreateBackupUseCase
) {
    
    suspend fun createBackup(request: BackupRequest): BackupResult {
        return createBackupUseCase.execute(request)
    }
}
```

### 5. Update Hilt Modules

**Datei:** `di/RepositoryModule.kt` (Update - add BackupRepository)

```kotlin
@Provides
@Singleton
fun provideBackupRepository(
    createBackupUseCase: CreateBackupUseCase
): BackupRepository {
    return BackupRepository(createBackupUseCase)
}
```

---

## Testing

### Manual Tests

**Test 1: Backup-Verzeichnis Creation**
```kotlin
// In MainActivity oder Test-Activity
scope.launch {
    val result = rootUtil.executeCommand("mkdir -p /storage/emulated/0/mgo/backups/test/")
    println("Create dir result: $result")
}
```

**Test 2: File Copy with Root**
```kotlin
scope.launch {
    val result = rootUtil.executeCommand(
        "cp -r /data/data/com.scopely.monopolygo/shared_prefs /storage/emulated/0/test_prefs/"
    )
    println("Copy result: $result")
}
```

**Test 3: ID Extraction**
```kotlin
val testFile = File("/storage/emulated/0/test_prefs/com.scopely.monopolygo.v2.playerprefs.xml")
val result = idExtractor.extractIdsFromPlayerPrefs(testFile)
println("Extracted IDs: $result")
```

**Test 4: Permission Reading**
```kotlin
scope.launch {
    val perms = permissionManager.getFilePermissions("/data/data/com.scopely.monopolygo/files")
    println("Permissions: $perms")
}
```

**Test 5: Complete Backup Flow**
```kotlin
scope.launch {
    val request = BackupRequest(
        accountName = "Test_01",
        prefix = "MGO_",
        backupRootPath = "/storage/emulated/0/mgo/backups/",
        hasFacebookLink = false
    )
    val result = backupRepository.createBackup(request)
    println("Backup result: $result")
}
```

### Checklist
- [ ] Backup-Verzeichnis wird erstellt
- [ ] Monopoly Go wird gestoppt
- [ ] Dateien werden kopiert (DiskBasedCacheDirectory + shared_prefs)
- [ ] SSAID-Datei wird kopiert
- [ ] User ID wird extrahiert (MANDATORY)
- [ ] GAID, Device Token, App Set ID werden extrahiert (optional)
- [ ] SSAID wird per Regex extrahiert
- [ ] File-Permissions werden gelesen
- [ ] Account wird in Datenbank gespeichert
- [ ] Logs werden geschrieben
- [ ] Bei Fehler: BackupResult.Failure
- [ ] Bei fehlenden IDs: BackupResult.PartialSuccess

---

## Troubleshooting

### Problem: "Permission denied" beim Kopieren
**Lösung:** Prüfe Root-Zugriff, verwende `su -c` prefix für alle Commands

### Problem: User ID nicht gefunden
**Lösung:** Prüfe XML-Struktur der playerprefs.xml, stelle sicher dass Monopoly Go mindestens einmal gestartet wurde

### Problem: SSAID Regex findet nichts
**Lösung:** Prüfe settings_ssaid.xml Inhalt, Regex-Pattern anpassen falls nötig

### Problem: Backup-Verzeichnis nicht beschreibbar
**Lösung:** Prüfe Storage-Permissions, verwende Root für mkdir

---

## Abschlusskriterien

- [ ] Kompletter Backup-Flow funktioniert
- [ ] IDs werden korrekt extrahiert
- [ ] Dateien werden vollständig kopiert
- [ ] Permissions werden gespeichert
- [ ] Account wird in DB gespeichert
- [ ] Logs werden geschrieben
- [ ] Error-Handling funktioniert

**Wenn alle Aufgaben abgeschlossen wurden und alle Tests erfolgreich bestanden wurden, lese `Initial_Entwicklungsprompt_4.md` und setze die Aufgaben um.**

---

*Entwicklungsprompt 3/7 - Erstellt für MGO Manager Projekt*
