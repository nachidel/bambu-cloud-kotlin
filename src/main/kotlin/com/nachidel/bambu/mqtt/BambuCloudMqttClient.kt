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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import javax.net.ssl.SSLContext
import kotlin.time.Duration.Companion.milliseconds

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

    private val client =
        MqttClient(
            broker,
            "bambu-kotlin-${UUID.randomUUID()}",
            MemoryPersistence()
        )

    private var initialStatusJob: Job? = null
    private var statusPollingJob: Job? = null

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
                    client.subscribe(reportTopic, 0)

                    _events.tryEmit(
                        BambuEvent.PrinterConnected
                    )

                    initialStatusJob?.cancel()

                    initialStatusJob =
                        scope.launch {

                            /*
                             * Lors de nos tests, envoyer le pushall
                             * immédiatement après la connexion ne
                             * produisait aucune réponse.
                             *
                             * Un délai de 2 secondes fonctionne.
                             */
                            delay(2_000.milliseconds)

                            if (!client.isConnected) {
                                return@launch
                            }

                            // On démarre d'abord le polling afin qu'une
                            // erreur ponctuelle du premier pushall ne
                            // l'empêche pas de fonctionner ensuite.
                            startStatusPolling()

                            requestFullStatus()
                        }
                }

                override fun connectionLost(
                    cause: Throwable?
                ) {
                    initialStatusJob?.cancel()
                    initialStatusJob = null

                    statusPollingJob?.cancel()
                    statusPollingJob = null

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

    private fun startStatusPolling() {

        if (statusPollingJob?.isActive == true) {
            return
        }

        statusPollingJob =
            scope.launch {
                while (isActive) {

                    delay(10_000.milliseconds)

                    if (!client.isConnected) {
                        continue
                    }

                    try {
                        requestFullStatus()
                    } catch (_: Exception) {
                        // Le polling ne doit jamais tuer le client MQTT.
                    }
                }
            }
    }

    override fun close() {

        initialStatusJob?.cancel()
        initialStatusJob = null

        statusPollingJob?.cancel()
        statusPollingJob = null

        if (client.isConnected) {
            client.disconnect()
        }

        client.close()

        scope.cancel()
    }
}