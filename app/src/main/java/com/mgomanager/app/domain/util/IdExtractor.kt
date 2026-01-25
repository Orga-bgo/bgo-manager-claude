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
     * Extract SSAID from settings_ssaid.xml
     * The file is in Android Binary XML format (ABX2).
     * Pattern in binary: /<16-hex-ssaid>/com.scopely.monopolygo/<16-hex-ssaid>
     * We look for the SSAID that appears before or after the package name.
     */
    fun extractSsaid(xmlFile: File): String {
        return try {
            // Read file as bytes and convert to string, replacing non-printable chars
            val bytes = xmlFile.readBytes()
            val content = bytes.map { byte ->
                val char = byte.toInt().and(0xFF).toChar()
                if (char.isLetterOrDigit() || char in "/.:-_") char else ' '
            }.joinToString("")

            // Try multiple patterns to find the SSAID

            // Pattern 1: SSAID after package name (com.scopely.monopolygo/SSAID)
            val patternAfter = Regex("""com\.scopely\.monopolygo/([a-f0-9]{16})""")
            val matchAfter = patternAfter.find(content)
            if (matchAfter != null) {
                return matchAfter.groupValues[1]
            }

            // Pattern 2: SSAID before package name (/SSAID/com.scopely.monopolygo)
            val patternBefore = Regex("""/([a-f0-9]{16})/com\.scopely\.monopolygo""")
            val matchBefore = patternBefore.find(content)
            if (matchBefore != null) {
                return matchBefore.groupValues[1]
            }

            // Pattern 3: Just find any 16-hex string near "monopolygo"
            val indexOfMonopoly = content.indexOf("com.scopely.monopolygo")
            if (indexOfMonopoly != -1) {
                // Search in a window around the package name
                val windowStart = maxOf(0, indexOfMonopoly - 50)
                val windowEnd = minOf(content.length, indexOfMonopoly + 80)
                val window = content.substring(windowStart, windowEnd)

                val hexPattern = Regex("""([a-f0-9]{16})""")
                val hexMatch = hexPattern.find(window)
                if (hexMatch != null) {
                    return hexMatch.groupValues[1]
                }
            }

            "nicht vorhanden"
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
