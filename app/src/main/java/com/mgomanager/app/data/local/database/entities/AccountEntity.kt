package com.mgomanager.app.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.SusLevel

/**
 * Room entity for storing account data
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // User-defined
    val accountName: String,
    val prefix: String = "",

    // Timestamps (Unix milliseconds)
    val createdAt: Long,
    val lastPlayedAt: Long,

    // Extracted IDs
    val userId: String,
    val gaid: String = "nicht vorhanden",
    val deviceToken: String = "nicht vorhanden",
    val appSetId: String = "nicht vorhanden",
    val ssaid: String = "nicht vorhanden",

    // Status flags (stored as Int and Boolean for Room)
    val susLevelValue: Int = 0, // 0, 3, 7, 99
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
)

/**
 * Extension functions for conversion between Entity and Domain Model
 */
fun AccountEntity.toDomain(): Account {
    return Account(
        id = id,
        accountName = accountName,
        prefix = prefix,
        createdAt = createdAt,
        lastPlayedAt = lastPlayedAt,
        userId = userId,
        gaid = gaid,
        deviceToken = deviceToken,
        appSetId = appSetId,
        ssaid = ssaid,
        susLevel = SusLevel.fromValue(susLevelValue),
        hasError = hasError,
        hasFacebookLink = hasFacebookLink,
        fbUsername = fbUsername,
        fbPassword = fbPassword,
        fb2FA = fb2FA,
        fbTempMail = fbTempMail,
        backupPath = backupPath,
        fileOwner = fileOwner,
        fileGroup = fileGroup,
        filePermissions = filePermissions,
        isLastRestored = isLastRestored
    )
}

fun Account.toEntity(): AccountEntity {
    return AccountEntity(
        id = id,
        accountName = accountName,
        prefix = prefix,
        createdAt = createdAt,
        lastPlayedAt = lastPlayedAt,
        userId = userId,
        gaid = gaid,
        deviceToken = deviceToken,
        appSetId = appSetId,
        ssaid = ssaid,
        susLevelValue = susLevel.value,
        hasError = hasError,
        hasFacebookLink = hasFacebookLink,
        fbUsername = fbUsername,
        fbPassword = fbPassword,
        fb2FA = fb2FA,
        fbTempMail = fbTempMail,
        backupPath = backupPath,
        fileOwner = fileOwner,
        fileGroup = fileGroup,
        filePermissions = filePermissions,
        isLastRestored = isLastRestored
    )
}
