package com.mgomanager.app.domain.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.data.repository.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class for parsed SSH server connection info
 */
data class SSHConnectionInfo(
    val username: String,
    val host: String,
    val port: Int = 22
)

/**
 * Result of checking the latest backup on the server
 */
sealed class ServerBackupCheckResult {
    data class Found(val date: Date, val filename: String) : ServerBackupCheckResult()
    data class NotConfigured(val reason: String) : ServerBackupCheckResult()
    data class Error(val message: String, val exception: Exception? = null) : ServerBackupCheckResult()
}

/**
 * Result of SSH operations
 */
sealed class SSHOperationResult {
    data class Success(val message: String) : SSHOperationResult()
    data class Error(val message: String, val exception: Exception? = null) : SSHOperationResult()
}

/**
 * Service for SSH/SFTP operations to sync backups with a remote server
 */
@Singleton
class SSHSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val logRepository: LogRepository
) {

    companion object {
        private const val TAG = "SSHSyncService"
        private const val CONNECTION_TIMEOUT_MS = 30000
        private const val READ_TIMEOUT_MS = 60000

        // Authentication methods
        const val AUTH_KEY_ONLY = "key_only"
        const val AUTH_PASSWORD_ONLY = "password_only"
        const val AUTH_TRY_BOTH = "try_both"
    }

    /**
     * Check if network is available
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Parse SSH server string (user@host:port or user@host)
     */
    fun parseServerString(serverString: String): SSHConnectionInfo? {
        if (serverString.isBlank()) return null

        return try {
            val atIndex = serverString.lastIndexOf('@')
            if (atIndex == -1) return null

            val username = serverString.substring(0, atIndex)
            val hostPart = serverString.substring(atIndex + 1)

            val (host, port) = if (hostPart.contains(':')) {
                val colonIndex = hostPart.lastIndexOf(':')
                val hostStr = hostPart.substring(0, colonIndex)
                val portStr = hostPart.substring(colonIndex + 1)
                hostStr to (portStr.toIntOrNull() ?: 22)
            } else {
                hostPart to 22
            }

            if (username.isBlank() || host.isBlank()) return null

            SSHConnectionInfo(username, host, port)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse server string: $serverString")
            null
        }
    }

    /**
     * Authenticate SSH client based on configured auth method
     * @return true if authentication successful, throws exception otherwise
     */
    private suspend fun authenticateSSH(
        ssh: SSHClient,
        username: String,
        keyPath: String,
        password: String,
        authMethod: String
    ): String {
        val keyFile = File(keyPath)
        val hasKey = keyFile.exists()
        val hasPassword = password.isNotBlank()

        var lastError: Exception? = null
        var usedMethod = ""

        // Try key authentication
        if ((authMethod == AUTH_KEY_ONLY || authMethod == AUTH_TRY_BOTH) && hasKey) {
            try {
                Timber.d("Attempting key authentication...")
                val keyProvider: KeyProvider = ssh.loadKeys(keyPath)
                ssh.authPublickey(username, keyProvider)
                usedMethod = "Key"
                Timber.d("Key authentication successful")
                return usedMethod
            } catch (e: UserAuthException) {
                Timber.w(e, "Key authentication failed")
                lastError = e
                if (authMethod == AUTH_KEY_ONLY) {
                    throw Exception("Key-Authentifizierung fehlgeschlagen: ${e.message}", e)
                }
            } catch (e: Exception) {
                Timber.w(e, "Key authentication error")
                lastError = e
                if (authMethod == AUTH_KEY_ONLY) {
                    throw Exception("Key-Fehler: ${e.message}", e)
                }
            }
        }

        // Try password authentication
        if ((authMethod == AUTH_PASSWORD_ONLY || authMethod == AUTH_TRY_BOTH) && hasPassword) {
            try {
                Timber.d("Attempting password authentication...")
                ssh.authPassword(username, password)
                usedMethod = "Passwort"
                Timber.d("Password authentication successful")
                return usedMethod
            } catch (e: UserAuthException) {
                Timber.w(e, "Password authentication failed")
                lastError = e
                if (authMethod == AUTH_PASSWORD_ONLY) {
                    throw Exception("Passwort-Authentifizierung fehlgeschlagen: ${e.message}", e)
                }
            } catch (e: Exception) {
                Timber.w(e, "Password authentication error")
                lastError = e
                if (authMethod == AUTH_PASSWORD_ONLY) {
                    throw Exception("Passwort-Fehler: ${e.message}", e)
                }
            }
        }

        // If we get here, all methods failed
        val errorMsg = when {
            authMethod == AUTH_KEY_ONLY && !hasKey -> "Private Key nicht gefunden: $keyPath"
            authMethod == AUTH_PASSWORD_ONLY && !hasPassword -> "Kein Passwort konfiguriert"
            authMethod == AUTH_TRY_BOTH && !hasKey && !hasPassword -> "Weder Key noch Passwort konfiguriert"
            else -> "Authentifizierung fehlgeschlagen: ${lastError?.message ?: "Unbekannter Fehler"}"
        }
        throw Exception(errorMsg, lastError)
    }

    /**
     * Test SSH connection with current settings
     * @return SSHOperationResult with connection test result
     */
    suspend fun testConnection(): SSHOperationResult = withContext(Dispatchers.IO) {
        // Check network availability first
        if (!isNetworkAvailable()) {
            return@withContext SSHOperationResult.Error("Keine Netzwerkverbindung. Bitte WLAN oder mobile Daten aktivieren.")
        }

        val serverString = settingsDataStore.sshServer.first()
        val keyPath = settingsDataStore.sshPrivateKeyPath.first()
        val password = settingsDataStore.sshPassword.first()
        val authMethod = settingsDataStore.sshAuthMethod.first()
        val remotePath = settingsDataStore.sshBackupPath.first()

        // Validate settings
        val connectionInfo = parseServerString(serverString)
        if (connectionInfo == null) {
            return@withContext SSHOperationResult.Error("Ungültiges Server-Format. Verwende: user@host:port")
        }

        // Validate auth requirements
        val keyFile = File(keyPath)
        when (authMethod) {
            AUTH_KEY_ONLY -> if (!keyFile.exists()) {
                return@withContext SSHOperationResult.Error("Private Key nicht gefunden: $keyPath")
            }
            AUTH_PASSWORD_ONLY -> if (password.isBlank()) {
                return@withContext SSHOperationResult.Error("Kein Passwort konfiguriert")
            }
            AUTH_TRY_BOTH -> if (!keyFile.exists() && password.isBlank()) {
                return@withContext SSHOperationResult.Error("Weder Key noch Passwort konfiguriert")
            }
        }

        var ssh: SSHClient? = null
        try {
            logRepository.logInfo(TAG, "Testing SSH connection to ${connectionInfo.host}:${connectionInfo.port}")

            ssh = SSHClient()
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connectTimeout = CONNECTION_TIMEOUT_MS
            ssh.timeout = READ_TIMEOUT_MS

            ssh.connect(connectionInfo.host, connectionInfo.port)

            // Authenticate using configured method
            val usedMethod = authenticateSSH(ssh, connectionInfo.username, keyPath, password, authMethod)

            // Test SFTP access
            val sftp = ssh.newSFTPClient()
            try {
                // Check if remote path exists
                val attrs = sftp.statExistence(remotePath)
                if (attrs == null) {
                    sftp.mkdirs(remotePath)
                    logRepository.logInfo(TAG, "Created remote directory: $remotePath")
                }

                // List files to test access
                val files = sftp.ls(remotePath)
                val zipCount = files.count { it.name.endsWith(".zip") }

                logRepository.logInfo(TAG, "SSH connection successful via $usedMethod. Found $zipCount ZIP files.")
                SSHOperationResult.Success("Verbindung erfolgreich ($usedMethod)! $zipCount ZIP-Dateien gefunden.")
            } finally {
                sftp.close()
            }
        } catch (e: Exception) {
            Timber.e(e, "SSH connection test failed")
            logRepository.logError(TAG, "SSH connection test failed: ${e.message}", exception = e)
            SSHOperationResult.Error("Verbindung fehlgeschlagen: ${e.message}", e)
        } finally {
            try {
                ssh?.disconnect()
            } catch (e: Exception) {
                Timber.w(e, "Error disconnecting SSH")
            }
        }
    }

    /**
     * Check the latest backup date on the server
     * @return ServerBackupCheckResult with the latest backup info
     */
    suspend fun checkLatestServerBackup(): ServerBackupCheckResult = withContext(Dispatchers.IO) {
        // Check network availability first
        if (!isNetworkAvailable()) {
            return@withContext ServerBackupCheckResult.Error("Keine Netzwerkverbindung")
        }

        val serverString = settingsDataStore.sshServer.first()
        val keyPath = settingsDataStore.sshPrivateKeyPath.first()
        val password = settingsDataStore.sshPassword.first()
        val authMethod = settingsDataStore.sshAuthMethod.first()
        val remotePath = settingsDataStore.sshBackupPath.first()

        // Validate settings
        if (serverString.isBlank()) {
            return@withContext ServerBackupCheckResult.NotConfigured("SSH-Server nicht konfiguriert")
        }

        val connectionInfo = parseServerString(serverString)
        if (connectionInfo == null) {
            return@withContext ServerBackupCheckResult.Error("Ungültiges Server-Format")
        }

        // Check if we have any authentication configured
        val keyFile = File(keyPath)
        if (!keyFile.exists() && password.isBlank()) {
            return@withContext ServerBackupCheckResult.NotConfigured("Keine Authentifizierung konfiguriert")
        }

        var ssh: SSHClient? = null
        try {
            ssh = SSHClient()
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connectTimeout = CONNECTION_TIMEOUT_MS
            ssh.timeout = READ_TIMEOUT_MS

            ssh.connect(connectionInfo.host, connectionInfo.port)

            authenticateSSH(ssh, connectionInfo.username, keyPath, password, authMethod)

            val sftp = ssh.newSFTPClient()
            try {
                val files = sftp.ls(remotePath)
                val zipFiles = files.filter { it.name.endsWith(".zip") && it.name.startsWith("mgo_export_") }

                if (zipFiles.isEmpty()) {
                    return@withContext ServerBackupCheckResult.NotConfigured("Keine Backups auf dem Server gefunden")
                }

                // Find the most recent file by modification time
                val latestFile = zipFiles.maxByOrNull { it.attributes.mtime }
                    ?: return@withContext ServerBackupCheckResult.NotConfigured("Keine Backups gefunden")

                val date = Date(latestFile.attributes.mtime * 1000L)
                logRepository.logInfo(TAG, "Latest server backup: ${latestFile.name} (${date})")

                ServerBackupCheckResult.Found(date, latestFile.name)
            } finally {
                sftp.close()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check server backup")
            logRepository.logError(TAG, "Failed to check server backup: ${e.message}", exception = e)
            ServerBackupCheckResult.Error("Prüfung fehlgeschlagen: ${e.message}", e)
        } finally {
            try {
                ssh?.disconnect()
            } catch (e: Exception) {
                Timber.w(e, "Error disconnecting SSH")
            }
        }
    }

    /**
     * Upload a ZIP file to the server
     * @param localZipPath Path to the local ZIP file
     * @return SSHOperationResult with upload result
     */
    suspend fun uploadZip(localZipPath: String): SSHOperationResult = withContext(Dispatchers.IO) {
        // Check network availability first
        if (!isNetworkAvailable()) {
            return@withContext SSHOperationResult.Error("Keine Netzwerkverbindung. Upload nicht möglich.")
        }

        val serverString = settingsDataStore.sshServer.first()
        val keyPath = settingsDataStore.sshPrivateKeyPath.first()
        val password = settingsDataStore.sshPassword.first()
        val authMethod = settingsDataStore.sshAuthMethod.first()
        val remotePath = settingsDataStore.sshBackupPath.first()

        // Validate settings
        val connectionInfo = parseServerString(serverString)
        if (connectionInfo == null) {
            return@withContext SSHOperationResult.Error("SSH-Server nicht konfiguriert")
        }

        // Check if we have any authentication configured
        val keyFile = File(keyPath)
        if (!keyFile.exists() && password.isBlank()) {
            return@withContext SSHOperationResult.Error("Keine Authentifizierung konfiguriert")
        }

        val localFile = File(localZipPath)
        if (!localFile.exists()) {
            return@withContext SSHOperationResult.Error("Lokale Datei nicht gefunden: $localZipPath")
        }

        var ssh: SSHClient? = null
        try {
            logRepository.logInfo(TAG, "Uploading $localZipPath to server")

            ssh = SSHClient()
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connectTimeout = CONNECTION_TIMEOUT_MS
            ssh.timeout = READ_TIMEOUT_MS

            ssh.connect(connectionInfo.host, connectionInfo.port)

            authenticateSSH(ssh, connectionInfo.username, keyPath, password, authMethod)

            val sftp = ssh.newSFTPClient()
            try {
                // Ensure remote directory exists
                val attrs = sftp.statExistence(remotePath)
                if (attrs == null) {
                    sftp.mkdirs(remotePath)
                }

                val remoteFilePath = "$remotePath/${localFile.name}"
                sftp.put(localZipPath, remoteFilePath)

                // Update last sync timestamp
                settingsDataStore.setSshLastSyncTimestamp(System.currentTimeMillis())

                logRepository.logInfo(TAG, "Upload successful: $remoteFilePath")
                SSHOperationResult.Success("Upload erfolgreich: ${localFile.name}")
            } finally {
                sftp.close()
            }
        } catch (e: Exception) {
            Timber.e(e, "Upload failed")
            logRepository.logError(TAG, "Upload failed: ${e.message}", exception = e)
            SSHOperationResult.Error("Upload fehlgeschlagen: ${e.message}", e)
        } finally {
            try {
                ssh?.disconnect()
            } catch (e: Exception) {
                Timber.w(e, "Error disconnecting SSH")
            }
        }
    }

    /**
     * Download the latest backup from the server
     * @param localDestination Local directory to save the file
     * @return SSHOperationResult with download result (includes file path on success)
     */
    suspend fun downloadLatestBackup(localDestination: String): SSHOperationResult = withContext(Dispatchers.IO) {
        // Check network availability first
        if (!isNetworkAvailable()) {
            return@withContext SSHOperationResult.Error("Keine Netzwerkverbindung. Download nicht möglich.")
        }

        val serverString = settingsDataStore.sshServer.first()
        val keyPath = settingsDataStore.sshPrivateKeyPath.first()
        val password = settingsDataStore.sshPassword.first()
        val authMethod = settingsDataStore.sshAuthMethod.first()
        val remotePath = settingsDataStore.sshBackupPath.first()

        // Validate settings
        val connectionInfo = parseServerString(serverString)
        if (connectionInfo == null) {
            return@withContext SSHOperationResult.Error("SSH-Server nicht konfiguriert")
        }

        // Check if we have any authentication configured
        val keyFile = File(keyPath)
        if (!keyFile.exists() && password.isBlank()) {
            return@withContext SSHOperationResult.Error("Keine Authentifizierung konfiguriert")
        }

        var ssh: SSHClient? = null
        try {
            logRepository.logInfo(TAG, "Downloading latest backup from server")

            ssh = SSHClient()
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connectTimeout = CONNECTION_TIMEOUT_MS
            ssh.timeout = READ_TIMEOUT_MS

            ssh.connect(connectionInfo.host, connectionInfo.port)

            authenticateSSH(ssh, connectionInfo.username, keyPath, password, authMethod)

            val sftp = ssh.newSFTPClient()
            try {
                val files = sftp.ls(remotePath)
                val zipFiles = files.filter { it.name.endsWith(".zip") && it.name.startsWith("mgo_export_") }

                if (zipFiles.isEmpty()) {
                    return@withContext SSHOperationResult.Error("Keine Backups auf dem Server gefunden")
                }

                val latestFile = zipFiles.maxByOrNull { it.attributes.mtime }
                    ?: return@withContext SSHOperationResult.Error("Keine Backups gefunden")

                val remoteFilePath = "$remotePath/${latestFile.name}"
                val localFile = File(localDestination, latestFile.name)

                // Ensure local directory exists
                localFile.parentFile?.mkdirs()

                sftp.get(remoteFilePath, localFile.absolutePath)

                // Update last sync timestamp
                settingsDataStore.setSshLastSyncTimestamp(System.currentTimeMillis())

                logRepository.logInfo(TAG, "Download successful: ${localFile.absolutePath}")
                SSHOperationResult.Success(localFile.absolutePath)
            } finally {
                sftp.close()
            }
        } catch (e: Exception) {
            Timber.e(e, "Download failed")
            logRepository.logError(TAG, "Download failed: ${e.message}", exception = e)
            SSHOperationResult.Error("Download fehlgeschlagen: ${e.message}", e)
        } finally {
            try {
                ssh?.disconnect()
            } catch (e: Exception) {
                Timber.w(e, "Error disconnecting SSH")
            }
        }
    }

    /**
     * Get latest local backup date
     */
    suspend fun getLatestLocalBackupDate(): Date? = withContext(Dispatchers.IO) {
        val exportDir = File("/storage/emulated/0/mgo/exports/")
        if (!exportDir.exists()) return@withContext null

        val zipFiles = exportDir.listFiles { file ->
            file.name.startsWith("mgo_export_") && file.name.endsWith(".zip")
        }?.sortedByDescending { it.lastModified() }

        zipFiles?.firstOrNull()?.let { Date(it.lastModified()) }
    }

    /**
     * Format date for display
     */
    fun formatDate(date: Date): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(date)
    }

    /**
     * Format timestamp for display
     */
    fun formatTimestamp(timestamp: Long): String {
        return if (timestamp > 0) {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(timestamp))
        } else {
            "Noch nie"
        }
    }
}
