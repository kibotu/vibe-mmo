package net.kibotu.ro.client.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String,
    val tag: String? = null
) {
    fun format(): String {
        val tagStr = tag?.let { "[$it] " } ?: ""
        return "[$timestamp] ${level.name} $tagStr$message"
    }
}

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

object ClientLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private const val MAX_LOGS = 1000
    
    private fun getTimestamp(): String {
        val now = System.currentTimeMillis()
        val seconds = now / 1000
        val millis = now % 1000
        val totalSeconds = seconds % 86400
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, secs, millis)
    }
    
    private fun addLog(level: LogLevel, message: String, tag: String? = null) {
        val entry = LogEntry(
            timestamp = getTimestamp(),
            level = level,
            message = message,
            tag = tag
        )
        _logs.value = (_logs.value + entry).takeLast(MAX_LOGS)
    }
    
    fun debug(message: String, tag: String? = null) {
        addLog(LogLevel.DEBUG, message, tag)
    }
    
    fun info(message: String, tag: String? = null) {
        addLog(LogLevel.INFO, message, tag)
    }
    
    fun warn(message: String, tag: String? = null) {
        addLog(LogLevel.WARN, message, tag)
    }
    
    fun error(message: String, tag: String? = null) {
        addLog(LogLevel.ERROR, message, tag)
    }
    
    fun clear() {
        _logs.value = emptyList()
    }
}

