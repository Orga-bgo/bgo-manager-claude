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
 *
 * Supports both XML and ABX2 (Android Binary XML) formats:
 * - On newer Android versions, the file may be stored in ABX2 binary format
 * - This class automatically detects the format and converts as needed
 * - Falls back to SQLite database if ABX2 tools are not available
 */
@Singleton
class SsaidManager @Inject constructor(
    private val rootUtil: RootUtil,
    private val logRepository: LogRepository
) {

    companion object {
        const val SSAID_FILE_PATH = "/data/system/users/0/settings_ssaid.xml"
        const val SSAID_BACKUP_PATH = "/data/system/users/0/settings_ssaid.xml.bak"
        const val SSAID_DB_PATH = "/data/system/users/0/settings_ssaid.db"
        const val TARGET_PACKAGE = "com.scopely.monopolygo"

        // Temp paths for conversion
        private const val TEMP_XML_PATH = "/data/local/tmp/settings_ssaid_temp.xml"
        private const val TEMP_ABX_PATH = "/data/local/tmp/settings_ssaid_temp.abx"

        // ABX2 conversion tools
        private const val ABX2XML_TOOL = "/system/bin/abx2xml"
        private const val XML2ABX_TOOL = "/system/bin/xml2abx"

        // Android ID format: exactly 16 hexadecimal characters
        private val ANDROID_ID_REGEX = Regex("^[a-fA-F0-9]{16}$")

        // ABX2 magic bytes (first 4 bytes of ABX2 file)
        private const val ABX2_MAGIC = "ABX"
    }

    // Track original file format for write-back
    private var originalWasAbx2Format = false
    private var originalSettingsVersion = "1"

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

                // Check if file exists and detect format
                val fileExists = checkFileExists(SSAID_FILE_PATH)
                if (!fileExists) {
                    logRepository.logInfo("SSAID", "SSAID-Datei existiert nicht, erstelle neue")
                    originalWasAbx2Format = false
                }

                // Read current file content (handles ABX2 conversion automatically)
                val currentXmlContent = readSsaidFile()

                // Log XML preview for debugging
                if (currentXmlContent != null) {
                    logRepository.logInfo("SSAID", "XML Preview (erste 500 Zeichen): ${currentXmlContent.take(500)}")
                }

                // Generate new XML content
                val newXmlContent = if (currentXmlContent == null) {
                    // File doesn't exist or couldn't be read, create new XML
                    logRepository.logInfo("SSAID", "Erstelle neue SSAID-Datei")
                    createNewSsaidXml(packageName, androidId)
                } else {
                    // Update existing file
                    updateSsaidXml(currentXmlContent, packageName, androidId)
                }

                if (newXmlContent == null) {
                    logRepository.logError("SSAID", "XML-Generierung fehlgeschlagen")
                    restoreBackup()
                    return@withContext false
                }

                // Write the new content (handles ABX2 conversion if needed)
                if (!writeSsaidFile(newXmlContent)) {
                    logRepository.logError("SSAID", "Schreiben der SSAID-Datei fehlgeschlagen")
                    restoreBackup()
                    return@withContext false
                }

                // Verify the write was successful
                if (verifyAndroidId(packageName, androidId)) {
                    logRepository.logInfo("SSAID", "Android ID erfolgreich gesetzt und verifiziert")
                    return@withContext true
                } else {
                    logRepository.logError("SSAID", "Verifikation fehlgeschlagen - geschriebener Wert stimmt nicht")

                    // Try SQLite fallback
                    logRepository.logInfo("SSAID", "Versuche SQLite-Fallback...")
                    if (setViaSqlite(packageName, androidId)) {
                        logRepository.logInfo("SSAID", "SQLite-Fallback erfolgreich")
                        return@withContext true
                    }

                    restoreBackup()
                    return@withContext false
                }

            } catch (e: Exception) {
                logRepository.logError("SSAID", "Fehler beim Setzen der Android ID: ${e.message}", exception = e)
                restoreBackup()
                return@withContext false
            }
        }

    /**
     * Reads the Android ID for a specific package from settings_ssaid.xml.
     *
     * Note: In abx2xml output, we search by `package` attribute (not `name`).
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
                // FIX: Search by package attribute instead of name
                val pkg = settingElement.getAttribute("package")

                if (pkg == packageName) {
                    return@withContext settingElement.getAttribute("value")
                }
            }

            null
        } catch (e: Exception) {
            logRepository.logError("SSAID", "getAndroidIdForPackage Exception: ${e.message}", exception = e)
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

    // ==================== ABX2 Format Detection and Conversion ====================

    /**
     * Checks if the raw file content is in ABX2 binary format.
     * ABX2 files start with "ABX" magic bytes and don't contain XML declaration.
     */
    private fun isAbx2Format(rawContent: String): Boolean {
        // ABX2 files start with "ABX" followed by version byte
        if (rawContent.startsWith(ABX2_MAGIC)) {
            return true
        }

        // If it doesn't contain XML declaration and has binary-looking content
        if (!rawContent.contains("<?xml") && !rawContent.contains("<settings")) {
            // Check for non-printable characters (binary indicator)
            val nonPrintableCount = rawContent.take(100).count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
            if (nonPrintableCount > 5) {
                return true
            }
        }

        return false
    }

    /**
     * Checks if a system tool exists.
     */
    private suspend fun toolExists(toolPath: String): Boolean {
        val result = rootUtil.executeCommand("[ -f $toolPath ] && echo 'exists'")
        return result.isSuccess && result.getOrNull()?.contains("exists") == true
    }

    /**
     * Converts ABX2 binary format to XML using system tool.
     *
     * @param inputPath Path to the ABX2 file
     * @return XML content as string, or null if conversion fails
     */
    private suspend fun convertAbx2ToXml(inputPath: String): String? {
        // Check if abx2xml tool exists
        if (!toolExists(ABX2XML_TOOL)) {
            logRepository.logWarning("SSAID", "abx2xml Tool nicht gefunden: $ABX2XML_TOOL")
            return null
        }

        // Convert ABX2 to XML
        val outputPath = TEMP_XML_PATH
        val result = rootUtil.executeCommand("$ABX2XML_TOOL $inputPath $outputPath 2>&1")

        if (result.isFailure) {
            logRepository.logError("SSAID", "ABX2 zu XML Konvertierung fehlgeschlagen: ${result.exceptionOrNull()?.message}")
            return null
        }

        // Read the converted XML
        val xmlResult = rootUtil.executeCommand("cat $outputPath 2>/dev/null")
        if (xmlResult.isSuccess) {
            val content = xmlResult.getOrNull()
            if (!content.isNullOrBlank() && (content.contains("<?xml") || content.contains("<settings"))) {
                logRepository.logInfo("SSAID", "ABX2 zu XML Konvertierung erfolgreich")
                return content
            }
        }

        logRepository.logError("SSAID", "Konvertierte XML-Datei konnte nicht gelesen werden")
        return null
    }

    /**
     * Converts XML to ABX2 binary format using system tool.
     *
     * @param xmlContent XML content to convert
     * @param outputPath Path for the output ABX2 file
     * @return true if conversion successful, false otherwise
     */
    private suspend fun convertXmlToAbx2(xmlContent: String, outputPath: String): Boolean {
        // Check if xml2abx tool exists
        if (!toolExists(XML2ABX_TOOL)) {
            logRepository.logWarning("SSAID", "xml2abx Tool nicht gefunden: $XML2ABX_TOOL")
            return false
        }

        // Write XML to temp file first
        val tempXmlPath = TEMP_XML_PATH
        if (!writeXmlToTempFile(xmlContent, tempXmlPath)) {
            logRepository.logError("SSAID", "Konnte XML nicht in Temp-Datei schreiben")
            return false
        }

        // Convert XML to ABX2
        val result = rootUtil.executeCommand("$XML2ABX_TOOL $tempXmlPath $outputPath 2>&1")

        if (result.isFailure) {
            logRepository.logError("SSAID", "XML zu ABX2 Konvertierung fehlgeschlagen: ${result.exceptionOrNull()?.message}")
            return false
        }

        // Verify output file exists and has content
        val checkResult = rootUtil.executeCommand("[ -s $outputPath ] && echo 'ok'")
        if (checkResult.isSuccess && checkResult.getOrNull()?.contains("ok") == true) {
            logRepository.logInfo("SSAID", "XML zu ABX2 Konvertierung erfolgreich")
            return true
        }

        logRepository.logError("SSAID", "ABX2 Ausgabedatei ist leer oder existiert nicht")
        return false
    }

    /**
     * Writes XML content to a temporary file using base64 encoding.
     */
    private suspend fun writeXmlToTempFile(content: String, path: String): Boolean {
        return try {
            val base64Content = android.util.Base64.encodeToString(
                content.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )

            val writeResult = rootUtil.executeCommand(
                "echo '$base64Content' | base64 -d > $path"
            )

            writeResult.isSuccess
        } catch (e: Exception) {
            false
        }
    }

    // ==================== File Operations ====================

    /**
     * Checks if a file exists.
     */
    private suspend fun checkFileExists(path: String): Boolean {
        val result = rootUtil.executeCommand("[ -f $path ] && echo 'exists'")
        return result.isSuccess && result.getOrNull()?.contains("exists") == true
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
     * Restores the backup file.
     */
    private suspend fun restoreBackup(): Boolean {
        return try {
            if (checkFileExists(SSAID_BACKUP_PATH)) {
                val result = rootUtil.executeCommand("cp $SSAID_BACKUP_PATH $SSAID_FILE_PATH && chmod 660 $SSAID_FILE_PATH && chown system:system $SSAID_FILE_PATH")
                if (result.isSuccess) {
                    logRepository.logInfo("SSAID", "Backup wiederhergestellt")
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads the content of settings_ssaid.xml using root.
     * Automatically handles ABX2 format conversion.
     */
    private suspend fun readSsaidFile(): String? {
        return try {
            // First, read raw bytes to detect format
            val rawResult = rootUtil.executeCommand("cat $SSAID_FILE_PATH 2>/dev/null")
            if (rawResult.isFailure) {
                logRepository.logInfo("SSAID", "SSAID-Datei konnte nicht gelesen werden (existiert moeglicherweise nicht)")
                return null
            }

            val rawContent = rawResult.getOrNull()
            if (rawContent.isNullOrBlank()) {
                logRepository.logInfo("SSAID", "SSAID-Datei ist leer")
                return null
            }

            // Detect format
            if (isAbx2Format(rawContent)) {
                logRepository.logInfo("SSAID", "ABX2-Format erkannt, konvertiere zu XML...")
                originalWasAbx2Format = true

                val xmlContent = convertAbx2ToXml(SSAID_FILE_PATH)
                if (xmlContent != null) {
                    // Extract settings version from the converted XML
                    extractSettingsVersion(xmlContent)
                    return xmlContent
                }

                logRepository.logWarning("SSAID", "ABX2-Konvertierung fehlgeschlagen, versuche SQLite-Fallback")
                return null
            }

            // Already XML format
            if (rawContent.contains("<?xml") || rawContent.contains("<settings")) {
                logRepository.logInfo("SSAID", "XML-Format erkannt")
                originalWasAbx2Format = false
                extractSettingsVersion(rawContent)
                return rawContent
            }

            logRepository.logWarning("SSAID", "Unbekanntes Dateiformat")
            null
        } catch (e: Exception) {
            logRepository.logError("SSAID", "Fehler beim Lesen: ${e.message}", exception = e)
            null
        }
    }

    /**
     * Extracts the settings version from XML content.
     * Handles both positive (e.g., "1") and negative (e.g., "-1") version numbers.
     */
    private fun extractSettingsVersion(xmlContent: String) {
        // Match version including optional negative sign
        val versionRegex = Regex("""<settings[^>]*version="(-?\d+)"[^>]*>""")
        val match = versionRegex.find(xmlContent)
        if (match != null) {
            originalSettingsVersion = match.groupValues[1]
        }
    }

    /**
     * Writes content to settings_ssaid.xml and sets proper permissions.
     * Handles ABX2 conversion if the original file was in ABX2 format.
     */
    private suspend fun writeSsaidFile(xmlContent: String): Boolean {
        return try {
            val finalPath: String

            if (originalWasAbx2Format) {
                logRepository.logInfo("SSAID", "Original war ABX2-Format, konvertiere zurueck...")

                // Convert XML to ABX2
                if (!convertXmlToAbx2(xmlContent, TEMP_ABX_PATH)) {
                    logRepository.logWarning("SSAID", "ABX2-Konvertierung fehlgeschlagen, schreibe als XML")
                    // Fallback: write as XML (might not work on all devices)
                    finalPath = TEMP_XML_PATH
                    if (!writeXmlToTempFile(xmlContent, finalPath)) {
                        return false
                    }
                } else {
                    finalPath = TEMP_ABX_PATH
                }
            } else {
                // Write as XML
                logRepository.logInfo("SSAID", "Schreibe als XML-Format")
                finalPath = TEMP_XML_PATH
                if (!writeXmlToTempFile(xmlContent, finalPath)) {
                    logRepository.logError("SSAID", "Schreiben der Temp-Datei fehlgeschlagen")
                    return false
                }
            }

            // Move to final location
            val moveResult = rootUtil.executeCommand("mv $finalPath $SSAID_FILE_PATH")
            if (moveResult.isFailure) {
                logRepository.logError("SSAID", "Verschieben der Datei fehlgeschlagen")
                return false
            }

            // Set proper permissions: 660 system:system
            rootUtil.executeCommand("chmod 660 $SSAID_FILE_PATH")
            rootUtil.executeCommand("chown system:system $SSAID_FILE_PATH")

            // Force sync to ensure data is written
            rootUtil.executeCommand("sync")

            // Cleanup temp files
            rootUtil.executeCommand("rm -f $TEMP_XML_PATH $TEMP_ABX_PATH 2>/dev/null || true")

            logRepository.logInfo("SSAID", "Datei erfolgreich geschrieben")
            true
        } catch (e: Exception) {
            logRepository.logError("SSAID", "Fehler beim Schreiben: ${e.message}", exception = e)
            false
        }
    }

    // ==================== SQLite Fallback ====================

    /**
     * Sets the Android ID via SQLite database (fallback method).
     * Some devices have settings_ssaid.db alongside or instead of settings_ssaid.xml.
     */
    private suspend fun setViaSqlite(packageName: String, androidId: String): Boolean {
        return try {
            // Check if database exists
            if (!checkFileExists(SSAID_DB_PATH)) {
                logRepository.logInfo("SSAID", "SQLite-Datenbank nicht gefunden: $SSAID_DB_PATH")
                return false
            }

            logRepository.logInfo("SSAID", "Versuche SSAID ueber SQLite zu setzen...")

            // Check if sqlite3 is available
            val sqliteCheck = rootUtil.executeCommand("which sqlite3")
            if (sqliteCheck.isFailure || sqliteCheck.getOrNull().isNullOrBlank()) {
                logRepository.logWarning("SSAID", "sqlite3 Tool nicht verfuegbar")
                return false
            }

            val lowercaseId = androidId.lowercase()

            // Try to update existing entry first
            val updateResult = rootUtil.executeCommand(
                """sqlite3 $SSAID_DB_PATH "UPDATE ssaid SET value='$lowercaseId' WHERE name='$packageName';" """
            )

            if (updateResult.isSuccess) {
                // Check if any rows were affected
                val checkResult = rootUtil.executeCommand(
                    """sqlite3 $SSAID_DB_PATH "SELECT value FROM ssaid WHERE name='$packageName';" """
                )

                if (checkResult.isSuccess && checkResult.getOrNull()?.trim() == lowercaseId) {
                    logRepository.logInfo("SSAID", "SQLite UPDATE erfolgreich")
                    return true
                }

                // Entry doesn't exist, try INSERT
                logRepository.logInfo("SSAID", "Eintrag nicht gefunden, versuche INSERT...")

                // Get next ID
                val maxIdResult = rootUtil.executeCommand(
                    """sqlite3 $SSAID_DB_PATH "SELECT COALESCE(MAX(_id), 0) + 1 FROM ssaid;" """
                )
                val nextId = maxIdResult.getOrNull()?.trim()?.toIntOrNull() ?: 1

                val insertResult = rootUtil.executeCommand(
                    """sqlite3 $SSAID_DB_PATH "INSERT INTO ssaid (_id, name, value, package, defaultValue, defaultSysSet) VALUES ($nextId, '$packageName', '$lowercaseId', '$packageName', '$lowercaseId', 1);" """
                )

                if (insertResult.isSuccess) {
                    // Verify insert
                    val verifyResult = rootUtil.executeCommand(
                        """sqlite3 $SSAID_DB_PATH "SELECT value FROM ssaid WHERE name='$packageName';" """
                    )

                    if (verifyResult.isSuccess && verifyResult.getOrNull()?.trim() == lowercaseId) {
                        logRepository.logInfo("SSAID", "SQLite INSERT erfolgreich")
                        return true
                    }
                }
            }

            logRepository.logError("SSAID", "SQLite-Fallback fehlgeschlagen")
            false
        } catch (e: Exception) {
            logRepository.logError("SSAID", "SQLite-Fehler: ${e.message}", exception = e)
            false
        }
    }

    // ==================== XML Generation ====================

    /**
     * Creates a new settings_ssaid.xml with a single package entry.
     *
     * Uses abx2xml-compatible format:
     * - version="-1"
     * - name: numeric UID (starting at 10000 for user apps)
     * - defaultSysSet="false" for user apps
     * - tag="null"
     * - 2-space indentation
     */
    private fun createNewSsaidXml(packageName: String, androidId: String): String {
        return """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<settings version="-1">
  <setting id="1" name="10000" value="${androidId.lowercase()}" package="$packageName" defaultValue="${androidId.lowercase()}" defaultSysSet="false" tag="null" />
</settings>
"""
    }

    /**
     * Updates existing settings_ssaid.xml with a new or updated package entry.
     *
     * Note: In abx2xml output, the XML structure uses:
     * - `name` attribute: numeric UID (e.g., "10487")
     * - `package` attribute: actual package name (e.g., "com.scopely.monopolygo")
     *
     * We search by `package` attribute to find the correct entry.
     */
    private suspend fun updateSsaidXml(currentContent: String, packageName: String, androidId: String): String? {
        return try {
            val doc = parseXml(currentContent) ?: return null
            val settingsElement = doc.documentElement
            val settingNodes = settingsElement.getElementsByTagName("setting")

            var packageFound = false
            var maxId = 0
            var maxUid = 0  // For name attribute (numeric UID)

            // Find package entry and track max ID + max UID
            for (i in 0 until settingNodes.length) {
                val settingElement = settingNodes.item(i) as Element
                val id = settingElement.getAttribute("id").toIntOrNull() ?: 0
                val uid = settingElement.getAttribute("name").toIntOrNull() ?: 0
                maxId = maxOf(maxId, id)
                maxUid = maxOf(maxUid, uid)

                // FIX: Search by package attribute instead of name
                val pkg = settingElement.getAttribute("package")

                if (pkg == packageName) {
                    // Update existing entry
                    settingElement.setAttribute("value", androidId.lowercase())
                    settingElement.setAttribute("defaultValue", androidId.lowercase())
                    packageFound = true
                    logRepository.logInfo("SSAID", "Existierender Eintrag aktualisiert (id=$id, uid=$uid)")
                }
            }

            // If package not found, add new entry
            if (!packageFound) {
                val newId = maxId + 1
                val newUid = maxUid + 1
                val newSetting = doc.createElement("setting")
                newSetting.setAttribute("id", newId.toString())
                newSetting.setAttribute("name", newUid.toString())  // Numeric UID
                newSetting.setAttribute("value", androidId.lowercase())
                newSetting.setAttribute("package", packageName)
                newSetting.setAttribute("defaultValue", androidId.lowercase())
                newSetting.setAttribute("defaultSysSet", "false")  // User apps = false
                newSetting.setAttribute("tag", "null")

                // Add with 2-space indentation (like in original abx2xml output)
                settingsElement.appendChild(doc.createTextNode("\n  "))
                settingsElement.appendChild(newSetting)
                settingsElement.appendChild(doc.createTextNode("\n"))

                logRepository.logInfo("SSAID", "Neuer Eintrag erstellt (id=$newId, uid=$newUid)")
            }

            documentToString(doc)
        } catch (e: Exception) {
            logRepository.logError("SSAID", "updateSsaidXml Exception: ${e.message}", exception = e)
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
