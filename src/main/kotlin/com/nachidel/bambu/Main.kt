package com.nachidel.bambu

import com.nachidel.bambu.api.BambuCloudClient
import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.value.AccessToken
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

fun main(): Unit = runBlocking {

    val token =
        System.getenv("BAMBU_TOKEN")?.trim()?.takeIf { it.isNotEmpty() } ?: error("Variable BAMBU_TOKEN absente")

    val bambu = BambuCloudClient {
        accessToken = AccessToken(token)
    }

    val closed = AtomicBoolean(false)

    fun closeClient() {
        if (closed.compareAndSet(false, true)) {
            println()
            println("Fermeture du client Bambu...")
            bambu.close()
        }
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            closeClient()
        })

    try {

        val eventJob = launch(Dispatchers.Default) {

            bambu.events.collect { event ->

                when (event) {

                    BambuEvent.PrinterConnected -> {
                        println(">>> CONNECTE AU CLOUD BAMBU")
                    }

                    is BambuEvent.PrinterDisconnected -> {
                        println(
                            ">>> DECONNECTE : " + "${event.cause?.message ?: "cause inconnue"}"
                        )
                    }

                    is BambuEvent.PrinterPreparing -> {
                        println(
                            ">>> PREPARATION : " + "${event.snapshot.subtaskName}"
                        )
                    }

                    is BambuEvent.PrinterStarted -> {
                        println(
                            ">>> IMPRESSION DEMARREE : " + "${event.snapshot.subtaskName}"
                        )
                    }

                    is BambuEvent.PrinterFinished -> {
                        println(
                            ">>> IMPRESSION TERMINEE : " + "${event.snapshot.subtaskName}"
                        )
                    }

                    is BambuEvent.PrinterStatusChanged -> {

                        val s = event.snapshot

                        println(
                            ">>> Etat=${s.gcodeState} | " + "Progression=${s.percent}% | " + "Couche=${s.currentLayer}/${s.totalLayers} | " + "Restant=${s.remainingTime} | " + "Projet=${s.subtaskName}"
                        )
                    }
                }
            }
        }

        println("Connexion a Bambu Cloud...")

        bambu.connect()

        println("Client actif.")
        println("Tape q puis Entree pour quitter.")

        /*
         * Lecture clavier sur Dispatchers.IO :
         * on ne bloque pas le thread des coroutines.
         */
        withContext(Dispatchers.IO) {

            while (true) {

                val command = readlnOrNull()?.trim()?.lowercase() ?: break

                if (command == "q") {
                    break
                }
            }
        }

        println("Arret demande...")

        /*
         * IMPORTANT :
         * le collect() sur un SharedFlow est infini.
         * Il faut donc annuler sa coroutine.
         */
        eventJob.cancelAndJoin()

    } finally {
        closeClient()
    }
}