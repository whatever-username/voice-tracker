package com.whatever.model
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.User

data class UserState(
    val user: User,
    val voiceState: GuildVoiceState?,
    val presence: Activity?
)