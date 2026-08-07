package com.nachidel.bambu.explorer

import com.nachidel.bambu.explorer.dto.LoginRequest
import com.nachidel.bambu.explorer.dto.LoginResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ExplorerHttpClient : AutoCloseable {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }

        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun login(
        account: String,
        password: String
    ): LoginResponse {
        return client.post(
            "https://api.bambulab.com/v1/user-service/user/login"
        ) {
            setBody(
                LoginRequest(
                    account = account,
                    password = password
                )
            )
        }.body()
    }

    suspend fun loginWithCode(
        account: String,
        code: String
    ): LoginResponse {
        return client.post(
            "https://api.bambulab.com/v1/user-service/user/login"
        ) {
            setBody(
                LoginRequest(
                    account = account,
                    code = code
                )
            )
        }.body()
    }

    suspend fun getRaw(
        path: String,
        accessToken: String
    ): String {
        return client.get("https://api.bambulab.com$path") {
            bearerAuth(accessToken)
        }.bodyAsText()
    }

    override fun close() {
        client.close()
    }
}