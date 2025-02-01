package com.whatever.service

import net.dv8tion.jda.api.events.guild.voice.GenericGuildVoiceEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.user.update.GenericUserPresenceEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class DiscordEventListener(
    private val discordEventHandler: DiscordEventHandler,
) : ListenerAdapter() {

    override fun onGenericUserPresence(event: GenericUserPresenceEvent) = discordEventHandler.handleEvent(event)

    override fun onGenericGuildVoice(event: GenericGuildVoiceEvent) = discordEventHandler.handleEvent(event)

    override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
        if (event.member.user.isBot) return

        event.channelJoined?.let { discordEventHandler.handleChannelJoined(it) }
        discordEventHandler.handleVoiceUpdate(event)
        discordEventHandler.handleEvent(event)
    }

}