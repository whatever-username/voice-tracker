package com.whatever.service

import com.aallam.openai.api.audio.TranscriptionRequest
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.whatever.logDebug
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.context.condition.Condition
import io.micronaut.context.condition.ConditionContext
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.time.Duration.Companion.seconds

@Singleton
@Requires(condition = OpenAIKeyCondition::class)
class OpenAIService(
    @Value("\${open-ai.key}")
    private val openAIKey: String,
) {
    private val openAI = OpenAI(token = openAIKey,
        logging = LoggingConfig(LogLevel.None),
        timeout = Timeout(socket = 60.seconds), httpClientConfig = {
            engine {
                pipelining = true
            }
        })
    private val chatGptMutex = Mutex()

    suspend fun voiceToText(file: File): String {
        chatGptMutex.withLock {
            logDebug("voiceToText file: ${file.name}")
            val res = openAI.transcription(
                TranscriptionRequest(
                    language = "RU",
                    model = ModelId("whisper-1"),
                    audio = FileSource(file.toOkioPath(), fileSystem = FileSystem.SYSTEM)
                )
            )
            return res.text
        }

    }
}

class OpenAIKeyCondition : Condition {
    override fun matches(context: ConditionContext<*>): Boolean {
        val token = context.getProperty("open-ai.key", String::class.java).orElse("")
        return token.isNotEmpty()
    }
}