package com.whatever.factory


import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.logging.LogLevel
import com.mpatric.mp3agic.Mp3File
import com.whatever.TagsMapConfig
import com.whatever.logError
import com.whatever.logInfo
import com.whatever.model.dto.MessageDTO
import com.whatever.model.dto.MessageUpdateDTO
import com.whatever.service.AudioHandler
import com.whatever.service.CoordinatorService
import com.whatever.service.OpenAIService
import com.whatever.toChatId
import io.micronaut.context.annotation.Value
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.SequenceInputStream
import java.nio.file.Files
import kotlin.system.exitProcess


@Singleton
class TelegramBot(
    private val audioHandler: AudioHandler,
    private val mapper: ObjectMapper,
    private val coordinatorService: CoordinatorService,
    private val openAIService: OpenAIService? = null,

    @Value("\${telegram.bot.token}") private val botToken: String,
    @Value("\${telegram.bot.webhook-url}") private val webhookUrl: String,
    @Value("\${telegram.target-chat-id}") private val targetChatId: Long,
    @Value("\${telegram.subgroup-id}") private val telegramSubgroupId: Long,
    @Value("\${telegram.voices-chat-id}") private val voicesChatId: Long,
    @Value("\${admins.telegram-ids}") private val adminsTelegramIds: Set<Long>,
    @Value("\${admins.main-admin}") private val mainAdmin: Long,
    private val tagsMapConfig: TagsMapConfig,
) {
    private val client = OkHttpClient()

    val bot = bot {
        webhook {
            url = webhookUrl.also { logInfo("Webhook URL: $it") }
            maxConnections = 50
            allowedUpdates = listOf("message")
        }
        token = botToken.also { logInfo("Bot token: $it") }
        logLevel = LogLevel.Error
        dispatch {
            tagsMapConfig.tagsMap.forEach { (tag, vals) ->
                this@dispatch.command(tag) {
                    bot.sendMessage(
                        targetChatId.toChatId(),
                        vals.joinToString(" ") { "@$it" })
                }
            }
            command("h") {
                bot.sendMessage(
                    targetChatId.toChatId(),
                    tagsMapConfig.tagsMap.keys.map { "/$it" }.joinToString("\n")
                )
            }
            command("restart") { exitProcess(1) }
            command("send") {
                handleSendCommand(message)

            }
            command("SEND") {
                handleSendCommand(message)
            }
            command("play") {
                logInfo("play command from ${message.from?.username}")
                if (message.from?.id in adminsTelegramIds) {
                    runCatching {
                        args.getOrNull(1)?.takeIf { it.startsWith("http") }?.let { url ->
                            CoroutineScope(Dispatchers.IO).launch { audioHandler.playMp3InDiscord(url) }
                        }
                        bot.deleteMessage(ChatId.fromId(message.chat.id), message.messageId)
                    }.exceptionOrNull()?.let { logError("Error on playing: ${it.message}") }
                }
            }
            command("stop") {
                logInfo("stop command from ${message.from?.username}")
                if (message.from?.id in adminsTelegramIds) {
                    runCatching {
                        audioHandler.stopPlayingInDiscord()
                        bot.deleteMessage(ChatId.fromId(message.chat.id), message.messageId)
                    }.exceptionOrNull()?.let { logError("Error on stopping: ${it.message}") }

                }
            }
            command("get_id") {
                bot.sendMessage(ChatId.fromId(message.chat.id), message.replyToMessage?.audio?.fileId ?: "null")
                bot.deleteMessage(ChatId.fromId(message.chat.id), message.messageId)
            }
            message {
                if ((message.text ?: "") in setOf(".ыутв", ".ЫУТВ")) {
                    handleSendCommand(message)
                    return@message
                }
                if (message.chat.id == targetChatId) {
                    message.messageId.let { coordinatorService.setLastMessageIdInChat(it) }
                }
            }
        }
    }

    private fun handleSendCommand(message: Message) {
        logInfo("send command from ${message.from?.username}")
        runCatching {
            if (message.from?.id in adminsTelegramIds) {
                message.replyToMessage?.audio?.fileId?.let {
                    CoroutineScope(Dispatchers.IO).launch { audioHandler.playMp3InDiscord(getVoiceLink(it)!!) }
                }
                bot.deleteMessage(ChatId.fromId(message.chat.id), message.messageId)
            }
        }.exceptionOrNull()?.let { logInfo("Error on sending: ${it.message}") }
    }

    @EventListener
    fun startup(event: ApplicationStartupEvent) {
        bot.deleteWebhook(true).also { logInfo("Webhook deletion") }
        bot.startWebhook().also { logInfo("Webhook started: $it") }
        bot.sendMessage(mainAdmin.toChatId(), "restarted")
    }

    fun sendToChat(text: String) = bot.sendMessage(targetChatId.toChatId(), text, parseMode = ParseMode.HTML)
    fun editInChat(text: String, messageId: Long) =
        bot.editMessageText(targetChatId.toChatId(), messageId, text = text, parseMode = ParseMode.HTML)
            .let {
                it.first to it.second
            }

    fun deleteInTargetChat(messageId: Long) = bot.deleteMessage(targetChatId.toChatId(), messageId)
    fun getVoiceLink(fileId: String): String? = runCatching {
        val fileUrl = bot.getFile(fileId).first?.body()?.result?.filePath ?: throw IOException("File not found")
        "https://api.telegram.org/file/bot${botToken}/${fileUrl}"
    }.getOrNull()


    fun processMessageThread(update: String) {
        val message = try {
            mapper.readValue(update, MessageUpdateDTO::class.java)
        } catch (e: Exception) {
            logError("Error on parsing message in thread: ${e.message}")
            return
        }
        if (message.message.message_thread_id != telegramSubgroupId) return
        message.message.audio?.file_id?.let {
            processAudiofileInFavChannel(it)
        }
        bot.deleteMessage(message.message.chat.id.toChatId(), message.message.message_id)

    }

    private val audioCache = mutableSetOf<String>()
    private var batchAudioProcessJob: Job? = null

    fun processAudiofileInFavChannel(fileId: String) {
        audioCache += fileId
        batchAudioProcessJob?.cancel()
        batchAudioProcessJob = batchAudioProcess()
    }

    private fun batchAudioProcess() = CoroutineScope(Dispatchers.IO).launch {
        delay(1000)
        val files = audioCache.map { async { getVoiceLink(it)?.let { downloadFileInTmpFile(it) } } }
        audioCache.clear()
        val merged = concatenateMp3Files(File.createTempFile("merged", ".mp3"), files.awaitAll().filterNotNull())
        sendAudioToTelegramChat(
            threadId = telegramSubgroupId,
            caption = openAIService?.voiceToText(merged),
            file = merged
        )
    }

    private fun downloadFileInTmpFile(fileUrl: String): File {
        val file = File.createTempFile("voice", ".mp3")
        client.newCall(Request.Builder().url(fileUrl.toHttpUrl()).build()).execute().body?.byteStream()?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    fun sendAudioToTelegramChat(caption: String? = null, file: File, threadId: Long? = null): MessageDTO? {
        val url = "https://api.telegram.org/bot${botToken}/sendAudio"

        val fileRequestBody = RequestBody.create("audio/mp3".toMediaTypeOrNull(), file)
        val multipartBody =
            MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("chat_id", voicesChatId.toString())
                .addFormDataPart("audio", file.name, fileRequestBody)
        threadId?.let {
            multipartBody.addFormDataPart("message_thread_id", it.toString())
        }
        caption?.let { multipartBody.addFormDataPart("caption", caption) }
        Request.Builder().url(url).post(multipartBody.build()).build().let { req ->
            client.newCall(req).execute()
        }.use { resp ->
            return runCatching { mapper.readValue<MessageDTO>(resp.body!!.string()) }.getOrNull()
        }
    }

    fun concatenateMp3Files(outputFile: File, inputFiles: List<File>): File {
        val outputStream = FileOutputStream(outputFile)
        val tempFiles = mutableListOf<File>()

        try {
            val inputStreams = inputFiles.map { filePath ->
                val mp3File = Mp3File(filePath)
                val tempFile = File.createTempFile("temp", ".mp3")
                tempFiles.add(tempFile)
                mp3File.save(tempFile.absolutePath)
                Files.newInputStream(tempFile.toPath())
            }

            val sequenceInputStream = SequenceInputStream(inputStreams.iterator().asSequence().toEnumeration())
            sequenceInputStream.use { it.copyTo(outputStream) }
        } finally {
            outputStream.close()
            tempFiles.forEach { it.delete() }
        }
        return outputFile
    }

    // Extension function to convert a sequence to an enumeration
    fun <T> Sequence<T>.toEnumeration(): java.util.Enumeration<T> {
        return object : java.util.Enumeration<T> {
            private val iterator = this@toEnumeration.iterator()
            override fun hasMoreElements(): Boolean = iterator.hasNext()
            override fun nextElement(): T = iterator.next()
        }
    }


    fun sendToMainAdmin(text: String) {
        bot.sendMessage(mainAdmin.toChatId(), text, parseMode = ParseMode.HTML)
    }
}