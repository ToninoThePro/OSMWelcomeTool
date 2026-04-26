package com.antoninofaro.welcometool.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class LogCaptureTree @Inject constructor() : Timber.Tree() {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val maxLogs = 500 // Mantieni gli ultimi 500 log in memoria
    private var captureEnabled = false // Flag per attivare/disattivare la cattura

    fun setEnabled(enabled: Boolean) {
        captureEnabled = enabled
        if (!enabled) {
            _logs.value = emptyList()
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Non catturare se disattivato
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

        _logs.value = (_logs.value + entry).takeLast(maxLogs)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun exportLogs(): String {
        return _logs.value.joinToString("\n") { entry ->
            "${entry.timestamp} ${entry.level}/${entry.tag}: ${entry.message}" +
                    (if (entry.throwable != null) "\n${entry.throwable.stackTraceToString()}" else "")
        }
    }
}

@Singleton
class LogCaptureManager @Inject constructor(
    private val logCaptureTree: LogCaptureTree
) {
    val logs: StateFlow<List<LogEntry>> = logCaptureTree.logs

    fun getLogCaptureTree(): Timber.Tree = logCaptureTree

    fun setEnabled(enabled: Boolean) = logCaptureTree.setEnabled(enabled)

    fun clearLogs() = logCaptureTree.clearLogs()

    fun exportLogs(): String = logCaptureTree.exportLogs()
}

