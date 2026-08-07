package com.nachidel.bambu.dto.auth

import kotlinx.serialization.Serializable

@Serializable
internal data class LoginRequest(
    val account: String,
    val password: String? = null,
    val code: String? = null
)