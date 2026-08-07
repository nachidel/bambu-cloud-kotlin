package com.nachidel.bambu.dto.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class LoginResponse(

    @SerialName("accessToken")
    val accessToken: String? = null,

    @SerialName("refreshToken")
    val refreshToken: String? = null,

    @SerialName("loginType")
    val loginType: String? = null,

    @SerialName("tfaKey")
    val tfaKey: String? = null,

    val message: String? = null,

    val code: String? = null
)