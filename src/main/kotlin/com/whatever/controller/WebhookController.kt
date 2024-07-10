package com.whatever.controller

import com.whatever.factory.TelegramBot
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.serde.annotation.SerdeImport

@SerdeImport(Unit::class)
@Controller
class WebhookController(
    private val telegramBot: TelegramBot,
) {
    @Post("/bot/voice-tracker-bot")
    suspend fun handleUpdate(
        @Body update: String,
    ): MutableHttpResponse<String> {
        if (update.contains("\"audio\"") && update.contains("message_thread_id") && !update.contains("/send")) {
            telegramBot.processMessageThread(update)
        } else {
            telegramBot.bot.processUpdate(update)

        }
        return HttpResponse.ok()
    }

}
