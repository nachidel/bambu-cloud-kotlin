package com.nachidel.bambu.explorer

import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.mqtt.BambuCloudMqttClient
import kotlinx.coroutines.*

class CloudMqttExplorer(
    userId: Long,
    deviceId: String,
    accessToken: String
) : AutoCloseable {

    private val scope =
        CoroutineScope(
            Job() + Dispatchers.Default
        )

    private val mqttClient =
        BambuCloudMqttClient(
            userId = userId,
            deviceId = deviceId,
            accessToken = accessToken
        )


    init {

        scope.launch {

            mqttClient.events.collect { event ->

                displayEvent(event)
            }
        }
    }


    fun connect() {
        mqttClient.connect()
    }


    private fun displayEvent(
        event: BambuEvent
    ) {

        when (event) {

            BambuEvent.PrinterConnected -> {
                println(">>> MQTT CONNECTE")
            }


            is BambuEvent.PrinterDisconnected -> {
                println(
                    ">>> MQTT DECONNECTE : " +
                            "${event.cause?.message ?: "cause inconnue"}"
                )
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


    override fun close() {

        mqttClient.close()

        scope.cancel()
    }
}