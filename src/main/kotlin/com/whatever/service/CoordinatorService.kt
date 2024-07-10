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
import kotlin.math.min

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

    init {
        CoroutineScope(dispatcher).launch {
            for (item in channel) {
                log("new item: ${item.hashCode()}")
                val start = System.currentTimeMillis()
                log("item != lastMessage: ${item != lastMessage}")
                log("lastMessageIdInChat: ${lastMessageIdInChat.value}, eventsMessageId: ${eventsMessageId.value}")
                if (item != lastMessage || (lastMessageIdInChat.value != eventsMessageId.value)) {
                    if (item.isNotBlank()) {
                        log("itemIsNotBlank: true")
                        if (lastMessageIdInChat.value != eventsMessageId.value) {
                            deleteCacheMessages()
                            telegramBot.get().sendToChat(item).also {
                                with(it.get().messageId) {
                                    lastMessageIdInChat.value = this
                                    messageIdsMutex.withLock {
                                        messageIds.add(it.get().messageId)
                                    }
                                }

                            }.let {
                                eventsMessageId.value = it.get().messageId
                                log("eventsMessageId: ${eventsMessageId.value}")
                            }
                        } else {
                            telegramBot.get().editInChat(item, eventsMessageId.value)
                        }
                        lastMessage = item
                    } else {
                        deleteCacheMessages()
                        lastMessage = ""
                        eventsMessageId.value = 0
                    }
                    delay(min(1000 - (System.currentTimeMillis() - start), 1))
                }
            }
        }
        CoroutineScope(dispatcher).launch {
            while (true) {
                delay(60 * 1000)
                if (lastMessageIdInChat.value != eventsMessageId.value) {
                    lastMessage?.let {
                        sendActivities(it)
                    }
                }
            }

        }
    }

    suspend fun sendActivities(string: String) {
        channel.send(string)
    }

    private suspend fun deleteCacheMessages() {
        messageIdsMutex.withLock {
            val toDelete = messageIds.toSet()
            messageIds.clear()
            IOScope.launch {
                toDelete.forEach {
                    telegramBot.get().deleteInTargetChat(it)
                }
            }
        }
    }


    fun setLastMessageIdInChat(value: Long) {
        lastMessageIdInChat.value = value
        log("lastMessageIdInChat: ${lastMessageIdInChat.value}")
    }
}