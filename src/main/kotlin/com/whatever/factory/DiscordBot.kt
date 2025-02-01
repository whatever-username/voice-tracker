package com.whatever.factory

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.whatever.condition.DiscordBotTokenCondition
import com.whatever.logError
import com.whatever.logger
import com.whatever.properties.DiscordProperties
import com.whatever.service.AudioHandler
import com.whatever.service.DiscordEventHandler
import com.whatever.service.DiscordEventListener
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Provider
import jakarta.inject.Singleton
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent

@Singleton
@Requires(condition = DiscordBotTokenCondition::class)
class DiscordBot(
    private val discordProperties: DiscordProperties,
    private val audioHandler: AudioHandler,
    private val telegramBot: Provider<TelegramBot>,
    private val discordEventHandler: DiscordEventHandler
) {

    @EventListener
    fun startup(event: ApplicationStartupEvent) {
        logger().info("Usernames to record: ${discordProperties.usernamesToRecord.joinToString()}")
        runCatching { initializeBot() }.exceptionOrNull()?.let {
            logError("Error while starting Discord bot: ${it.message}", telegramBot.get())
        }
    }

    private fun initializeBot() {
        val jda = createJDA().build().awaitReady()
        jda.addEventListener(
            DiscordEventListener(
                discordEventHandler
            )
        )
        val guild = jda.getGuildsByName(discordProperties.guildName, true).firstOrNull()
            ?: throw RuntimeException("Guild ${discordProperties.guildName} not found")

        configureAudioHandler(guild)

        if (shouldOpenAudioConnection()) {
            audioHandler.audioManager.openAudioConnection(audioHandler.channel)
        }
    }

    private fun createJDA() = JDABuilder.create(
        discordProperties.bot.token,
        GatewayIntent.GUILD_PRESENCES,
        GatewayIntent.GUILD_MEMBERS,
        GatewayIntent.GUILD_MESSAGES,
        GatewayIntent.GUILD_VOICE_STATES
    )

    private fun configureAudioHandler(guild: net.dv8tion.jda.api.entities.Guild) {
        audioHandler.apply {
            audioManager = guild.audioManager
            audioManager.receivingHandler = this
            channel = guild.voiceChannels.first()
            AudioSourceManagers.registerRemoteSources(playerManager)
            audioManager.sendingHandler = audioSendHandler
        }
    }

    private fun shouldOpenAudioConnection(): Boolean {
        val members = audioHandler.channel.members.map { it.user.name }
        return members.any { it in discordProperties.usernamesToRecord } && !members.contains(discordProperties.bot.name)
    }
}

