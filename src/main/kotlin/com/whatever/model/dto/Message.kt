package com.whatever.model.dto

data class Message(
    val chat: Chat,
    val date: Long,
    val from: From,
    val is_topic_message: Boolean,
    val message_id: Long,
    val message_thread_id: Long,
    val reply_to_message: ReplyToMessage,
    val text: String?,
    val audio: MessageDTO.Result.Audio?,
)