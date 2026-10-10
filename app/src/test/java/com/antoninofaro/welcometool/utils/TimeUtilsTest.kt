package com.antoninofaro.welcometool.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeUtilsTest {

    private val fixedNow = 1_700_000_000_000L // Fixed reference time for deterministic tests

    @Test
    fun `formatRelativeTime returns now string when less than 1 minute ago`() {
        val timestamp = fixedNow - 30_000 // 30 seconds ago
        val result = formatRelativeTime(timestamp, "now", "min ago", "hour ago", currentTimeMillis = fixedNow)
        assertThat(result).isEqualTo("now")
    }

    @Test
    fun `formatRelativeTime returns minAgo string when between 1 minute and 1 hour ago`() {
        val timestamp = fixedNow - 2 * 60_000 // 2 minutes ago
        val result = formatRelativeTime(timestamp, "now", "%d min ago", "hour ago", currentTimeMillis = fixedNow)
        assertThat(result).isEqualTo("2 min ago")
    }

    @Test
    fun `formatRelativeTime returns hourAgo string when between 1 hour and 24 hours ago`() {
        val timestamp = fixedNow - 3 * 3_600_000 // 3 hours ago
        val result = formatRelativeTime(timestamp, "now", "min ago", "%d hours ago", currentTimeMillis = fixedNow)
        assertThat(result).isEqualTo("3 hours ago")
    }

    @Test
    fun `formatRelativeTime returns formatted date when more than 24 hours ago`() {
        val timestamp = fixedNow - 2 * 86_400_000 // 2 days ago
        val result = formatRelativeTime(timestamp, "now", "min ago", "hour ago", "yyyy-MM-dd", currentTimeMillis = fixedNow)
        // Date format depends on locale, but with fixed timestamp we can verify format
        assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}")
    }

    @Test
    fun `formatRelativeTime handles exactly 1 minute boundary`() {
        val timestamp = fixedNow - 60_000 // exactly 1 minute ago
        val result = formatRelativeTime(timestamp, "now", "%d min ago", "hour ago", currentTimeMillis = fixedNow)
        assertThat(result).isEqualTo("1 min ago")
    }

    @Test
    fun `formatRelativeTime handles exactly 1 hour boundary`() {
        val timestamp = fixedNow - 3_600_000 // exactly 1 hour ago
        val result = formatRelativeTime(timestamp, "now", "min ago", "%d hours ago", currentTimeMillis = fixedNow)
        assertThat(result).isEqualTo("1 hours ago")
    }

    @Test
    fun `formatRelativeTime handles exactly 24 hours boundary`() {
        val timestamp = fixedNow - 86_400_000 // exactly 24 hours ago
        val result = formatRelativeTime(timestamp, "now", "min ago", "hour ago", "yyyy-MM-dd", currentTimeMillis = fixedNow)
        assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}")
    }

    @Test
    fun `formatRelativeTime uses default date pattern when not specified`() {
        val timestamp = fixedNow - 2 * 86_400_000 // 2 days ago
        val result = formatRelativeTime(timestamp, "now", "min ago", "hour ago", currentTimeMillis = fixedNow)
        // Default pattern is "dd/MM/yyyy"
        assertThat(result).matches("\\d{2}/\\d{2}/\\d{4}")
    }

    @Test
    fun `formatRelativeTime works with zero timestamp`() {
        val timestamp = 0L // Unix epoch
        val result = formatRelativeTime(timestamp, "now", "min ago", "hour ago", currentTimeMillis = fixedNow)
        assertThat(result).matches("\\d{2}/\\d{2}/\\d{4}")
    }
}