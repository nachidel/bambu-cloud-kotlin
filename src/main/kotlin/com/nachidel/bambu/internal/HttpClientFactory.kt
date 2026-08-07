package com.nachidel.bambu.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal object HttpClientFactory {

    private val jsonConfiguration =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    fun create(): HttpClient {

        return HttpClient(CIO) {

            install(ContentNegotiation) {
                json(jsonConfiguration)
            }

            defaultRequest {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
            }
        }
    }
}