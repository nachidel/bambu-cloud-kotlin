package com.nachidel.bambu.api

import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.internal.DefaultBambuCloudClient
import kotlinx.coroutines.flow.Flow

interface BambuCloudClient : AutoCloseable {

    val events: Flow<BambuEvent>

    suspend fun connect()

    suspend fun disconnect()

    override fun close()

    companion object {

        operator fun invoke(
            block: BambuConfiguration.() -> Unit = {}
        ): BambuCloudClient {

            val configuration =
                BambuConfiguration().apply(block)

            return DefaultBambuCloudClient(
                configuration
            )
        }
    }
}