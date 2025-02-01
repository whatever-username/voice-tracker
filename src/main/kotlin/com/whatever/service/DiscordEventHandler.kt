package com.whatever.service

import com.whatever.containsOneOf
import com.whatever.model.UserState
import com.whatever.properties.DiscordProperties
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import java.io.File

@Singleton
class DiscordEventHandler(
    private val discordProperties: DiscordProperties,
    private val audioHandler: AudioHandler,
    private val coordinatorService: CoordinatorService
) {

    fun handleVoiceUpdate(event: GuildVoiceUpdateEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            val entered = determineVoiceStateChange(event)
            entered?.let {
                val file = if (it) "in.mp3" else "out.mp3"
                audioHandler.playFromFile(File("/audio/$file"))
            }
        }
    }

    fun handleChannelJoined(channel: AudioChannelUnion) {
        if (channel.members.map { it.user.name }.containsOneOf(discordProperties.usernamesToRecord) &&
            !channel.members.map { it.user.name }.contains(discordProperties.bot.name)
        ) {
            audioHandler.audioManager.openAudioConnection(channel)
        }
    }

    fun handleEvent(event: GenericEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            coordinatorService.sendActivities(transformDiscordDataToMessage(event))
        }
    }

    private fun determineVoiceStateChange(event: GuildVoiceUpdateEvent): Boolean? {
        val currentChannel = audioHandler.audioManager.connectedChannel
        return when {
            event.channelJoined != null && currentChannel?.name == event.channelJoined?.name -> true
            event.channelLeft != null && currentChannel?.name == event.channelLeft?.name -> false
            else -> null
        }
    }

    private fun transformDiscordDataToMessage(event: GenericEvent): String {
        val guild = event.jda.getGuildsByName(discordProperties.guildName, true).firstOrNull()
            ?: return ""
        val userStates = extractUserStates(guild)
        val voiceChatToUsers = userStates.groupBy { it.voiceState?.channel?.name }.filterKeys { !it.isNullOrEmpty() }

        return buildString {
            voiceChatToUsers.forEach { (channelName, users) ->
                appendLine("<b>$channelName:</b>")
                users.forEach { appendUserState(it) }
            }
            appendLine(userStates.filter { it.presence != null }
                .joinToString("\n") {
                    "<code>" +
                            "${it.user.name}: ${
                                it.presence?.name?.replace("<", "&lt;")?.replace(">", "&gt;")?.replace("&", "&amp;")
                            }" +
                            "</code>"
                })
        }.takeIf { it.isNotBlank() } ?: ""
    }

    private fun extractUserStates(guild: net.dv8tion.jda.api.entities.Guild) =
        guild.members.filter { !it.user.isBot }.map {
            UserState(it.user, it.voiceState, it.activities.firstOrNull())
        }.filter {
            it.voiceState?.channel != null || it.presence != null
        }

    private fun StringBuilder.appendUserState(userState: UserState) {
        append("  🔹${userState.user.name}")
        userState.voiceState?.let {
            if (it.isMuted || it.isSelfMuted || it.isGuildMuted || it.isSuppressed || it.isSelfDeafened) append("🙊")
            if (it.isDeafened || it.isSelfDeafened || it.isGuildDeafened) append("🙉")
            if (it.isStream) append("🎥")
        }
        appendLine()
    }
}