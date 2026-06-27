package com.antoninofaro.welcometool.domain

import com.antoninofaro.welcometool.data.model.CountWrapper
import com.antoninofaro.welcometool.data.model.OsmUser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class UserAnalyzerTest {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun createMockUser(accountCreated: String, totalEdits: Int): OsmUser {
        return OsmUser(
            id = 1L,
            displayName = "Test User",
            accountCreated = accountCreated,
            description = null,
            img = null,
            roles = null,
            changesets = CountWrapper(totalEdits),
            traces = null
        )
    }

    @Test
    fun `isReturning should be true when account is older than 365 days and edits are less than 300`() {
        val now = dateFormat.parse("2024-01-01T00:00:00Z")!!.time
        val accountCreated = "2022-12-31T23:59:59Z" // More than 365 days ago
        val user = createMockUser(accountCreated, 299)

        val analysis = UserAnalyzer.analyze(user, emptyList(), null, now = now)

        assertEquals(true, analysis.isReturning)
    }

    @Test
    fun `isReturning should be false when account is exactly 365 days old and edits are less than 300`() {
        val now = dateFormat.parse("2024-01-01T00:00:00Z")!!.time
        val accountCreated = "2023-01-01T00:00:00Z" // Exactly 365 days ago
        val user = createMockUser(accountCreated, 299)

        val analysis = UserAnalyzer.analyze(user, emptyList(), null, now = now)

        assertEquals(false, analysis.isReturning)
    }

    @Test
    fun `isReturning should be false when account is old but edits are high`() {
        val now = dateFormat.parse("2024-01-01T00:00:00Z")!!.time
        val accountCreated = "2020-01-01T00:00:00Z"
        val user = createMockUser(accountCreated, 1000)

        val analysis = UserAnalyzer.analyze(user, emptyList(), null, now = now)

        assertEquals(false, analysis.isReturning)
    }

    @Test
    fun `isReturning should be false when account is new and edits are low`() {
        val now = dateFormat.parse("2024-01-01T00:00:00Z")!!.time
        val accountCreated = "2023-12-31T00:00:00Z"
        val user = createMockUser(accountCreated, 1)

        val analysis = UserAnalyzer.analyze(user, emptyList(), null, now = now)

        assertEquals(false, analysis.isReturning)
    }

    @Test
    fun `isReturning should be true when account is 365 days and 1 second old and edits are less than 300`() {
        val now = dateFormat.parse("2024-01-01T00:00:01Z")!!.time
        val accountCreated = "2023-01-01T00:00:00Z" // 365 days and 1 second ago
        val user = createMockUser(accountCreated, 299)

        val analysis = UserAnalyzer.analyze(user, emptyList(), null, now = now)

        assertEquals(true, analysis.isReturning)
    }

    @Test
    fun `isReturning should be false when edits are exactly 300`() {
        val now = dateFormat.parse("2024-01-01T00:00:01Z")!!.time
        val accountCreated = "2022-01-01T00:00:00Z" // Well over 365 days
        val user = createMockUser(accountCreated, 300)

        val analysis = UserAnalyzer.analyze(user, emptyList(), null, now = now)

        assertEquals(false, analysis.isReturning)
    }

    @Test
    fun `isReturning should be false when account is less than 365 days old`() {
        val now = dateFormat.parse("2024-01-01T00:00:00Z")!!.time
        val accountCreated = "2023-06-01T00:00:00Z" // ~6 months ago
        val user = createMockUser(accountCreated, 10)

        val analysis = UserAnalyzer.analyze(user, emptyList(), null, now = now)

        assertEquals(false, analysis.isReturning)
    }

    @Test
    fun `isWelcomed should be respected when passed to analyze`() {
        val user = createMockUser("2024-01-01T00:00:00Z", 0)
        val analysis = UserAnalyzer.analyze(user, emptyList(), null, isWelcomed = true)
        assertEquals(true, analysis.isWelcomed)

        val analysisNotWelcomed = UserAnalyzer.analyze(user, emptyList(), null, isWelcomed = false)
        assertEquals(false, analysisNotWelcomed.isWelcomed)
    }
}
