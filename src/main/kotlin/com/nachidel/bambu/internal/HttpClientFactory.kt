package com.nachidel.bambu.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal object HttpClientFactory {

    fun create() =
        HttpClient(CIO) {

        }

}