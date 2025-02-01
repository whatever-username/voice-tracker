package com.whatever.properties

import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties


@ConfigurationProperties("discord")
class DiscordProperties @ConfigurationInject constructor(
    val usernamesToRecord: List<String>,
    val bot: Bot,
    val guildName: String
) {
    @ConfigurationProperties("bot")
    class Bot @ConfigurationInject constructor(
        val token: String,
        val name: String
    )
}