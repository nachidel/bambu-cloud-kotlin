package com.nachidel.bambu.http

interface BambuHttpClient {

    suspend fun get(
        path: String
    ): String

    suspend fun post(
        path: String,
        body: Any? = null
    ): String
}