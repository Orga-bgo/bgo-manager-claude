# MGO Manager - Comprehensive Project Specification

## Project Overview

**Project Name:** MGO Manager  
**Type:** Android Root-Based Backup Management Tool  
**Target Game:** Monopoly Go (com.scopely.monopolygo)  
**Primary Function:** Create and restore complete game account backups with metadata tracking  
**Android Compatibility:** Android 9, 12, 15+ (Root required)

---

## Core Functionality

### 1. Backup System
The app creates comprehensive backups of Monopoly Go accounts by:
- Copying game data directories with preserved permissions
- Extracting device and account identifiers
- Storing metadata in local database
- Managing Facebook login credentials (optional)

### 2. Restore System
The app restores backups by:
- Force-stopping Monopoly Go
- Copying backed-up files back to original locations
- Restoring correct file ownership and permissions
- Optionally launching the game after restore
- Updating "last played" timestamp

### 3. Account Management
- Visual overview of all backed-up accounts
- Detailed view with all metadata
- Manual tagging system (Sus levels, Error flags)
- Edit and delete functionality

---

## Technical Architecture

### Technology Stack
- **Language:** Kotlin
- **Architecture:** MVVM (Model-View-ViewModel)
- **Dependency Injection:** Hilt
- **Database:** Room Database
- **UI Framework:** Jetpack Compose with Material3
- **Root Access:** libsu (TopJohnWu)
- **Data Storage:** Room + DataStore (for app settings)

### Project Structure
```
com.mgomanager.app/
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── AppDatabase.kt
│   │   │   ├── entities/
│   │   │   │   ├── AccountEntity.kt
│   │   │   │   └── LogEntity.kt
│   │   │   └── dao/
│   │   │       ├── AccountDao.kt
│   │   │       └── LogDao.kt
│   │   └── preferences/
│   │       └── SettingsDataStore.kt
│   ├── repository/
│   │   ├── AccountRepository.kt
│   │   ├── BackupRepository.kt
│   │   └── LogRepository.kt
│   └── model/
│       ├── Account.kt
│       ├── BackupResult.kt
│       └── SusLevel.kt
├── domain/
│   ├── usecase/
│   │   ├── CreateBackupUseCase.kt
│   │   ├── RestoreBackupUseCase.kt
│   │   └── ValidateBackupUseCase.kt
│   └── util/
│       ├── RootUtil.kt
│       ├── IdExtractor.kt
│       └── PermissionManager.kt
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── screens/
│   │   ├── home/
│   │   │   ├── HomeScreen.kt
│   │   │   └── HomeViewModel.kt
│   │   ├── detail/
│   │   │   ├── DetailScreen.kt
│   │   │   └── DetailViewModel.kt
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   └── SettingsViewModel.kt
│   │   └── logs/
│   │       ├── LogScreen.kt
│   │       └── LogViewModel.kt
│   ├── components/
│   │   ├── AccountCard.kt
│   │   ├── StatisticsCard.kt
│   │   ├── BackupDialog.kt
│   │   └── ConfirmationDialog.kt
│   └── navigation/
│       └── NavGraph.kt
└── di/
    ├── AppModule.kt
    ├── DatabaseModule.kt
    └── RepositoryModule.kt
```

---

## Data Model

### Account Entity
```kotlin
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // User-defined
    val accountName: String,
    val prefix: String = "", // From settings
    
    // Timestamps
    val createdAt: Long, // Unix timestamp
    val lastPlayedAt: Long, // Unix timestamp
    
    // Extracted IDs
    val userId: String, // MANDATORY
    val gaid: String? = "nicht vorhanden",
    val deviceToken: String? = "nicht vorhanden",
    val appSetId: String? = "nicht vorhanden",
    val ssaid: String? = "nicht vorhanden",
    
    // Manual flags
    val susLevel: Int = 0, // 0, 3, 7, or special value for "perm"
    val hasError: Boolean = false,
    
    // Facebook data (optional)
    val hasFacebookLink: Boolean = false,
    val fbUsername: String? = null,
    val fbPassword: String? = null,
    val fb2FA: String? = null,
    val fbTempMail: String? = null,
    
    // File system metadata
    val backupPath: String,
    val fileOwner: String, // e.g., "u0_a123"
    val fileGroup: String, // e.g., "u0_a123"
    val filePermissions: String // e.g., "755"
)
```

### Log Entity
```kotlin
@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String, // UUID for app start
    val timestamp: Long,
    val level: LogLevel, // INFO, WARNING, ERROR
    val operation: String, // "BACKUP", "RESTORE", "ERROR"
    val accountName: String?,
    val message: String,
    val stackTrace: String? = null
)

enum class LogLevel {
    INFO, WARNING, ERROR
}
```

### Sus Level Enum
```kotlin
enum class SusLevel(val value: Int, val displayName: String, val color: Color) {
    NONE(0, "Keine", Color.Green),
    LEVEL_3(3, "Level 3", Color(0xFFFF9800)), // Orange
    LEVEL_7(7, "Level 7", Color(0xFFFFB74D)), // Light Orange
    PERMANENT(99, "Permanent", Color.Red)
}
```

---

## File System Operations

### Important Paths

#### Source Paths (Monopoly Go Data)
```
/data/data/com.scopely.monopolygo/
├── files/DiskBasedCacheDirectory/    [BACKUP: Full directory]
└── shared_prefs/                      [BACKUP: Full directory]

/data/system/users/0/settings_ssaid.xml [BACKUP: Single file]
```

#### Backup Destination
```
{BACKUP_ROOT}/{PREFIX}{ACCOUNT_NAME}/
├── DiskBasedCacheDirectory/
├── shared_prefs/
│   └── com.scopely.monopolygo.v2.playerprefs.xml  [ID extraction source]
└── settings_ssaid.xml
```

**Default Backup Root:** `/storage/emulated/0/mgo/backups/`

### ID Extraction

#### From: `shared_prefs/com.scopely.monopolygo.v2.playerprefs.xml`

```xml
<!-- User ID (MANDATORY) -->
<string name="Scopely.Attribution.UserId">1140407373</string>

<!-- GAID -->
<string name="GoogleAdId">1bbae05f-b61e-47f0-b01f-d31601cd2a3c</string>

<!-- Device Token -->
<string name="LastOpenedDeviceToken">198aab99-d769-4dc2-96f4-2fcf0f33fec4</string>

<!-- App Set ID -->
<string name="AppSetId">cb52ea67-ba06-dd6d-598a-441f19252c12</string>
```

**Extraction Logic:**
- Parse XML and find `<string name="KEY">VALUE</string>`
- If not found or parsing fails: Set value to `"nicht vorhanden"`
- User ID must always be present (validation requirement)

#### From: `settings_ssaid.xml`

**Regex Pattern:**
```regex
com\.scopely\.monopolygo[^/]*/[^/]*/[^/]*/([0-9a-f]{16})
```

**Example:**
```xml
<setting id="..." name="com.scopely.monopolygo" value="123abc..." 
         package="com.scopely.monopolygo" defaultValue="..." 
         defaultSysSet="..." tag="..." />
```
Extract the 16-character hex value.

### Permission Management

**During Backup:**
1. Read and store current ownership and permissions:
   ```bash
   stat -c '%U:%G %a' /data/data/com.scopely.monopolygo/files
   ```
   Example output: `u0_a123:u0_a123 755`

2. Store in database (fileOwner, fileGroup, filePermissions)

**During Restore:**
1. Copy files back using root privileges
2. Restore ownership:
   ```bash
   chown -R u0_a123:u0_a123 /data/data/com.scopely.monopolygo/files
   chown -R u0_a123:u0_a123 /data/data/com.scopely.monopolygo/shared_prefs
   ```
3. Restore permissions:
   ```bash
   chmod -R 755 /data/data/com.scopely.monopolygo/files
   chmod -R 755 /data/data/com.scopely.monopolygo/shared_prefs
   ```

---

## User Interface Specification

### Theme & Colors

**Primary Color:** Purple (from screenshot)
- Primary: `#6200EE` or similar purple
- Secondary: `#03DAC5`
- Background: `#FFFFFF` (Light) / `#121212` (Dark)
- Error: `#B00020`

**Status Colors:**
- Success/Green (Sus=0): `#4CAF50`
- Warning/Orange (Sus=3): `#FF9800`
- Warning/Light Orange (Sus=7): `#FFB74D`
- Error/Red (Sus=perm): `#F44336`

### Screen Layouts

#### 1. Home Screen (Main Overview)

**Top Section - Statistics Cards (Row of 3)**
```
┌─────────────────────────────────────────────┐
│  GESAMT          ERROR           SUS        │
│    24              2              1         │
└─────────────────────────────────────────────┘
```

**Account List - LazyVerticalGrid (2 columns)**
```
┌──────────────┬──────────────┐
│ MGO_Main_01  │ MGO_Alt_05   │
│ ID: ...7373  │ [ERROR]      │
│ ⏱ 24.01.14:30│ Path error   │
│ 💾 23.01.10:00│              │
│ [RESTORE]    │ [RESTORE]    │
└──────────────┴──────────────┘
```

**Card Colors:**
- Left border: Green (no sus/error), Orange (sus 3/7), Red (sus perm or error)
- Background: White/Dark mode appropriate

**Floating Action Button:** 
- Position: Bottom-right
- Icon: Plus icon
- Action: Open "Create Backup" dialog

#### 2. Account Detail Screen

**Layout:**
```
┌─────────────────────────────────────┐
│ ← MGO_Main_01                    ⚙️ │
├─────────────────────────────────────┤
│ Allgemeine Informationen            │
│ • Name: MGO_Main_01                 │
│ • Erstellt am: 24.01.2025 14:30    │
│ • Zuletzt gespielt: 23.01.2025 10:00│
│                                     │
│ IDs                                 │
│ • User ID: 1140407373              │
│ • GAID: 1bbae05f-b61e-47f0...      │
│ • Device Token: 198aab99-d769...   │
│ • App Set ID: cb52ea67-ba06...     │
│ • SSAID: a1b2c3d4e5f67890          │
│                                     │
│ Status                              │
│ • Sus Level: [Dropdown: 0/3/7/perm]│
│ • Error: [Checkbox]                 │
│                                     │
│ Facebook Verbindung                 │
│ • Verknüpft: Ja                     │
│ • Username: user@email.com          │
│ • Passwort: ••••••••               │
│ • 2FA: 123456                       │
│ • Temp-Mail: temp@mail.com          │
│                                     │
│ Dateisystem                         │
│ • Backup-Pfad: /storage/...         │
│ • Eigentümer: u0_a123:u0_a123      │
│ • Berechtigungen: 755               │
├─────────────────────────────────────┤
│ [RESTORE] [BEARBEITEN] [LÖSCHEN]   │
└─────────────────────────────────────┘
```

#### 3. Backup Creation Dialog

**Dialog Workflow:**
```
Step 1: Basic Information
┌────────────────────────────┐
│ Neues Backup erstellen     │
├────────────────────────────┤
│ Accountname:               │
│ [Textfeld]                 │
│                            │
│ Facebook-Verbindung:       │
│ ○ Ja  ● Nein              │
│                            │
│ [ABBRECHEN]  [WEITER]     │
└────────────────────────────┘

Step 2 (if FB = Yes): Facebook Details
┌────────────────────────────┐
│ Facebook-Details           │
├────────────────────────────┤
│ Nutzername:                │
│ [Textfeld]                 │
│                            │
│ Passwort:                  │
│ [Passwort-Feld]           │
│                            │
│ 2FA-Code:                  │
│ [Textfeld]                 │
│                            │
│ Temp-Mail:                 │
│ [Textfeld]                 │
│                            │
│ [ZURÜCK]  [BACKUP STARTEN]│
└────────────────────────────┘

Step 3: Backup Progress
┌────────────────────────────┐
│ Backup wird erstellt...    │
├────────────────────────────┤
│ ✓ Monopoly Go gestoppt     │
│ ⟳ Dateien werden kopiert   │
│ ○ IDs werden extrahiert    │
│ ○ Berechtigungen gespeichert│
│                            │
│ [Fortschrittsbalken]       │
└────────────────────────────┘

Step 4: Backup Complete
┌────────────────────────────┐
│ Backup erfolgreich!        │
├────────────────────────────┤
│ ✓ Dateien kopiert          │
│ ✓ IDs extrahiert           │
│   • User ID: 1140407373    │
│   • GAID: 1bbae05f...      │
│   • Device Token: 198aab...│
│   • App Set ID: cb52ea67...│
│   • SSAID: a1b2c3d4...     │
│ ✓ Berechtigungen: u0_a123  │
│                            │
│ [FERTIG]                   │
└────────────────────────────┘
```

#### 4. Settings Screen

**Layout:**
```
┌─────────────────────────────────────┐
│ ← Einstellungen                     │
├─────────────────────────────────────┤
│ Backup-Konfiguration                │
│                                     │
│ Accountname-Präfix                  │
│ [MGO_                        ]      │
│                                     │
│ Backup-Pfad                         │
│ [/storage/emulated/0/mgo/backups/] │
│                                     │
├─────────────────────────────────────┤
│ System                              │
│                                     │
│ Logs anzeigen                →      │
│                                     │
│ Root-Status                         │
│ ✓ Root-Zugriff verfügbar            │
│                                     │
├─────────────────────────────────────┤
│ Über                                │
│ Version 1.0.0                       │
└─────────────────────────────────────┘
```

#### 5. Log Screen

**Display last 5 app sessions:**
```
┌─────────────────────────────────────┐
│ ← Logs                              │
├─────────────────────────────────────┤
│ Session: 24.01.2025 14:30          │
│ ├─ 14:30:15 [INFO] App gestartet   │
│ ├─ 14:31:02 [INFO] Backup: MGO_M...│
│ └─ 14:31:45 [INFO] Backup erfolg...│
│                                     │
│ Session: 24.01.2025 10:15          │
│ ├─ 10:15:03 [INFO] App gestartet   │
│ ├─ 10:16:20 [INFO] Restore: MGO_A..│
│ ├─ 10:16:55 [ERROR] Datei fehlt...│
│ └─ 10:17:10 [INFO] Restore abgebr..│
│                                     │
│ Session: 23.01.2025 18:45          │
│ ...                                 │
└─────────────────────────────────────┘
```

---

## Business Logic

### Backup Workflow

```
1. User clicks FAB (+) button
2. Show backup dialog (Step 1)
3. User enters account name
4. User selects FB connection (yes/no)
5. If FB = yes: Show Step 2 dialog for FB details
6. User clicks "Backup starten"
7. Show progress dialog (Step 3)
8. EXECUTE:
   a. Force-stop Monopoly Go
      → su -c "am force-stop com.scopely.monopolygo"
   
   b. Read file ownership/permissions
      → su -c "stat -c '%U:%G %a' /data/data/com.scopely.monopolygo/files"
   
   c. Create backup directory
      → {BACKUP_ROOT}/{PREFIX}{ACCOUNT_NAME}/
   
   d. Copy directories (with root)
      → DiskBasedCacheDirectory/
      → shared_prefs/
   
   e. Copy SSAID file
      → /data/system/users/0/settings_ssaid.xml
   
   f. Extract IDs from playerprefs.xml
      → User ID (mandatory)
      → GAID, Device Token, App Set ID (optional)
   
   g. Extract SSAID using regex
   
   h. Save to database:
      → AccountEntity with all data
      → createdAt = now()
      → lastPlayedAt = now()
      → susLevel = 0
      → hasError = false
   
   i. Log operation
      → LogEntity(operation="BACKUP", level=INFO, ...)
9. Show completion dialog (Step 4) with extracted IDs
10. Return to home screen (refresh list)
```

### Restore Workflow

```
1. User clicks "RESTORE" button on account card or detail screen
2. Show confirmation dialog
   "Account '{NAME}' wiederherstellen?"
3. User confirms
4. Show progress dialog
5. EXECUTE:
   a. Force-stop Monopoly Go
      → su -c "am force-stop com.scopely.monopolygo"
   
   b. Validate backup files exist
      → Check if all required directories/files are present
      → If missing: Show error, abort
   
   c. Copy directories back (with root)
      → {BACKUP_PATH}/DiskBasedCacheDirectory/ 
         → /data/data/com.scopely.monopolygo/files/DiskBasedCacheDirectory/
      → {BACKUP_PATH}/shared_prefs/
         → /data/data/com.scopely.monopolygo/shared_prefs/
   
   d. Copy SSAID file back
      → {BACKUP_PATH}/settings_ssaid.xml
         → /data/system/users/0/settings_ssaid.xml
   
   e. Restore ownership (from database)
      → su -c "chown -R {fileOwner}:{fileGroup} /data/data/com.scopely.monopolygo/files"
      → su -c "chown -R {fileOwner}:{fileGroup} /data/data/com.scopely.monopolygo/shared_prefs"
   
   f. Restore permissions (from database)
      → su -c "chmod -R {filePermissions} /data/data/com.scopely.monopolygo/files"
      → su -c "chmod -R {filePermissions} /data/data/com.scopely.monopolygo/shared_prefs"
   
   g. Update lastPlayedAt timestamp in database
      → lastPlayedAt = now()
   
   h. Log operation
      → LogEntity(operation="RESTORE", level=INFO, ...)
6. Show completion dialog with option to launch game
   "Monopoly Go starten?"
   [NEIN] [JA]
7. If JA:
   → su -c "am start -n com.scopely.monopolygo/.MainActivity"
8. Return to home screen (refresh list)
```

### Error Handling

**Common Errors:**
- No root access → Show alert on app start
- Monopoly Go not installed → Disable backup/restore
- Backup path not writable → Show error in settings
- Required ID (User ID) not found → Mark backup with hasError=true
- File copy failure → Log error, show user-friendly message
- Insufficient storage → Check before backup, show warning

**Error Display:**
- All errors logged to database
- Critical errors shown as dialog
- Non-critical errors shown as Snackbar
- Error flag on account card (red border)

---

## Settings & Preferences

### App Settings (DataStore)
```kotlin
data class AppSettings(
    val accountPrefix: String = "MGO_",
    val backupRootPath: String = "/storage/emulated/0/mgo/backups/",
    val currentSessionId: String = UUID.randomUUID().toString(),
    val appStartCount: Int = 0
)
```

### Settings Management
- Prefix: Editable text field
- Backup path: Editable text field with directory picker
- Validate path writability on change
- Save to DataStore immediately

---

## Logging System

### Log Requirements
1. **Every app start:** Create new session ID
2. **Log all:**
   - Backup operations (start, success, failure)
   - Restore operations (start, success, failure)
   - All errors (with stack trace)
   - Critical operations (force-stop, file operations)

### Session Management
- Session = One app lifecycle (launch to close)
- Session ID = UUID
- Keep last 5 sessions in database
- Older sessions auto-deleted

### Log Display
- Group by session
- Show session start time
- Expandable/collapsible per session
- Color-coded by level (INFO=grey, WARNING=orange, ERROR=red)

---

## Testing Requirements

### Unit Tests
- ID extraction logic
- Regex pattern for SSAID
- SusLevel enum conversions
- Data validation

### Integration Tests
- Database operations (CRUD)
- Settings persistence
- Root command execution (mocked)

### UI Tests
- Navigation flow
- Dialog interactions
- List scrolling and filtering

### Manual Testing Checklist
**Per Android Version (9, 12, 15):**
- [ ] Root access granted
- [ ] Backup creates all files
- [ ] IDs extracted correctly
- [ ] Restore works completely
- [ ] Permissions preserved
- [ ] Game launches after restore
- [ ] Statistics update correctly
- [ ] Logs persist across sessions

---

## Development Phases

### Phase 1: Project Setup & Base Architecture
- Gradle configuration
- Dependencies (Hilt, Room, Compose, libsu)
- Root permission check
- Basic navigation structure

### Phase 2: Data Model & Database
- Room entities (Account, Log)
- DAOs and Repository pattern
- DataStore for settings
- Database migrations

### Phase 3: Backup Logic Implementation
- File system operations with root
- ID extraction from XML
- SSAID regex extraction
- Permission reading
- Backup directory management

### Phase 4: Restore Logic Implementation
- File copy with root
- Permission restoration
- Game process management (force-stop, launch)
- Backup validation

### Phase 5: UI - Overview & Navigation
- Home screen with statistics
- Account grid layout
- FAB and dialogs
- Navigation graph

### Phase 6: UI - Detail & Editing
- Account detail screen
- Edit functionality
- Delete with confirmation
- Facebook data display

### Phase 7: Settings & Logging
- Settings screen with DataStore
- Log display grouped by session
- Log management (keep last 5)
- About screen

---

## Security Considerations

### Root Access
- Check root on app start
- Request root permission once
- Handle root denial gracefully
- Use libsu for all root operations (secure shell)

### Data Storage
- Facebook credentials stored in plain text (acceptable per client requirement)
- Database not encrypted (local-only app)
- No network communication
- No external service integration

### File Operations
- Always validate paths before operations
- Never delete original game data without backup
- Confirm destructive operations (delete, overwrite)
- Log all critical file operations

---

## Performance Considerations

### Database
- Index on accountName for fast search
- Lazy loading for logs
- Auto-delete old sessions (keep last 5)

### UI
- LazyVerticalGrid for account list (efficient scrolling)
- Coil for image loading (if needed for future features)
- Remember scroll position on navigation

### File Operations
- Use coroutines for all I/O operations
- Show progress for long operations (large backups)
- Cancel support for backup/restore

---

## Future Enhancement Ideas
(Not included in current scope)

- Cloud backup sync
- Backup encryption
- Backup versioning (multiple backups per account)
- Import/export backup database
- Backup scheduling
- Comparison view (compare two accounts)
- Search and filter functionality
- Backup integrity check (MD5/SHA)
- Batch operations (restore multiple accounts)

---

## Glossary

- **SSAID:** Settings Secure Android ID - unique device identifier
- **GAID:** Google Advertising ID
- **Sus:** Suspicious account level indicator (0, 3, 7, permanent)
- **Error Flag:** Manual marker for problematic accounts
- **Prefix:** User-defined text prepended to account names
- **Session:** Single app lifecycle from launch to close
- **Force-stop:** Kill app process completely
- **Root Shell:** Privileged command execution environment

---

## Contact & Support

**Developer:** babix (KI-Agent-Prompt-Entwickler)  
**Project Type:** Personal tool for Monopoly Go account management  
**Development Approach:** Phase-based with structured prompts

---

*Last Updated: January 2025*  
*Document Version: 1.0*
