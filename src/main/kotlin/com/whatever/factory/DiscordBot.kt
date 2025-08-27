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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.requests.GatewayIntent

@Singleton
@Requires(condition = DiscordBotTokenCondition::class)
class DiscordBot(
    private val discordProperties: DiscordProperties,
    private val audioHandler: AudioHandler,
    private val telegramBot: Provider<TelegramBot>,
    private val discordEventHandler: DiscordEventHandler
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutedSince = mutableMapOf<Long, Long>()
    private lateinit var guild: Guild

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
        this.guild = guild

        configureAudioHandler(guild)

        if (shouldOpenAudioConnection()) {
            audioHandler.audioManager.openAudioConnection(audioHandler.channel)
        }

        startInlineModerationLoop()
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

    private fun startInlineModerationLoop() {
        val minutes = discordProperties.disconnectMutedAfterMinutes ?: return
        if (minutes <= 0) return
        val thresholdMs = minutes * 60_000

        logger().info("[moderation] enabled, threshold=${minutes}m, interval=15s")
        scope.launch {
            while (true) {
                logger().debug("[moderation] scan start")
                runCatching { scanAndKickMuted(thresholdMs) }
                    .onFailure { logError("Moderation scan failed: ${it.message}", telegramBot.get()) }
                logger().debug("[moderation] scan end")
                delay(15_000)
            }
        }
    }

    private fun scanAndKickMuted(thresholdMs: Long) {
        if (!this::guild.isInitialized) return
        val now = System.currentTimeMillis()

        var kicked = 0
        var considered = 0
        guild.voiceChannels.forEach { channel ->
            val anyStreaming = channel.members.any { it.voiceState?.isStream == true }
            logger().debug("[moderation] channel='${channel.name}', members=${channel.members.size}, streaming=${anyStreaming}")

            channel.members.filter { !it.user.isBot }.forEach { member ->
                val vs = member.voiceState ?: return@forEach
                val fullyMuted =
                    (vs.isDeafened || vs.isSelfDeafened || vs.isGuildDeafened)

                val key = member.idLong
                if (fullyMuted) {
                    val since = mutedSince.getOrPut(key) {
                        logger().debug("[moderation] start timer user='${member.user.name}' (${member.id})")
                        now
                    }
                    if (!anyStreaming && now - since >= thresholdMs) {
                        logger().info("[moderation] kick user='${member.user.name}' (${member.id}) channel='${channel.name}' mutedForMs=${now - since}")
                        member.guild.kickVoiceMember(member).queue()
                        mutedSince.remove(key)
                        kicked++
                    }
                } else {
                    if (mutedSince.remove(key) != null) {
                        logger().debug("[moderation] reset timer user='${member.user.name}' (${member.id})")
                    }
                }
                considered++
            }
        }
        logger().debug("[moderation] scan summary considered=${considered}, kicked=${kicked}")
    }
}

