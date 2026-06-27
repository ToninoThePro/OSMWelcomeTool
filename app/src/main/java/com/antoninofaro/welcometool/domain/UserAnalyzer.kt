package com.antoninofaro.welcometool.domain

import androidx.compose.runtime.Immutable
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.OsmUser
import timber.log.Timber
import java.time.Instant

@Immutable
data class UserAnalysis(
    val isNewcomer: Boolean,
    val isReturning: Boolean,
    val isPowerUser: Boolean,
    val totalEdits: Int,
    val firstChangesetDate: String?,
    val lastActiveDate: String?,
    val isWelcomed: Boolean = false,
    val osmchaLikes: Int,
    val osmchaDislikes: Int,
    val accountAge: Long,
)

object UserAnalyzer {

    private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    private const val NEWCOMER_DAYS_MS = 60L * ONE_DAY_MS
    private const val RETURNING_DAYS_MS = 365L * ONE_DAY_MS
    private const val POWER_USER_EDITS = 1000
    private const val RETURNING_EDITS_MAX = 300

    fun analyze(
        user: OsmUser,
        userChangesets: List<OsmChangeset>,
        recentChangeset: OsmChangeset?,
        osmchaLikes: Int = 0,
        osmchaDislikes: Int = 0,
        isWelcomed: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): UserAnalysis {
        val accountCreated = parseIsoDateToMillis(user.accountCreated) ?: now
        val accountAge = now - accountCreated
        val totalEdits = user.changesets?.count ?: 0

        return UserAnalysis(
            isNewcomer = accountAge < NEWCOMER_DAYS_MS,
            isReturning = accountAge > RETURNING_DAYS_MS && totalEdits < RETURNING_EDITS_MAX,
            isPowerUser = totalEdits > POWER_USER_EDITS,
            totalEdits = totalEdits,
            firstChangesetDate = userChangesets.lastOrNull()?.createdAt,
            lastActiveDate = recentChangeset?.createdAt ?: userChangesets.firstOrNull()?.createdAt,
            osmchaLikes = osmchaLikes,
            osmchaDislikes = osmchaDislikes,
            isWelcomed = isWelcomed,
            accountAge = accountAge
        )
    }

    private fun parseIsoDateToMillis(dateString: String): Long? = try {
        Instant.parse(dateString).toEpochMilli()
    } catch (e: Exception) {
        Timber.w(e, "Failed to parse ISO date with Instant: $dateString")
        null
    }
}
