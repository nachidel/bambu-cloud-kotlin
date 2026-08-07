package com.nachidel.bambu.explorer

import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.internal.PrinterStatusTracker
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import javax.net.ssl.SSLContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CloudMqttExplorer(
    private val userId: Long,
    private val deviceId: String,
    private val accessToken: String
) : AutoCloseable {

    private val broker =
        "ssl://us.mqtt.bambulab.com:8883"

    private val client = MqttClient(
        broker,
        "bambu-kotlin-${UUID.randomUUID()}",
        MemoryPersistence()
    )

    private val reportTopic =
        "device/$deviceId/report"

    private val requestTopic =
        "device/$deviceId/request"

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tracker = PrinterStatusTracker()

    fun connect() {

        client.setCallback(
            object : MqttCallbackExtended {

                override fun connectComplete(
                    reconnect: Boolean,
                    serverURI: String?
                ) {
                    println(
                        if (reconnect)
                            "MQTT reconnecte."
                        else
                            "MQTT connecte."
                    )

                    subscribe()
                }

                override fun connectionLost(cause: Throwable?) {
                    println(
                        "Connexion MQTT perdue : ${cause?.message}"
                    )
                }

                override fun messageArrived(
                    topic: String?,
                    message: MqttMessage?
                ) {

                    val payload =
                        message
                            ?.payload
                            ?.toString(Charsets.UTF_8)
                            ?: return

                    val events =
                        tracker.update(payload)

                    if (events.isEmpty()) {
                        return
                    }

                    events.forEach { event ->

                        when (event) {

                            is BambuEvent.PrinterConnected -> {
                                println(">>> IMPRIMANTE CONNECTEE")
                            }

                            is BambuEvent.PrinterDisconnected -> {
                                println(">>> IMPRIMANTE DECONNECTEE")
                            }
                            is BambuEvent.PrinterPreparing -> {

                                println()
                                println("================================")
                                println(">>> IMPRESSION EN PREPARATION")
                                println("Projet : ${event.snapshot.subtaskName}")
                                println("================================")
                            }


                            is BambuEvent.PrinterStarted -> {

                                println()
                                println("================================")
                                println(">>> IMPRESSION DEMARREE")
                                println("Projet : ${event.snapshot.subtaskName}")
                                println("================================")
                            }


                            is BambuEvent.PrinterFinished -> {

                                println()
                                println("================================")
                                println(">>> IMPRESSION TERMINEE")
                                println("Projet : ${event.snapshot.subtaskName}")
                                println("================================")
                            }


                            is BambuEvent.PrinterStatusChanged -> {

                                val s =
                                    event.snapshot

                                println(
                                    "Etat=${s.gcodeState} | " +
                                            "Progression=${s.percent}% | " +
                                            "Couche=${s.currentLayer}/${s.totalLayers} | " +
                                            "Restant=${s.remainingTime} | " +
                                            "Projet=${s.subtaskName}"
                                )
                            }


                        }
                    }
                }

                override fun deliveryComplete(
                    token: IMqttDeliveryToken?
                ) {
                    // Rien pour l'instant :
                    // on ne publie aucune commande.
                }
            }
        )

        val options = MqttConnectOptions().apply {

            userName = "u_$userId"

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

        println("Connexion au cloud MQTT Bambu...")
        println("Broker : $broker")
        println("Topic  : $reportTopic")

        client.connect(options)
    }

    private fun subscribe() {

        if (!client.isConnected) {
            return
        }

        println("Souscription : $reportTopic")

        try {
            client.subscribe(reportTopic, 0)
            println("Souscription OK.")
        } catch (e: Exception) {
            println("ERREUR souscription MQTT : ${e.message}")
            return
        }

        scope.launch {

            println("Attente de 2 secondes avant pushall...")

            delay(2_000)

            if (!client.isConnected) {
                println("MQTT deconnecte avant le pushall.")
                return@launch
            }

            requestFullStatus()
        }
    }

    private fun requestFullStatus() {

        if (!client.isConnected) {
            return
        }

        val payload = """
        {
            "pushing": {
                "sequence_id": "0",
                "command": "pushall",
                "version": 1,
                "push_target": 1
            }
        }
    """.trimIndent()

        val message = MqttMessage(
            payload.toByteArray(Charsets.UTF_8)
        ).apply {
            qos = 0
            isRetained = false
        }

        println("Demande de l'etat complet de la H2C...")

        println("Publication pushall sur : $requestTopic")

        try {
            client.publish(
                requestTopic,
                message
            )

            println("Pushall transmis au client MQTT.")
        } catch (e: Exception) {
            println("ERREUR publication pushall : ${e.message}")
        }
    }

    override fun close() {

        scope.cancel()

        if (client.isConnected) {
            client.disconnect()
        }

        client.close()
    }
}