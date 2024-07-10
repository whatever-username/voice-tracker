package com.whatever.model.dto

data class From(
    val first_name: String,
    val id: Long,
    val is_bot: Boolean,
    val is_premium: Boolean,
    val language_code: String,
    val username: String
)