package space.kalloware.hysera.logging

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel {
    INFO,
    WARNING,
    ERROR,
}

data class LogEntry(
    val id: Long,
    val timestampMillis: Long,
    val level: LogLevel,
    val message: String,
)

object EventLogger {
    private const val MAX_ENTRIES = 250
    private val nextId = AtomicLong(1L)
    private val mutableEntries = MutableStateFlow<List<LogEntry>>(emptyList())

    val entries = mutableEntries.asStateFlow()

    @Synchronized
    fun info(message: String) = append(LogLevel.INFO, message)

    @Synchronized
    fun warning(message: String) = append(LogLevel.WARNING, message)

    @Synchronized
    fun error(message: String) = append(LogLevel.ERROR, message)

    private fun append(level: LogLevel, message: String) {
        val entry = LogEntry(
            id = nextId.getAndIncrement(),
            timestampMillis = System.currentTimeMillis(),
            level = level,
            message = message,
        )
        mutableEntries.value = (mutableEntries.value + entry).takeLast(MAX_ENTRIES)
    }
}
