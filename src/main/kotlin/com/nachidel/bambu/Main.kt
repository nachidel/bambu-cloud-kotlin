package com.nachidel.bambu

import com.nachidel.bambu.api.BambuCloudClient
import com.nachidel.bambu.value.AccessToken
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {

    val client = BambuCloudClient {
        accessToken = AccessToken("xxxxx")
    }

    client.connect()

    println("SDK started")
}