package com.whatever

import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Long.toChatId(): ChatId.Id {
    return ChatId.fromId(this)
}

fun String.toChatId(): ChatId.Id {
    return ChatId.fromId(this.toLong())
}

fun <T> List<T>.containsOneOf(prefixes: List<T>): Boolean {
    return prefixes.any { this.contains(it) }
}

fun log(message: Any?) {
    println(LocalDateTime.now().format(formatter) + ": " + message)
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