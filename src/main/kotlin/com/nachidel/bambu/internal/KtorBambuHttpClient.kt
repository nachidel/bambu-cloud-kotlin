package com.nachidel.bambu.internal

import com.nachidel.bambu.http.BambuHttpClient
import io.ktor.client.HttpClient

internal class KtorBambuHttpClient(
    private val client: HttpClient
) : BambuHttpClient {

    override suspend fun get(path: String): String {
        TODO("Not yet implemented")
    }

    override suspend fun post(path: String, body: Any?): String {
        TODO("Not yet implemented")
    }
}