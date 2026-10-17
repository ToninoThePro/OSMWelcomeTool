package com.antoninofaro.welcometool.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun parseIsoToMillis(dateStr: String?): Long {
    if (dateStr.isNullOrBlank()) return 0L
    return try {
        java.time.Instant.parse(dateStr).toEpochMilli()
    } catch (_: Exception) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}

fun formatRelativeTime(
    timestamp: Long,
    now: String,
    minAgo: String,
    hourAgo: String,
    datePattern: String = "dd/MM/yyyy",
    currentTimeMillis: Long = System.currentTimeMillis()
): String {
    val diff = currentTimeMillis - timestamp
    return when {
        diff < 60_000 -> now
        diff < 3_600_000 -> minAgo.format(diff / 60_000)
        diff < 86_400_000 -> hourAgo.format(diff / 3_600_000)
        else -> {
            val sdf = SimpleDateFormat(datePattern, Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
