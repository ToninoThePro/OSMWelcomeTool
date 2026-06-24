package com.antoninofaro.welcometool.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: Long,
    @ColumnInfo(index = true)
    val displayName: String,
    val accountCreated: String,
    val description: String?,
    val accountAge: Long,
    val isNewcomer: Boolean,
    val isReturning: Boolean,
    val isPowerUser: Boolean,
    val totalEdits: Int,
    val firstChangesetDate: String?,
    val lastActiveDate: String?,
    val osmchaLikes: Int,
    val osmchaDislikes: Int,
    val osmchaLastChecked: Long = 0L,
    val isWelcomed: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    val imgUrl: String? = null
)
