package com.nachidel.bambu.mqtt

import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.internal.PrinterStatusTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import javax.net.ssl.SSLContext

internal class BambuCloudMqttClient(
    private val userId: Long,
    private val deviceId: String,
    private val accessToken: String
) : AutoCloseable {

    private val broker =
        "ssl://us.mqtt.bambulab.com:8883"

    private val reportTopic =
        "device/$deviceId/report"

    private val requestTopic =
        "device/$deviceId/request"

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private val tracker =
        PrinterStatusTracker()

    private val _events =
        MutableSharedFlow<BambuEvent>(
            extraBufferCapacity = 64
        )

    val events: Flow<BambuEvent> =
        _events.asSharedFlow()

    private var pushAllJob: Job? = null

    private val client =
        MqttClient(
            broker,
            "bambu-kotlin-${UUID.randomUUID()}",
            MemoryPersistence()
        )


    init {
        configureCallback()
    }


    val isConnected: Boolean
        get() = client.isConnected


    fun connect() {

        if (client.isConnected) {
            return
        }

        val options =
            MqttConnectOptions().apply {

                userName =
                    "u_$userId"

                password =
                    accessToken.toCharArray()

                isAutomaticReconnect = true
                isCleanSession = true

                connectionTimeout = 15
                keepAliveInterval = 30

                mqttVersion =
                    MqttConnectOptions.MQTT_VERSION_3_1_1

                socketFactory =
                    SSLContext
                        .getDefault()
                        .socketFactory
            }

        client.connect(options)
    }


    private fun configureCallback() {

        client.setCallback(
            object : MqttCallbackExtended {

                override fun connectComplete(
                    reconnect: Boolean,
                    serverURI: String?
                ) {
                    onConnected()
                }


                override fun connectionLost(
                    cause: Throwable?
                ) {
                    pushAllJob?.cancel()

                    _events.tryEmit(
                        BambuEvent.PrinterDisconnected(
                            cause = cause
                        )
                    )
                }


                override fun messageArrived(
                    topic: String?,
                    message: MqttMessage?
                ) {

                    if (topic != reportTopic) {
                        return
                    }

                    val payload =
                        message
                            ?.payload
                            ?.toString(Charsets.UTF_8)
                            ?: return

                    val events =
                        tracker.update(payload)

                    events.forEach { event ->
                        _events.tryEmit(event)
                    }
                }


                override fun deliveryComplete(
                    token: IMqttDeliveryToken?
                ) {
                    // Rien à faire pour le moment.
                }
            }
        )
    }


    private fun onConnected() {

        subscribe()

        _events.tryEmit(
            BambuEvent.PrinterConnected
        )

        scheduleFullStatusRequest()
    }


    private fun subscribe() {

        if (!client.isConnected) {
            return
        }

        client.subscribe(
            reportTopic,
            0
        )
    }


    private fun scheduleFullStatusRequest() {

        pushAllJob?.cancel()

        pushAllJob =
            scope.launch {

                /*
                 * Important :
                 *
                 * Lors de nos tests, envoyer le pushall
                 * immédiatement après la connexion ne
                 * produisait aucune réponse.
                 *
                 * Un délai de 2 secondes fonctionne.
                 */
                delay(2_000)

                if (!client.isConnected) {
                    return@launch
                }

                requestFullStatus()
            }
    }


    fun requestFullStatus() {

        if (!client.isConnected) {
            return
        }

        val payload =
            """
            {
              "pushing": {
                "sequence_id": "0",
                "command": "pushall",
                "version": 1,
                "push_target": 1
              }
            }
            """.trimIndent()

        val message =
            MqttMessage(
                payload.toByteArray(
                    Charsets.UTF_8
                )
            ).apply {
                qos = 0
                isRetained = false
            }

        client.publish(
            requestTopic,
            message
        )
    }


    override fun close() {

        pushAllJob?.cancel()

        if (client.isConnected) {
            client.disconnect()
        }

        client.close()

        scope.cancel()
    }
}