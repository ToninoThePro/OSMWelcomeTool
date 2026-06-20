package com.antoninofaro.welcometool.domain

import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.OsmUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.runtime.Immutable
import timber.log.Timber

/**
 * Represents the result of analyzing an OpenStreetMap user's profile and history.
 * Contains flags classifying the user based on their activity and age.
 *
 * @property isNewcomer True if the user's account was created recently.
 * @property isReturning True if the user account is older but has few recent edits.
 * @property isPowerUser True if the user has many total edits.
 * @property totalEdits The total number of changesets made by the user.
 * @property firstChangesetDate The creation date of the user's very first changeset, if available.
 * @property lastActiveDate The creation date of the user's most recent changeset.
 * @property isWelcomed Indicates if the user has already been welcomed by the community.
 * @property osmchaLikes The number of likes the user has received on OSMCha.
 * @property osmchaDislikes The number of dislikes the user has received on OSMCha.
 * @property accountAge The age of the user's account in milliseconds.
 */
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

/**
 * Utility object responsible for applying business rules to classify OpenStreetMap users.
 */
object UserAnalyzer {

    private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    private const val NEWCOMER_DAYS_MS = 60L * ONE_DAY_MS
    private const val RETURNING_DAYS_MS = 365L * ONE_DAY_MS
    private const val POWER_USER_EDITS = 1000
    private const val RETURNING_EDITS_MAX = 300

    private val isoDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }

    /**
     * Completes an analysis of an OSM user.
     * Evaluates account age and edit count to determine if the user is a newcomer, returning, or a power user.
     *
     * @param user The user details fetched from the OSM API.
     * @param userChangesets A list of the user's changesets, used for date extraction.
     * @param recentChangeset The most recent changeset made by the user, representing their latest activity.
     * @param osmchaLikes The optional OSMCha positive reviews count.
     * @param osmchaDislikes The optional OSMCha negative reviews count.
     * @param now Current timestamp in milliseconds, used to calculate account age relative to today.
     * @return A [UserAnalysis] representing the user's classification and aggregated stats.
     */
    fun analyze(
        user: OsmUser,
        userChangesets: List<OsmChangeset>,
        recentChangeset: OsmChangeset?,
        osmchaLikes: Int = 0,
        osmchaDislikes: Int = 0,
        now: Long = System.currentTimeMillis()
    ): UserAnalysis {
        val accountCreated = parseIsoDate(user.accountCreated)?.time ?: now
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
            isWelcomed = false,
            accountAge = accountAge
        )
    }

    private fun parseIsoDate(dateString: String): Date? {
        return try {
            isoDateFormat.get()?.parse(dateString)
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse ISO date: %s", dateString)
            null
        }
    }
}
