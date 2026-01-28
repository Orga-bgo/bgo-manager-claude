package com.mgomanager.app.data.model

import java.text.SimpleDateFormat
import java.util.*

/**
 * Domain model for MGO account
 * This is the business logic representation (not database entity)
 */
data class Account(
    val id: Long = 0,
    val accountName: String,
    val prefix: String = "",
    val createdAt: Long,
    val lastPlayedAt: Long,

    // Extracted IDs
    val userId: String,
    val gaid: String = "nicht vorhanden",
    val deviceToken: String = "nicht vorhanden",
    val appSetId: String = "nicht vorhanden",
    val ssaid: String = "nicht vorhanden",

    // Status flags
    val susLevel: SusLevel = SusLevel.NONE,
    val hasError: Boolean = false,

    // Facebook data
    val hasFacebookLink: Boolean = false,
    val fbUsername: String? = null,
    val fbPassword: String? = null,
    val fb2FA: String? = null,
    val fbTempMail: String? = null,

    // File system metadata
    val backupPath: String,
    val fileOwner: String,
    val fileGroup: String,
    val filePermissions: String,

    // Xposed Hook: Marks the last restored account for App Set ID replacement
    val isLastRestored: Boolean = false
) {
    val fullName: String
        get() = if (prefix.isNotEmpty()) "$prefix$accountName" else accountName

    val shortUserId: String
        get() = if (userId.length > 4) "...${userId.takeLast(4)}" else userId

    fun getFormattedCreatedAt(): String {
        return formatTimestamp(createdAt)
    }

    fun getFormattedLastPlayedAt(): String {
        return formatTimestamp(lastPlayedAt)
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN)
        return sdf.format(Date(timestamp))
    }

    /**
     * Get border color based on status
     * Priority: Error > Sus Level
     */
    fun getBorderColor(): androidx.compose.ui.graphics.Color {
        return if (hasError) {
            com.mgomanager.app.ui.theme.StatusRed
        } else {
            susLevel.getColor()
        }
    }
}
