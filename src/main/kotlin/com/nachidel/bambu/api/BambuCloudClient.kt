package com.nachidel.bambu.api

import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.internal.DefaultBambuCloudClient
import kotlinx.coroutines.flow.Flow

interface BambuCloudClient {

    val events: Flow<BambuEvent>

    suspend fun connect()

    suspend fun disconnect()

    companion object {

        operator fun invoke(
            block: BambuConfiguration.() -> Unit = {}
        ): DefaultBambuCloudClient {

            val configuration = BambuConfiguration()
                .apply(block)

            return DefaultBambuCloudClient(configuration)

        }

    }

}