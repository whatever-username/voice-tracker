package com.whatever.factory

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.whatever.containsOneOf
import com.whatever.log
import com.whatever.model.UserState
import com.whatever.service.AudioHandler
import com.whatever.service.CoordinatorService
import io.micronaut.context.annotation.Value
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.guild.voice.GenericGuildVoiceEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.user.update.GenericUserPresenceEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent

@Singleton
class DiscordBot(
    @Value("\${discord.usernames-to-record}") private val usernamesToRecord: List<String>,
    @Value("\${discord.bot.token}") private val botToken: String,
    private val coordinatorService: CoordinatorService,
    private val audioHandler: AudioHandler,
    @Value("\${discord.bot.name}") private val discordBotName: String,
    @Value("\${discord.guild-name}") private val guildName: String,
    private val telegramBot: Provider<TelegramBot>
) {

    @EventListener
    fun startup(event: ApplicationStartupEvent) {
        runCatching {
            val jda = JDABuilder.create(
                botToken,
                GatewayIntent.GUILD_PRESENCES,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.GUILD_VOICE_STATES
            ).addEventListeners(object : ListenerAdapter() {
                override fun onGenericUserPresence(event: GenericUserPresenceEvent) = send(event)
                override fun onGenericGuildVoice(event: GenericGuildVoiceEvent) = send(event)
                override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
                    if (!event.member.user.isBot) {
                        event.channelJoined?.let {
                            if (it.members.map { it.user.name }.containsOneOf(usernamesToRecord) &&
                                !it.members.map { it.user.name }.contains(discordBotName)
                            ) {
                                audioHandler.audioManager.openAudioConnection(it)
                            }
                        }
                        send(event)
                    }
                }
            }).build().awaitReady()

            (jda.getGuildsByName(guildName, true).firstOrNull()
                ?: throw RuntimeException("Guild $guildName not found")).let { guild ->
                audioHandler.apply {
                    audioManager = guild.audioManager
                    audioManager.receivingHandler = this
                    channel = guild.voiceChannels.first()
                    AudioSourceManagers.registerRemoteSources(playerManager)
                    audioManager.sendingHandler = audioSendHandler
                }

                if (audioHandler.channel.members.map { it.user.name }.any { it in usernamesToRecord }) {
                    audioHandler.channel.members.map { it.user.name }.run {
                        if (!contains(discordBotName)) {
                            audioHandler.audioManager.openAudioConnection(audioHandler.channel)
                        }
                    }
                }
            }
        }.exceptionOrNull()?.let {
            with("Error while starting Discord bot: ${it.message}") {
                log(this)
                telegramBot.get().sendToMainAdmin(this)
            }

        }

    }

    private fun send(event: GenericEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            coordinatorService.sendActivities(transformDiscordDataToMessage(event))
        }
    }

    private fun transformDiscordDataToMessage(event: GenericEvent): String {
        val guild = event.jda.getGuildsByName(guildName, true).first()
        val members = guild.members.filter { !it.user.isBot }
        val userStates = members.map {
            UserState(it.user, it.voiceState, it.activities.firstOrNull())
        }.filter {
            it.voiceState?.channel != null || it.presence != null
        }
        val voiceChatToUsers = userStates.groupBy { it.voiceState?.channel?.name }.filterKeys { !it.isNullOrEmpty() }
        return buildString {
            voiceChatToUsers.forEach { (channelName, users) ->
                appendLine("<b>$channelName:</b>")
                users.forEach {
                    append("  🔹${it.user.name}")
                    if (it.voiceState?.isMuted == true || it.voiceState?.isSelfMuted == true ||
                        it.voiceState?.isGuildMuted == true || it.voiceState?.isSuppressed == true ||
                        it.voiceState?.isSelfDeafened == true
                    ) append("🙊")
                    if (it.voiceState?.isDeafened == true || it.voiceState?.isSelfDeafened == true ||
                        it.voiceState?.isGuildDeafened == true || it.voiceState?.isSelfDeafened == true
                    ) append("🙉")
                    if (it.voiceState?.isStream == true) append("🎥")
                    appendLine()
                }
            }
            appendLine(userStates.filter { it.presence != null }
                .joinToString("\n") { "<code>${it.user.name}: ${it.presence?.name}</code>" })
        }.takeIf { it.isNotBlank() } ?: ""
    }
}
