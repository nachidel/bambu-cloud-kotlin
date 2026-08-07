package com.nachidel.bambu.auth

import com.nachidel.bambu.dto.auth.LoginRequest
import com.nachidel.bambu.dto.auth.LoginResponse
import com.nachidel.bambu.value.AccessToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal class AuthenticationService(
    private val httpClient: HttpClient
) {

    suspend fun login(
        email: String,
        password: String
    ): AuthenticationResult {

        require(email.isNotBlank()) {
            "Email must not be blank"
        }

        require(password.isNotBlank()) {
            "Password must not be blank"
        }

        return authenticate(
            LoginRequest(
                account = email,
                password = password
            )
        )
    }


    suspend fun verifyCode(
        email: String,
        code: String
    ): AuthenticationResult {

        require(email.isNotBlank()) {
            "Email must not be blank"
        }

        require(code.isNotBlank()) {
            "Verification code must not be blank"
        }

        return authenticate(
            LoginRequest(
                account = email,
                code = code
            )
        )
    }


    private suspend fun authenticate(
        request: LoginRequest
    ): AuthenticationResult {

        val response =
            httpClient.post(LOGIN_ENDPOINT) {
                setBody(request)
            }.body<LoginResponse>()

        return AuthenticationMapper.map(response)
    }

    private companion object {

        const val LOGIN_ENDPOINT =
            "https://api.bambulab.com/v1/user-service/user/login"
    }
}