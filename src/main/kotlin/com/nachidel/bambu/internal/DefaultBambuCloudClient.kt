package com.nachidel.bambu.internal

import com.nachidel.bambu.api.BambuCloudClient
import com.nachidel.bambu.api.BambuConfiguration
import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.mqtt.BambuCloudMqttClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class DefaultBambuCloudClient(
    private val configuration: BambuConfiguration
) : BambuCloudClient {

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private val _events =
        MutableSharedFlow<BambuEvent>(
            extraBufferCapacity = 64
        )

    override val events: Flow<BambuEvent> =
        _events.asSharedFlow()

    private val httpClient =
        HttpClientFactory.create()

    private val discovery =
        CloudDiscoveryService(
            httpClient
        )

    private var mqttClient:
            BambuCloudMqttClient? = null


    override suspend fun connect() {

        if (mqttClient != null) {
            return
        }

        val token =
            configuration.accessToken
                ?: error(
                    "Bambu access token is required"
                )

        val accessToken =
            token.value

        val userId =
            discovery.getUserId(
                accessToken
            )

        val devices =
            discovery.getDevices(
                accessToken
            )

        require(devices.isNotEmpty()) {
            "No Bambu Lab printer found on this account"
        }

        val device =
            selectDevice(
                devices
            )

        val mqtt =
            BambuCloudMqttClient(
                userId = userId,
                deviceId = device.id,
                accessToken = accessToken
            )

        /*
         * On commence à collecter AVANT connect().
         *
         * Sinon PrinterConnected pourrait théoriquement
         * être émis avant que le collecteur soit prêt.
         */
        scope.launch {

            mqtt.events.collect { event ->

                _events.emit(event)
            }
        }

        mqttClient =
            mqtt

        mqtt.connect()
    }


    private fun selectDevice(
        devices: List<CloudDevice>
    ): CloudDevice {

        val requestedPrinter =
            configuration.printerId

        if (requestedPrinter != null) {

            return devices.firstOrNull {
                it.id == requestedPrinter
            } ?: error(
                "Printer '$requestedPrinter' is not linked to this account"
            )
        }

        if (devices.size == 1) {
            return devices.first()
        }

        /*
         * Pour l'instant on refuse de choisir arbitrairement
         * si le compte possède plusieurs imprimantes.
         */
        error(
            "Multiple printers are linked to this account. " +
                    "Set printerId in BambuConfiguration."
        )
    }


    override suspend fun disconnect() {

        mqttClient
            ?.close()

        mqttClient =
            null
    }


    override fun close() {

        mqttClient
            ?.close()

        mqttClient =
            null

        httpClient.close()

        scope.cancel()
    }
}