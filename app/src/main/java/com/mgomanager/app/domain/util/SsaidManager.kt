package com.mgomanager.app.domain.util

import com.mgomanager.app.data.repository.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringReader
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Manages Android ID (SSAID) by directly modifying /data/system/users/0/settings_ssaid.xml.
 *
 * This approach is more reliable than hooking Settings.Secure.getString() because:
 * - Changes persist across app restarts
 * - No race conditions with Xposed hooks
 * - Works immediately after restore without requiring hook activation
 *
 * The SSAID file stores per-app Android IDs that are returned by Settings.Secure.getString("android_id").
 */
@Singleton
class SsaidManager @Inject constructor(
    private val rootUtil: RootUtil,
    private val logRepository: LogRepository
) {

    companion object {
        const val SSAID_FILE_PATH = "/data/system/users/0/settings_ssaid.xml"
        const val SSAID_BACKUP_PATH = "/data/system/users/0/settings_ssaid.xml.bak"
        const val TARGET_PACKAGE = "com.scopely.monopolygo"

        // Android ID format: exactly 16 hexadecimal characters
        private val ANDROID_ID_REGEX = Regex("^[a-fA-F0-9]{16}$")
    }

    /**
     * Sets the Android ID for a specific package in settings_ssaid.xml.
     *
     * @param packageName The package name to set the Android ID for
     * @param androidId The Android ID (must be 16 hex characters)
     * @return true if successful, false otherwise
     */
    suspend fun setAndroidIdForPackage(packageName: String, androidId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Validate Android ID format
                if (!isValidAndroidId(androidId)) {
                    logRepository.logError(
                        "SSAID",
                        "Ungueltige Android ID Format: $androidId (erwartet: 16 Hex-Zeichen)"
                    )
                    return@withContext false
                }

                logRepository.logInfo("SSAID", "Setze Android ID fuer $packageName: $androidId")

                // Create backup before modification
                if (!createBackup()) {
                    logRepository.logWarning("SSAID", "Backup konnte nicht erstellt werden, fahre trotzdem fort")
                }

                // Read current file content or create new
                val currentContent = readSsaidFile()
                val newContent = if (currentContent == null) {
                    // File doesn't exist, create new XML
                    createNewSsaidXml(packageName, androidId)
                } else {
                    // Update existing file
                    updateSsaidXml(currentContent, packageName, androidId)
                }

                if (newContent == null) {
                    logRepository.logError("SSAID", "XML-Generierung fehlgeschlagen")
                    return@withContext false
                }

                // Write the new content
                if (!writeSsaidFile(newContent)) {
                    logRepository.logError("SSAID", "Schreiben der SSAID-Datei fehlgeschlagen")
                    return@withContext false
                }

                // Verify the write was successful
                if (verifyAndroidId(packageName, androidId)) {
                    logRepository.logInfo("SSAID", "Android ID erfolgreich gesetzt und verifiziert")
                    return@withContext true
                } else {
                    logRepository.logError("SSAID", "Verifikation fehlgeschlagen - geschriebener Wert stimmt nicht")
                    return@withContext false
                }

            } catch (e: Exception) {
                logRepository.logError("SSAID", "Fehler beim Setzen der Android ID: ${e.message}", exception = e)
                return@withContext false
            }
        }

    /**
     * Reads the Android ID for a specific package from settings_ssaid.xml.
     *
     * @param packageName The package name to read the Android ID for
     * @return The Android ID if found, null otherwise
     */
    suspend fun getAndroidIdForPackage(packageName: String): String? = withContext(Dispatchers.IO) {
        try {
            val content = readSsaidFile() ?: return@withContext null

            val doc = parseXml(content) ?: return@withContext null
            val settingsElement = doc.documentElement
            val settingNodes = settingsElement.getElementsByTagName("setting")

            for (i in 0 until settingNodes.length) {
                val settingElement = settingNodes.item(i) as Element
                val name = settingElement.getAttribute("name")
                if (name == packageName) {
                    return@withContext settingElement.getAttribute("value")
                }
            }

            null
        } catch (e: Exception) {
            logRepository.logError("SSAID", "Fehler beim Lesen der Android ID: ${e.message}", exception = e)
            null
        }
    }

    /**
     * Verifies that the written Android ID matches the expected value.
     *
     * @param packageName The package name to verify
     * @param expectedId The expected Android ID
     * @return true if the value matches, false otherwise
     */
    suspend fun verifyAndroidId(packageName: String, expectedId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val actualId = getAndroidIdForPackage(packageName)
                actualId?.lowercase() == expectedId.lowercase()
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Validates that the Android ID has the correct format.
     *
     * @param androidId The Android ID to validate
     * @return true if valid (16 hex characters), false otherwise
     */
    fun isValidAndroidId(androidId: String): Boolean {
        return ANDROID_ID_REGEX.matches(androidId)
    }

    /**
     * Creates a backup of the settings_ssaid.xml file.
     */
    private suspend fun createBackup(): Boolean {
        return try {
            val result = rootUtil.executeCommand("cp $SSAID_FILE_PATH $SSAID_BACKUP_PATH 2>/dev/null || true")
            if (result.isSuccess) {
                logRepository.logInfo("SSAID", "Backup erstellt: $SSAID_BACKUP_PATH")
                true
            } else {
                // File might not exist yet, which is okay
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads the content of settings_ssaid.xml using root.
     */
    private suspend fun readSsaidFile(): String? {
        return try {
            val result = rootUtil.executeCommand("cat $SSAID_FILE_PATH 2>/dev/null")
            if (result.isSuccess) {
                val content = result.getOrNull()
                if (!content.isNullOrBlank() && content.contains("<?xml")) {
                    content
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Writes content to settings_ssaid.xml and sets proper permissions.
     */
    private suspend fun writeSsaidFile(content: String): Boolean {
        return try {
            // Write to a temp file first, then move (more atomic)
            val tempPath = "/data/local/tmp/settings_ssaid_temp.xml"

            // Escape content for shell (use base64 to avoid issues with special characters)
            val base64Content = android.util.Base64.encodeToString(
                content.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )

            // Write using base64 decode to avoid shell escaping issues
            val writeResult = rootUtil.executeCommand(
                "echo '$base64Content' | base64 -d > $tempPath"
            )

            if (writeResult.isFailure) {
                logRepository.logError("SSAID", "Schreiben der Temp-Datei fehlgeschlagen")
                return false
            }

            // Move to final location
            val moveResult = rootUtil.executeCommand("mv $tempPath $SSAID_FILE_PATH")
            if (moveResult.isFailure) {
                logRepository.logError("SSAID", "Verschieben der Datei fehlgeschlagen")
                return false
            }

            // Set proper permissions: 660 system:system
            rootUtil.executeCommand("chmod 660 $SSAID_FILE_PATH")
            rootUtil.executeCommand("chown system:system $SSAID_FILE_PATH")

            // Force sync to ensure data is written
            rootUtil.executeCommand("sync")

            true
        } catch (e: Exception) {
            logRepository.logError("SSAID", "Fehler beim Schreiben: ${e.message}", exception = e)
            false
        }
    }

    /**
     * Creates a new settings_ssaid.xml with a single package entry.
     */
    private fun createNewSsaidXml(packageName: String, androidId: String): String {
        return """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<settings version="1">
    <setting id="1" name="$packageName" value="${androidId.lowercase()}" package="$packageName" defaultValue="${androidId.lowercase()}" defaultSysSet="true" />
</settings>
"""
    }

    /**
     * Updates existing settings_ssaid.xml with a new or updated package entry.
     */
    private fun updateSsaidXml(currentContent: String, packageName: String, androidId: String): String? {
        return try {
            val doc = parseXml(currentContent) ?: return null
            val settingsElement = doc.documentElement
            val settingNodes = settingsElement.getElementsByTagName("setting")

            var packageFound = false
            var maxId = 0

            // Find the package entry and track max ID
            for (i in 0 until settingNodes.length) {
                val settingElement = settingNodes.item(i) as Element
                val id = settingElement.getAttribute("id").toIntOrNull() ?: 0
                maxId = maxOf(maxId, id)

                val name = settingElement.getAttribute("name")
                if (name == packageName) {
                    // Update existing entry
                    settingElement.setAttribute("value", androidId.lowercase())
                    settingElement.setAttribute("defaultValue", androidId.lowercase())
                    packageFound = true
                }
            }

            // If package not found, add new entry
            if (!packageFound) {
                val newSetting = doc.createElement("setting")
                newSetting.setAttribute("id", (maxId + 1).toString())
                newSetting.setAttribute("name", packageName)
                newSetting.setAttribute("value", androidId.lowercase())
                newSetting.setAttribute("package", packageName)
                newSetting.setAttribute("defaultValue", androidId.lowercase())
                newSetting.setAttribute("defaultSysSet", "true")

                // Add with proper indentation
                settingsElement.appendChild(doc.createTextNode("\n    "))
                settingsElement.appendChild(newSetting)
                settingsElement.appendChild(doc.createTextNode("\n"))
            }

            documentToString(doc)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses XML string to Document.
     */
    private fun parseXml(xml: String): Document? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val inputSource = InputSource(StringReader(xml))
            builder.parse(inputSource)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts Document to XML string.
     */
    private fun documentToString(doc: Document): String? {
        return try {
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            transformer.setOutputProperty(OutputKeys.INDENT, "no")
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes")

            val writer = StringWriter()
            transformer.transform(DOMSource(doc), StreamResult(writer))

            // Ensure proper XML declaration format
            var result = writer.toString()
            if (!result.startsWith("<?xml")) {
                result = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n$result"
            }

            result
        } catch (e: Exception) {
            null
        }
    }
}
