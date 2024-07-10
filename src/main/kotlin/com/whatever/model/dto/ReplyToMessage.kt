package com.whatever.model.dto

data class ReplyToMessage(
    val chat: Chat,
    val date: Long,
    val forum_topic_created: ForumTopicCreated,
    val from: From,
    val is_topic_message: Boolean,
    val message_id: Long,
    val message_thread_id: Long
)