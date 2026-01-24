package com.mgomanager.app

import android.app.Application
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.data.repository.LogRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
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

        // Initialize Timber for logging (optional but helpful)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // libsu configuration - CRITICAL for root access
        com.topjohnwu.superuser.Shell.enableVerboseLogging = BuildConfig.DEBUG
        com.topjohnwu.superuser.Shell.setDefaultBuilder(
            com.topjohnwu.superuser.Shell.Builder.create()
                .setFlags(com.topjohnwu.superuser.Shell.FLAG_REDIRECT_STDERR or com.topjohnwu.superuser.Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )

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
