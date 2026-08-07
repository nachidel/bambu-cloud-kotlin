package com.nachidel.bambu.internal

internal data class CloudDevice(
    val id: String,
    val name: String,
    val productName: String?,
    val online: Boolean
)