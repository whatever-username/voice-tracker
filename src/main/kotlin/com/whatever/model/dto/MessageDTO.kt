package com.whatever.model.dto

data class MessageDTO(
    val ok: Boolean?,
    val result: Result?,
) {
    data class Result(
        val audio: Audio?,
        val caption: String?,
        val chat: Chat?,
        val date: Long?,
        val from: From?,
        val message_id: Long?,
    ) {
        data class Audio(
            val duration: Long?,
            val file_id: String?,
            val file_name: String?,
            val file_size: Long?,
            val file_unique_id: String?,
            val mime_type: String?,
        )

        data class Chat(
            val id: Long?,
            val title: String?,
            val type: String?,
        )

        data class From(
            val first_name: String?,
            val id: Long?,
            val is_bot: Boolean?,
            val username: String?,
        )
    }
}
