package com.whatever.service

import com.whatever.factory.TelegramBot
import com.whatever.logDebug
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

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
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val lastMessageSentTime = AtomicLong(System.currentTimeMillis())

    init {
        scope.launch { processChannelItems() }
        scope.launch { periodicallySendActivities() }
    }

    private suspend fun processChannelItems() {
        for (item in channel) {
            var item = item.trim()
            val start = System.currentTimeMillis()
            logDebug("Event: ${if (item.isNotEmpty()) "\n$item\n" else ""}")

            messageIdsMutex.withLock {
                logDebug(buildString {
                    appendLine()
                    appendLine("lastMessageIdInChat: ${lastMessageIdInChat.get()}")
                    appendLine("eventsMessageId: ${eventsMessageId.get()}")
                    appendLine("messageIds: ${messageIds}")
                    appendLine(
                        "lastMessage: ${
                            lastMessage?.let {
                                if (it.length > 20) it.take(10) + "..." + it.takeLast(
                                    10
                                ) else it
                            }
                        }"
                    )
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
            delay(60.seconds)
            logDebug("Passed from last: " + (System.currentTimeMillis() - lastMessageSentTime.get()))
            if (lastMessageIdInChat.get() != eventsMessageId.get() || isOld()) {
                scope.launch {
                    lastMessage?.let { sendActivities(it) }
                }
            }

        }
    }

    private fun itemShouldBeProcessed(item: String): Boolean {
        return isOld() || item != lastMessage || (lastMessageIdInChat.get() != eventsMessageId.get())
    }

    private suspend fun handleItem(item: String) {
        if (item.isNotEmpty()) {
            processNonBlankItem(item)
        } else {
            processBlankItem()
        }
    }

    private fun processNonBlankItem(item: String) {
        logDebug("Processing non-blank item")
        logDebug("Message:\n$item")
        logDebug("lastMessageIdInChat: ${lastMessageIdInChat.get()}, eventsMessageId: ${eventsMessageId.get()}")
        if (lastMessageIdInChat.get() != eventsMessageId.get() || isOld()) {
            logDebug("sending new message, deleting $messageIds")
            deleteCacheMessages()
            val newMessageId = telegramBot.get().sendToChat(item).get().messageId
            lastMessageSentTime.set(System.currentTimeMillis())
            updateMessageIds(newMessageId)
        } else {
            logDebug("editing message ${eventsMessageId.get()}")
            val res = telegramBot.get().editInChat(item, eventsMessageId.get())
            logDebug("Editing result: ${res.first} ${res.second}")
            if (res.second != null || res.first?.isSuccessful == false) {
                deleteCacheMessages()
                logDebug("Editing failed, handling as 'wanna be' new message")
                eventsMessageId.set(0)
                processNonBlankItem(item)
            }
        }
        lastMessage = item
    }

    private fun processBlankItem() {
        logDebug("Processing blank item")
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

    fun isOld(): Boolean {
        return (System.currentTimeMillis() - lastMessageSentTime.get()) > Duration.ofMinutes(1).toMillis()
    }

    private fun deleteCacheMessages() {
        val toDelete = HashSet(messageIds)
        messageIds.clear()
        logDebug("Deleting messages: $toDelete")
        toDelete.forEach {
            val res = runCatching { telegramBot.get().deleteInTargetChat(it) }.exceptionOrNull()
            logDebug("Deleting $it ${res?.message ?: "deleted"}")
        }
    }

    suspend fun setLastMessageIdInChat(value: Long) {
        messageIdsMutex.withLock {
            lastMessageIdInChat.set(value)
            logDebug("lastMessageIdInChat: $value")
        }

    }
}
