package com.whatever.model.dto

data class Chat(
    val id: Long,
    val is_forum: Boolean,
    val title: String,
    val type: String
)