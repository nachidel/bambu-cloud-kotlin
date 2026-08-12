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
import com.nachidel.bambu.auth.AuthenticationResult
import com.nachidel.bambu.auth.AuthenticationService
import com.nachidel.bambu.exception.BambuAuthenticationException
import com.nachidel.bambu.model.PrintTask
import com.nachidel.bambu.model.Printer
import com.nachidel.bambu.model.PrinterState
import com.nachidel.bambu.model.PrinterType
import com.nachidel.bambu.value.SerialNumber

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

    private val tasks =
        CloudTaskService(
            httpClient
        )

    private val authentication =
        AuthenticationService(
            httpClient
        )

    private var mqttClient:
            BambuCloudMqttClient? = null


    override suspend fun login(
        email: String,
        password: String
    ): AuthenticationResult {

        val result =
            authentication.login(
                email = email,
                password = password
            )

        rememberAccessToken(result)

        return result
    }


    override suspend fun verifyCode(
        email: String,
        code: String
    ): AuthenticationResult {

        val result =
            authentication.verifyCode(
                email = email,
                code = code
            )

        rememberAccessToken(result)

        return result
    }


    private fun rememberAccessToken(
        result: AuthenticationResult
    ) {

        if (
            result is AuthenticationResult.Authenticated
        ) {

            configuration.accessToken =
                result.accessToken
        }
    }

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

    override suspend fun printers(): List<Printer> {

        val token =
            configuration.accessToken
                ?: throw BambuAuthenticationException(
                    "An access token is required to list printers"
                )

        return discovery
            .getDevices(token.value)
            .map { device ->

                Printer(
                    serial = SerialNumber(device.id),
                    name = device.name,
                    type = PrinterType.fromProductName(
                        device.productName
                    ),
                    state =
                        if (device.online) {
                            PrinterState.UNKNOWN
                        } else {
                            PrinterState.OFFLINE
                        }
                )
            }
    }

    private fun selectDevice(
        devices: List<CloudDevice>
    ): CloudDevice {

        val requestedPrinter =
            configuration.printer

        if (requestedPrinter != null) {

            return devices.firstOrNull {
                it.id == requestedPrinter.value
            } ?: error(
                "Printer '${requestedPrinter.value}' is not linked to this account"
            )
        }

        if (devices.size == 1) {
            return devices.first()
        }

        error(
            "Multiple printers are linked to this account. " +
                    "Set printer in BambuConfiguration."
        )
    }

    override suspend fun latestTask(printer: SerialNumber): PrintTask? {
        val token =
            configuration.accessToken
                ?: throw BambuAuthenticationException(
                    "An access token is required to retrieve print tasks"
                )

        return tasks.getLatestTask(
            accessToken = token.value,
            deviceId = printer.value
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