package com.nachidel.bambu.auth

import com.nachidel.bambu.value.AccessToken

sealed interface AuthenticationResult {

    data class Authenticated(
        val accessToken: AccessToken
    ) : AuthenticationResult

    data object VerificationCodeRequired :
        AuthenticationResult

    data class Rejected(
        val code: String? = null,
        val message: String? = null
    ) : AuthenticationResult
}