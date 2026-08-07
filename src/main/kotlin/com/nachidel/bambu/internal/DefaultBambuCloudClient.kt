package com.nachidel.bambu.internal

import com.nachidel.bambu.api.BambuCloudClient
import com.nachidel.bambu.api.BambuConfiguration
import com.nachidel.bambu.event.BambuEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DefaultBambuCloudClient(configuration: BambuConfiguration) : BambuCloudClient {

    private val _events = MutableSharedFlow<BambuEvent>()

    override val events: Flow<BambuEvent> = _events.asSharedFlow()

    override suspend fun connect() {
        println("Connected")
    }

    override suspend fun disconnect() {
        println("Disconnected")
    }
}