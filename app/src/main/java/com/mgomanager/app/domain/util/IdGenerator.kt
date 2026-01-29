package com.mgomanager.app.domain.util

import java.util.UUID
import kotlin.random.Random

/**
 * Utility object for generating unique IDs for new Monopoly GO accounts.
 * These IDs are used by the LSPosed hooks to spoof device identifiers.
 */
object IdGenerator {

    /**
     * Generate Device Name: "{AccountName}'s Device"
     */
    fun generateDeviceName(accountName: String): String {
        return "$accountName's Device"
    }

    /**
     * Generate Android ID (16-digit lowercase hex string).
     * Format: 16 hexadecimal characters (e.g., "abcd123456789012")
     */
    fun generateAndroidId(): String {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .take(16)
            .lowercase()
    }

    /**
     * Generate App Set ID (UUID format).
     * Format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     */
    fun generateAppSetId(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Generate GSF ID (16-digit hex, 64-bit).
     * Google Services Framework ID.
     */
    fun generateGsfId(): String {
        val randomLong = Random.nextLong()
        return randomLong.toULong().toString(16).padStart(16, '0').takeLast(16)
    }

    /**
     * Generate GAID (Google Advertising ID, UUID format).
     * Format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     */
    fun generateGaid(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Validate Android ID format (16 hex characters).
     */
    fun isValidAndroidId(androidId: String): Boolean {
        return androidId.matches(Regex("^[a-fA-F0-9]{16}$"))
    }

    /**
     * Validate UUID format.
     */
    fun isValidUuid(uuid: String): Boolean {
        return try {
            UUID.fromString(uuid)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Validate GSF ID format (16 hex characters).
     */
    fun isValidGsfId(gsfId: String): Boolean {
        return gsfId.matches(Regex("^[a-fA-F0-9]{16}$"))
    }

    /**
     * Generate all IDs for a new account at once.
     * Returns a data class containing all generated IDs.
     */
    fun generateAllIds(accountName: String): GeneratedIds {
        return GeneratedIds(
            deviceName = generateDeviceName(accountName),
            androidId = generateAndroidId(),
            appSetIdApp = generateAppSetId(),
            appSetIdDev = generateAppSetId(),
            gsfId = generateGsfId(),
            gaid = generateGaid()
        )
    }
}

/**
 * Data class containing all generated IDs for a new account.
 */
data class GeneratedIds(
    val deviceName: String,
    val androidId: String,
    val appSetIdApp: String,
    val appSetIdDev: String,
    val gsfId: String,
    val gaid: String
)
