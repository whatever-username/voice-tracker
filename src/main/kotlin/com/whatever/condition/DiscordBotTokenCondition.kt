package com.whatever.condition

import io.micronaut.context.condition.Condition
import io.micronaut.context.condition.ConditionContext


class DiscordBotTokenCondition : Condition {
    override fun matches(context: ConditionContext<*>): Boolean {
        val token = context.getProperty("discord.bot.token", String::class.java).orElse("")
        return token.isNotEmpty()
    }
}