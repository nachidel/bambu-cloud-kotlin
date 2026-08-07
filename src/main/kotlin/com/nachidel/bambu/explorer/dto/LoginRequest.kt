package com.nachidel.bambu.explorer.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val account: String,
    val password: String? = null,
    val code: String? = null
)