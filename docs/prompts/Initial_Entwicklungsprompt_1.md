# Initial Development Prompt 1: Project Setup & Base Architecture

## Übersicht
In dieser Phase wird die grundlegende Projektstruktur erstellt, alle notwendigen Dependencies konfiguriert, Root-Zugriff implementiert und die Basisarchitektur mit Hilt Dependency Injection aufgesetzt.

## Voraussetzungen
- Android Studio (neueste stabile Version)
- Kotlin Plugin
- Grundverständnis von MVVM-Architektur
- Zugriff auf CLAUDE.md Spezifikation

---

## Aufgaben

### 1. Projekt-Initialisierung

#### 1.1 Neues Android Projekt erstellen
- **Template:** Empty Activity (Compose)
- **Package Name:** `com.mgomanager.app`
- **Language:** Kotlin
- **Minimum SDK:** API 28 (Android 9)
- **Build Configuration:** Kotlin DSL (build.gradle.kts)

#### 1.2 Projekt-Struktur anlegen
Erstelle folgende Verzeichnisstruktur unter `app/src/main/java/com/mgomanager/app/`:

```
com.mgomanager.app/
├── data/
│   ├── local/
│   │   ├── database/
│   │   ├── preferences/
│   ├── repository/
│   └── model/
├── domain/
│   ├── usecase/
│   └── util/
├── ui/
│   ├── theme/
│   ├── screens/
│   ├── components/
│   └── navigation/
├── di/
└── MainActivity.kt
```

### 2. Gradle-Konfiguration

#### 2.1 Project-Level build.gradle.kts
Füge folgende Plugins hinzu:

```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
}
```

#### 2.2 App-Level build.gradle.kts

**Plugins:**
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}
```

**Android Configuration:**
```kotlin
android {
    namespace = "com.mgomanager.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mgomanager.app"
        minSdk = 28  // Android 9
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
```

**Dependencies:**
```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Root Access (libsu)
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    implementation("com.github.topjohnwu.libsu:io:5.2.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

**libsu Repository hinzufügen (settings.gradle.kts):**
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 3. Android Manifest Konfiguration

#### 3.1 AndroidManifest.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Root access will be requested at runtime -->
    <!-- Storage permissions for backup directory access -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="32"
        tools:ignore="ScopedStorage" />
    
    <!-- For Android 13+ -->
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    
    <!-- Manage external storage (Android 11+) -->
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />
    
    <!-- Query installed apps -->
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />

    <application
        android:name=".MGOApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MGOManager"
        tools:targetApi="31">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.MGOManager">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### 4. Hilt Setup

#### 4.1 Application Class erstellen
**Datei:** `MGOApplication.kt`

```kotlin
package com.mgomanager.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MGOApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging (optional but helpful)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // libsu configuration
        com.topjohnwu.superuser.Shell.enableVerboseLogging = BuildConfig.DEBUG
        com.topjohnwu.superuser.Shell.setDefaultBuilder(
            com.topjohnwu.superuser.Shell.Builder.create()
                .setFlags(com.topjohnwu.superuser.Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
}
```

#### 4.2 App Module erstellen
**Datei:** `di/AppModule.kt`

```kotlin
package com.mgomanager.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }
    
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

### 5. Root Access Utility

#### 5.1 Root Manager erstellen
**Datei:** `domain/util/RootUtil.kt`

```kotlin
package com.mgomanager.app.domain.util

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootUtil @Inject constructor() {
    
    /**
     * Check if device has root access
     */
    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.getShell().isRoot
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Request root access if not already granted
     */
    suspend fun requestRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            shell.isRoot
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Execute a single root command
     */
    suspend fun executeCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd(command).exec()
            if (result.isSuccess) {
                Result.success(result.out.joinToString("\n"))
            } else {
                Result.failure(Exception("Command failed: ${result.err.joinToString("\n")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Execute multiple root commands
     */
    suspend fun executeCommands(commands: List<String>): Result<List<String>> = 
        withContext(Dispatchers.IO) {
            try {
                val result = Shell.cmd(*commands.toTypedArray()).exec()
                if (result.isSuccess) {
                    Result.success(result.out)
                } else {
                    Result.failure(Exception("Commands failed: ${result.err.joinToString("\n")}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    /**
     * Check if Monopoly Go is installed
     */
    suspend fun isMonopolyGoInstalled(): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("pm list packages com.scopely.monopolygo")
        result.isSuccess && result.getOrNull()?.contains("com.scopely.monopolygo") == true
    }
    
    /**
     * Force stop Monopoly Go
     */
    suspend fun forceStopMonopolyGo(): Result<Unit> = withContext(Dispatchers.IO) {
        executeCommand("am force-stop com.scopely.monopolygo").map { }
    }
    
    /**
     * Launch Monopoly Go
     */
    suspend fun launchMonopolyGo(): Result<Unit> = withContext(Dispatchers.IO) {
        executeCommand("am start -n com.scopely.monopolygo/.MainActivity").map { }
    }
}
```

### 6. Permission Handler

#### 6.1 Permission Manager erstellen
**Datei:** `domain/util/PermissionManager.kt`

```kotlin
package com.mgomanager.app.domain.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Check if all necessary storage permissions are granted
     */
    fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            Environment.isExternalStorageManager()
        } else {
            // Android 9-10
            val readPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            
            val writePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            
            readPermission && writePermission
        }
    }
    
    /**
     * Request storage permissions (must be called from Activity)
     */
    fun requestStoragePermissions(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Request MANAGE_EXTERNAL_STORAGE
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                activity.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            }
        } else {
            // Android 9-10 - Request READ/WRITE permissions
            val requestPermissionLauncher = activity.registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                // Handle permission result
            }
            
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}
```

### 7. Basic Theme Setup

#### 7.1 Color Definition
**Datei:** `ui/theme/Color.kt`

```kotlin
package com.mgomanager.app.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Colors (Purple theme)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Custom Colors for App
val PrimaryPurple = Color(0xFF6200EE)
val SecondaryTeal = Color(0xFF03DAC5)

// Status Colors
val StatusGreen = Color(0xFF4CAF50)      // Sus = 0
val StatusOrange = Color(0xFFFF9800)     // Sus = 3
val StatusLightOrange = Color(0xFFFFB74D) // Sus = 7
val StatusRed = Color(0xFFF44336)        // Sus = perm or Error

// Background Colors
val BackgroundLight = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF121212)
val ErrorColor = Color(0xFFB00020)
```

#### 7.2 Theme Definition
**Datei:** `ui/theme/Theme.kt`

```kotlin
package com.mgomanager.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = BackgroundDark,
    error = ErrorColor
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    secondary = SecondaryTeal,
    tertiary = Pink40,
    background = BackgroundLight,
    error = ErrorColor
)

@Composable
fun MGOManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

#### 7.3 Typography
**Datei:** `ui/theme/Type.kt`

```kotlin
package com.mgomanager.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

### 8. MainActivity Setup

#### 8.1 MainActivity mit Root Check
**Datei:** `MainActivity.kt`

```kotlin
package com.mgomanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mgomanager.app.domain.util.PermissionManager
import com.mgomanager.app.domain.util.RootUtil
import com.mgomanager.app.ui.theme.MGOManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var rootUtil: RootUtil
    
    @Inject
    lateinit var permissionManager: PermissionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MGOManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RootCheckScreen(
                        rootUtil = rootUtil,
                        permissionManager = permissionManager,
                        activity = this
                    )
                }
            }
        }
    }
}

@Composable
fun RootCheckScreen(
    rootUtil: RootUtil,
    permissionManager: PermissionManager,
    activity: ComponentActivity
) {
    var isRooted by remember { mutableStateOf<Boolean?>(null) }
    var hasPermissions by remember { mutableStateOf(false) }
    var isMonopolyGoInstalled by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            isRooted = rootUtil.requestRootAccess()
            hasPermissions = permissionManager.hasStoragePermissions()
            isMonopolyGoInstalled = rootUtil.isMonopolyGoInstalled()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isRooted == null -> {
                CircularProgressIndicator()
            }
            isRooted == false -> {
                RootErrorDialog()
            }
            !hasPermissions -> {
                PermissionRequestDialog(
                    onRequestPermissions = {
                        permissionManager.requestStoragePermissions(activity)
                    }
                )
            }
            isMonopolyGoInstalled == false -> {
                MonopolyGoNotInstalledDialog()
            }
            else -> {
                // Success - Show placeholder for next phase
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "✓ Root-Zugriff verfügbar",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ Berechtigungen erteilt",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ Monopoly Go installiert",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "App bereit für Entwicklung!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun RootErrorDialog() {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Root-Zugriff erforderlich") },
        text = { 
            Text("Diese App benötigt Root-Zugriff, um Backups von Monopoly Go zu erstellen. Bitte roote dein Gerät oder verwende ein gerootetes Gerät.") 
        },
        confirmButton = {
            TextButton(onClick = { /* Exit app */ }) {
                Text("Schließen")
            }
        }
    )
}

@Composable
fun PermissionRequestDialog(onRequestPermissions: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Berechtigungen erforderlich") },
        text = { 
            Text("Diese App benötigt Zugriff auf den Speicher, um Backups zu erstellen. Bitte erteile die erforderlichen Berechtigungen.") 
        },
        confirmButton = {
            TextButton(onClick = onRequestPermissions) {
                Text("Berechtigungen erteilen")
            }
        }
    )
}

@Composable
fun MonopolyGoNotInstalledDialog() {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Monopoly Go nicht gefunden") },
        text = { 
            Text("Monopoly Go ist nicht auf diesem Gerät installiert. Die App kann ohne das Spiel nicht funktionieren.") 
        },
        confirmButton = {
            TextButton(onClick = { /* Exit app */ }) {
                Text("Schließen")
            }
        }
    )
}
```

---

## Testing

### Manuelle Tests
1. **Build-Test:**
   - App kompiliert ohne Fehler
   - Alle Dependencies laden korrekt
   - Keine Gradle-Sync-Fehler

2. **Root-Zugriff:**
   - App startet auf gerootet Gerät
   - Root-Dialog erscheint (SuperSU/Magisk)
   - Root-Status wird korrekt erkannt
   - Fehlermeldung bei nicht-gerootet Gerät

3. **Permissions:**
   - Permission-Dialog erscheint
   - Storage-Permissions werden erteilt
   - App erkennt erteilte Permissions

4. **Monopoly Go Check:**
   - Erkennt installiertes Monopoly Go
   - Zeigt Fehler wenn nicht installiert

5. **UI:**
   - Theme wird korrekt angewendet
   - Purple Primary Color sichtbar
   - Dark Mode funktioniert

### Unit Tests
**Datei:** `test/java/com/mgomanager/app/RootUtilTest.kt` (Optional)

```kotlin
package com.mgomanager.app

import com.mgomanager.app.domain.util.RootUtil
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class RootUtilTest {
    
    @Test
    fun testCommandExecution() = runBlocking {
        val rootUtil = RootUtil()
        // Mock test - actual implementation requires rooted device/emulator
        assertTrue(true) // Placeholder
    }
}
```

---

## Troubleshooting

### Problem: Hilt Compiler Fehler
**Symptom:** `@HiltAndroidApp`-Annotation wird nicht erkannt  
**Lösung:**
1. Prüfe dass KSP-Plugin korrekt konfiguriert ist
2. Clean + Rebuild Projekt
3. Invalidate Caches and Restart in Android Studio

### Problem: libsu Dependency nicht gefunden
**Symptom:** Import von `com.topjohnwu.superuser` fehlschlägt  
**Lösung:**
1. Prüfe `settings.gradle.kts` für JitPack Repository
2. Gradle Sync durchführen
3. Prüfe Internet-Verbindung

### Problem: Root-Zugriff schlägt fehl
**Symptom:** `isRooted()` gibt `false` zurück trotz Root  
**Lösung:**
1. Prüfe Magisk/SuperSU Installation
2. Erteile Root-Berechtigung in SuperUser-App
3. Prüfe Logcat für libsu-Fehlermeldungen

### Problem: Permission-Dialog öffnet nicht (Android 11+)
**Symptom:** `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` startet nicht  
**Lösung:**
1. Prüfe AndroidManifest für `MANAGE_EXTERNAL_STORAGE`
2. Verwende Fallback zu `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`
3. Teste auf echtem Android 11+ Gerät (nicht Emulator)

### Problem: Compose Preview zeigt nichts an
**Symptom:** `@Preview` rendert nicht  
**Lösung:**
1. Prüfe Compose Compiler Extension Version
2. Stelle sicher dass `ui-tooling` Dependency vorhanden ist
3. Build Variante auf Debug setzen

---

## Abschlusskriterien

Bevor du mit Prompt 2 fortfährst, müssen ALLE folgenden Punkte erfüllt sein:

- [ ] Projekt kompiliert ohne Fehler
- [ ] App startet auf gerootet Gerät (Android 9, 12 oder 15)
- [ ] Root-Zugriff wird erfolgreich erkannt und erteilt
- [ ] Storage-Permissions werden korrekt abgefragt und erteilt (API 28-34)
- [ ] Monopoly Go Installation wird erkannt
- [ ] Hilt Dependency Injection funktioniert (keine Injection-Fehler)
- [ ] RootUtil führt Test-Command erfolgreich aus
- [ ] Theme zeigt lila Farben korrekt an
- [ ] MainActivity zeigt "App bereit" nach erfolgreichen Checks
- [ ] Keine Compile-Warnings für veraltete APIs

---

## Nächste Schritte

**Wenn alle Aufgaben abgeschlossen wurden und alle Tests erfolgreich bestanden wurden, lese `Initial_Entwicklungsprompt_2.md` und setze die Aufgaben um.**

---

*Entwicklungsprompt 1/7 - Erstellt für MGO Manager Projekt*
