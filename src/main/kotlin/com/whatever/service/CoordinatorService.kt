package com.whatever.service

import co.touchlab.stately.concurrency.value
import com.whatever.IOScope
import com.whatever.factory.TelegramBot
import com.whatever.log
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

@Singleton
class CoordinatorService(
    private val telegramBot: Provider<TelegramBot>,
) {

    private val channel = Channel<String>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var lastMessage: String? = null
    private val messageIds = mutableSetOf<Long>()
    private val messageIdsMutex = Mutex()
    private val lastMessageIdInChat = AtomicLong(1)
    private val eventsMessageId = AtomicLong(0)
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)

    init {
        scope.launch { processChannelItems() }
        scope.launch { periodicallySendActivities() }
    }

    private suspend fun processChannelItems() {
        for (item in channel) {
            val item = item.trim()
            val start = System.currentTimeMillis()
            log("Event: ${if (item.isNotBlank()) "\n$item\n" else ""}")

            messageIdsMutex.withLock {
                log(buildString {
                    appendLine()
                    appendLine("lastMessageIdInChat: ${lastMessageIdInChat.value}")
                    appendLine("eventsMessageId: ${eventsMessageId.value}")
                    appendLine("messageIds: ${messageIds}")
                    appendLine("lastMessage: ${lastMessage?.let { if (it.length > 20) it.take(10) + "..." + it.takeLast(10) else it}}")
                })
                if (itemShouldBeProcessed(item)) {
                    handleItem(item)
                }
            }
            delay(max(1000 - (System.currentTimeMillis() - start), 1))

        }
    }


    private suspend fun periodicallySendActivities() {
        while (true) {
            delay(60 * 1000)
            if (lastMessageIdInChat.value != eventsMessageId.value) {
                scope.launch {
                    lastMessage?.let { sendActivities(it) }
                }
            }

        }
    }

    private fun itemShouldBeProcessed(item: String): Boolean {
        return item != lastMessage || (lastMessageIdInChat.value != eventsMessageId.value)
    }

    private suspend fun handleItem(item: String) {
        if (item.isNotBlank()) {
            processNonBlankItem(item)
        } else {
            processBlankItem()
        }
    }

    private suspend fun processNonBlankItem(item: String) {
        if (lastMessageIdInChat.value != eventsMessageId.value) {
            deleteCacheMessages()
            val newMessageId = telegramBot.get().sendToChat(item).get().messageId
            updateMessageIds(newMessageId)
            eventsMessageId.value = newMessageId
        } else {
            telegramBot.get().editInChat(item, eventsMessageId.value)
        }
        lastMessage = item
    }

    private suspend fun processBlankItem() {
        deleteCacheMessages()
        lastMessage = ""
        eventsMessageId.value = 0
    }

    private fun updateMessageIds(newMessageId: Long) {
        lastMessageIdInChat.value = newMessageId
        messageIds.add(newMessageId)
    }

    suspend fun sendActivities(string: String) {
        channel.send(string)
    }

    private suspend fun deleteCacheMessages() {
        val toDelete = messageIds.toSet()
        messageIds.clear()
        IOScope.launch {
            toDelete.forEach { telegramBot.get().deleteInTargetChat(it) }
        }
    }

    fun setLastMessageIdInChat(value: Long) {
        lastMessageIdInChat.value = value
    }
}
