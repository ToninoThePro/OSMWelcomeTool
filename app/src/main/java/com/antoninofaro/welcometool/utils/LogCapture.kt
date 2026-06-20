package com.antoninofaro.welcometool.utils

import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class LogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

/**
 * LogCapture è un Tree di Timber che cattura tutti i log in memoria.
 * Utile per debug e visualizzazione in-app dei log.
 */
@Singleton class LogCaptureTree @Inject constructor() : Timber.Tree() {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val maxLogs = 500
    private var captureEnabled = false
    private val ringBuffer = ArrayDeque<LogEntry>(maxLogs) // ponytail: ring buffer, O(1) append/trim instead of O(n) list copy

    fun setEnabled(enabled: Boolean) {
        captureEnabled = enabled
        if (!enabled) {
            _logs.value = emptyList()
        }
    }

    @Synchronized
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!captureEnabled) return

        val levelName = when (priority) {
            android.util.Log.VERBOSE -> "V"
            android.util.Log.DEBUG -> "D"
            android.util.Log.INFO -> "I"
            android.util.Log.WARN -> "W"
            android.util.Log.ERROR -> "E"
            android.util.Log.ASSERT -> "A"
            else -> "?"
        }

        val timestamp = dateFormat.format(Date())
        val entry = LogEntry(
            timestamp = timestamp,
            level = levelName,
            tag = tag ?: "NO_TAG",
            message = message,
            throwable = t
        )

        ringBuffer.addLast(entry)
        if (ringBuffer.size > maxLogs) ringBuffer.removeFirst()
        _logs.value = ringBuffer.toList()
    }
}
