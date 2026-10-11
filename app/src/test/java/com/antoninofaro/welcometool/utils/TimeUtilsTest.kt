package com.antoninofaro.welcometool.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class TimeUtilsTest {

    private val nowStr = "adesso"
    private val minAgoStr = "%d minuti fa"
    private val hourAgoStr = "%d ore fa"
    private val datePattern = "dd/MM/yyyy"

    @Test
    fun `formatRelativeTime returns 'now' for less than 1 minute`() {
        val currentTime = 10000000L
        val timestamp = currentTime - 30_000 // 30 seconds ago

        val result = formatRelativeTime(
            timestamp = timestamp,
            now = nowStr,
            minAgo = minAgoStr,
            hourAgo = hourAgoStr,
            datePattern = datePattern,
            currentTimeMillis = currentTime
        )

        assertThat(result).isEqualTo(nowStr)
    }

    @Test
    fun `formatRelativeTime returns minAgo for between 1 minute and 1 hour`() {
        val currentTime = 10000000L
        val timestamp = currentTime - 180_000 // 3 minutes ago

        val result = formatRelativeTime(
            timestamp = timestamp,
            now = nowStr,
            minAgo = minAgoStr,
            hourAgo = hourAgoStr,
            datePattern = datePattern,
            currentTimeMillis = currentTime
        )

        assertThat(result).isEqualTo("3 minuti fa")
    }

    @Test
    fun `formatRelativeTime returns hourAgo for between 1 hour and 24 hours`() {
        val currentTime = 100000000L
        val timestamp = currentTime - (5 * 3_600_000) // 5 hours ago

        val result = formatRelativeTime(
            timestamp = timestamp,
            now = nowStr,
            minAgo = minAgoStr,
            hourAgo = hourAgoStr,
            datePattern = datePattern,
            currentTimeMillis = currentTime
        )

        assertThat(result).isEqualTo("5 ore fa")
    }

    @Test
    fun `formatRelativeTime returns formatted date for more than 24 hours`() {
        // Set the default locale for deterministic testing on this specific test
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.ITALY)

        try {
            // Using a known timestamp: Jan 1, 2024, 12:00:00 PM UTC = 1704110400000
            val timestamp = 1704110400000L
            // Current time 3 days later
            val currentTime = timestamp + (3 * 86_400_000)

            val result = formatRelativeTime(
                timestamp = timestamp,
                now = nowStr,
                minAgo = minAgoStr,
                hourAgo = hourAgoStr,
                datePattern = datePattern,
                currentTimeMillis = currentTime
            )

            // For Locale.ITALY, the fallback date format should match the datePattern "dd/MM/yyyy"
            assertThat(result).isEqualTo("01/01/2024")
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
