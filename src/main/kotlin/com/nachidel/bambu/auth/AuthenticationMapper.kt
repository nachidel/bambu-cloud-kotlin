package com.nachidel.bambu.auth

import com.nachidel.bambu.dto.auth.LoginResponse
import com.nachidel.bambu.value.AccessToken

internal object AuthenticationMapper {

    fun map(
        response: LoginResponse
    ): AuthenticationResult {

        val token =
            response.accessToken
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }

        if (token != null) {

            return AuthenticationResult.Authenticated(
                AccessToken(token)
            )
        }

        if (
            response.loginType.equals(
                "verifyCode",
                ignoreCase = true
            )
        ) {

            return AuthenticationResult
                .VerificationCodeRequired
        }

        return AuthenticationResult.Rejected(
            code = response.code,
            message = response.message
        )
    }
}