package com.whatever

import com.github.kotlintelegrambot.entities.ChatId
import com.whatever.factory.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.format.DateTimeFormatter

inline fun <reified T> T.logger(): Logger {
    if (T::class.isCompanion) {
        return LoggerFactory.getLogger(T::class.java.enclosingClass)
    }
    return LoggerFactory.getLogger(T::class.java)
}
inline fun <reified T> T.logDebug(message: Any?) {
    logger().debug(message.toString())
}
inline fun <reified T> T.logInfo(message: Any?) {
    logger().info(message.toString())

}

inline fun <reified T> T.logError(ex: Throwable, bot: TelegramBot? = null) {
    val logger = logger()
    ex.stackTraceToString().let {
        logger.error(it)
        bot?.sendToMainAdmin(if (it.length > 1000) it.substring(0, 1000) else it)

    }
}
inline fun <reified T> T.logError(ex: String, bot: TelegramBot? = null) {
    val logger = logger()
    ex.let {
        logger.error("\n> $it")
        bot?.sendToMainAdmin(if (it.length > 1000) it.substring(0, 1000) else it)

    }
}

fun Long.toChatId(): ChatId.Id {
    return ChatId.fromId(this)
}

fun String.toChatId(): ChatId.Id {
    return ChatId.fromId(this.toLong())
}

fun <T> List<T>.containsOneOf(prefixes: List<T>): Boolean {
    return prefixes.any { this.contains(it) }
}


val IOScope = CoroutineScope(Dispatchers.IO)
val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

class RateLimiter(private val rate: Long) {
    private val mutex = Mutex()
    private var lastExecutionTime = 0L

    suspend fun <T> execute(block: suspend () -> T): T {
        mutex.withLock {
            val currentTime = System.currentTimeMillis()
            val waitTime = rate - (currentTime - lastExecutionTime)
            if (waitTime > 0) {
                delay(waitTime)
            }
            val res = block()
            lastExecutionTime = System.currentTimeMillis()
            return res
        }
    }
}