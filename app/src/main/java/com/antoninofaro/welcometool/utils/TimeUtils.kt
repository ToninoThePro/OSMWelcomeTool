package com.antoninofaro.welcometool.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatRelativeTime(
    timestamp: Long,
    now: String,
    minAgo: String,
    hourAgo: String,
    datePattern: String = "dd/MM/yyyy"
): String {
    val diff = System.currentTimeMillis() - timestamp
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
