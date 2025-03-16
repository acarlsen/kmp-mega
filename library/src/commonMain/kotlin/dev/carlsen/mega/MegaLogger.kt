package dev.carlsen.mega

class MegaLogger {
    private val listeners = mutableListOf<MegaLogListener>()
    var minLogLevel = MegaLogLevel.INFO

    fun addListener(listener: MegaLogListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: MegaLogListener) {
        listeners.remove(listener)
    }

    fun log(level: MegaLogLevel, message: String, throwable: Throwable? = null) {
        if (level.ordinal < minLogLevel.ordinal) return

        if (listeners.isEmpty()) {
            // Default implementation when no listeners are registered
            val prefix = when (level) {
                MegaLogLevel.VERBOSE -> "V"
                MegaLogLevel.DEBUG -> "D"
                MegaLogLevel.INFO -> "I"
                MegaLogLevel.WARNING -> "W"
                MegaLogLevel.ERROR -> "E"
            }
            val logMsg = "[$prefix] $message"
            println(logMsg)
            throwable?.printStackTrace()
        } else {
            listeners.forEach { it.onLogMessage(level, message, throwable) }
        }
    }

    fun v(message: String) = log(MegaLogLevel.VERBOSE, message)
    fun d(message: String) = log(MegaLogLevel.DEBUG, message)
    fun i(message: String) = log(MegaLogLevel.INFO, message)
    fun w(message: String, throwable: Throwable? = null) = log(MegaLogLevel.WARNING, message, throwable)
    fun e(message: String, throwable: Throwable? = null) = log(MegaLogLevel.ERROR, message, throwable)
}

enum class MegaLogLevel {
    VERBOSE, DEBUG, INFO, WARNING, ERROR
}

interface MegaLogListener {
    fun onLogMessage(level: MegaLogLevel, message: String, throwable: Throwable? = null)
}