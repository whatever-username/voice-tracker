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
            var item = item.trim()
            val start = System.currentTimeMillis()
            log("Event: ${if (item.isNotEmpty()) "\n$item\n" else ""}")

            messageIdsMutex.withLock {
                log(buildString {
                    appendLine()
                    appendLine("lastMessageIdInChat: ${lastMessageIdInChat.get()}")
                    appendLine("eventsMessageId: ${eventsMessageId.get()}")
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
            if (lastMessageIdInChat.get() != eventsMessageId.get()) {
                scope.launch {
                    lastMessage?.let { sendActivities(it) }
                }
            }

        }
    }

    private fun itemShouldBeProcessed(item: String): Boolean {
        return item != lastMessage || (lastMessageIdInChat.get() != eventsMessageId.get())
    }

    private suspend fun handleItem(item: String) {
        if (item.isNotEmpty()) {
            processNonBlankItem(item)
        } else {
            processBlankItem()
        }
    }

    private fun processNonBlankItem(item: String) {
        log("Processing non-blank item")
        log("lastMessageIdInChat: ${lastMessageIdInChat.get()}, eventsMessageId: ${eventsMessageId.get()}")
        if (lastMessageIdInChat.get() != eventsMessageId.get()) {
            log("sending new message, deleting $messageIds")
            deleteCacheMessages()
            val newMessageId = telegramBot.get().sendToChat(item).get().messageId
            updateMessageIds(newMessageId)
        } else {
            log("editing message ${eventsMessageId.get()}")
            telegramBot.get().editInChat(item, eventsMessageId.get())
        }
        lastMessage = item
    }

    private fun processBlankItem() {
        log("Processing blank item")
        deleteCacheMessages()
        lastMessage = ""
        eventsMessageId.set(0)
    }

    private fun updateMessageIds(newMessageId: Long) {
        lastMessageIdInChat.set(newMessageId)
        eventsMessageId.set(newMessageId)
        messageIds.add(newMessageId)
    }

    suspend fun sendActivities(string: String) {
        channel.send(string)
    }

    private fun deleteCacheMessages() {
        val toDelete = HashSet(messageIds)
        messageIds.clear()
        IOScope.launch {
            log("Deleting messages: $toDelete")
            toDelete.forEach { telegramBot.get().deleteInTargetChat(it).let {
                if (it.isError){
                    log("Error on deleting message")
                }
            } }
        }
    }

    suspend fun setLastMessageIdInChat(value: Long) {
        messageIdsMutex.withLock {
            lastMessageIdInChat.set(value)
            log("lastMessageIdInChat: $value")
        }

    }
}
