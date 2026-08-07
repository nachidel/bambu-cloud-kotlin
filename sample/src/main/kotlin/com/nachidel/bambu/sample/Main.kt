package com.nachidel.bambu.sample

import com.nachidel.bambu.api.BambuCloudClient
import com.nachidel.bambu.auth.AuthenticationResult
import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.exception.BambuAuthenticationException
import com.nachidel.bambu.model.PrinterIssue
import com.nachidel.bambu.model.PrinterState
import com.nachidel.bambu.model.PrinterType
import com.nachidel.bambu.value.AccessToken
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

fun main(): Unit = runBlocking {

    val existingToken =
        System.getenv("BAMBU_TOKEN")
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }

    val bambu =
        BambuCloudClient {

            if (existingToken != null) {
                accessToken =
                    AccessToken(existingToken)
            }
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

                        if (
                            s.state == PrinterState.PAUSED ||
                            s.state == PrinterState.FAILED
                        ) {

                            val d =
                                s.diagnostics

                            println(
                                ">>> DIAG | " +
                                        "stage=${d.stageCurrent} | " +
                                        "mcStage=${d.machineStage} | " +
                                        "printStage=${d.printStage} | " +
                                        "subStage=${d.printSubStage} | " +
                                        "jobState=${d.jobState} | " +
                                        "state=${d.machineState} | " +
                                        "mcAction=${d.machineAction} | " +
                                        "gcodeAction=${d.gcodeAction}"
                            )

                            println(
                                ">>> ERROR | " +
                                        "msg=${d.messageCode} | " +
                                        "print=${d.printErrorCode} | " +
                                        "mc=${d.machinePrintErrorCode} | " +
                                        "fail=${d.failReason} | " +
                                        "err=${d.errorCode} | " +
                                        "err2=${d.secondaryErrorCode} | " +
                                        "xcam=${d.xcamStatus}"
                            )

                            println(
                                ">>> HMS | " +
                                        d.hms.joinToString {
                                            "attr=${it.attr},code=${it.code}"
                                        }
                            )
                        }


                        println(
                            ">>> Etat=${s.state} | " +
                                    "Bambu=${s.rawGcodeState} | " +
                                    "Progression=${s.percent}% | " +
                                    "Couche=${s.currentLayer}/${s.totalLayers} | " +
                                    "Restant=${s.remainingTime} | " +
                                    "Projet=${s.subtaskName}"
                        )
                       /* println(
                            ">>> Etat=${s.state} | " + "Progression=${s.percent}% | " + "Couche=${s.currentLayer}/${s.totalLayers} | " + "Restant=${s.remainingTime} | " + "Projet=${s.subtaskName}"
                        )*/
                    }

                    is BambuEvent.PrinterPaused -> {

                        println(
                            ">>> IMPRESSION EN PAUSE : " +
                                    event.snapshot.subtaskName
                        )

                        when (val issue = event.issue) {

                            is PrinterIssue.FilamentRunout ->
                                println(
                                    ">>> CAUSE : FILAMENT EPUISE " +
                                            "[${issue.rawCode}]"
                                )

                            is PrinterIssue.ForeignObjectOnBuildPlate ->
                                println(
                                    ">>> CAUSE : OBJET DETECTE SUR LE PLATEAU " +
                                            "[${issue.rawCode}]"
                                )

                            is PrinterIssue.Unknown ->
                                println(
                                    ">>> CAUSE : INCONNUE " +
                                            "[${issue.rawCode}]"
                                )

                            null ->
                                println(
                                    ">>> CAUSE : AUCUNE CAUSE IDENTIFIEE"
                                )
                        }
                    }

                    is BambuEvent.PrinterResumed -> {

                        println(
                            ">>> IMPRESSION REPRISE : " +
                                    "${event.snapshot.subtaskName}"
                        )
                    }

                    is BambuEvent.PrinterFailed -> {

                        println(
                            ">>> IMPRESSION INTERROMPUE : " +
                                    "${event.snapshot.subtaskName}"
                        )
                    }
                }
            }
        }

        if (existingToken == null) {

            val email =
                System.getenv("BAMBU_EMAIL")
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?: error(
                        "BAMBU_EMAIL absent"
                    )

            val password =
                System.getenv("BAMBU_PASSWORD")
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?: error(
                        "BAMBU_PASSWORD absent"
                    )

            when (
                val result =
                    bambu.login(
                        email,
                        password
                    )
            ) {

                is AuthenticationResult.Authenticated -> {

                    println(
                        "Authentification reussie."
                    )
                }

                AuthenticationResult.VerificationCodeRequired -> {

                    println(
                        "Code de verification demande."
                    )

                    print(
                        "Code recu par email : "
                    )

                    val code =
                        withContext(Dispatchers.IO) {
                            readln()
                                .trim()
                        }

                    when (
                        val verification =
                            bambu.verifyCode(
                                email,
                                code
                            )
                    ) {

                        is AuthenticationResult.Authenticated -> {

                            println(
                                "Verification reussie."
                            )
                        }

                        AuthenticationResult.VerificationCodeRequired -> {

                            error(
                                "Un nouveau code de verification est demande."
                            )
                        }

                        is AuthenticationResult.Rejected -> {

                            error(
                                "Verification refusee : " +
                                        "${verification.code} - " +
                                        "${verification.message}"
                            )
                        }
                    }
                }

                is AuthenticationResult.Rejected -> {

                    error(
                        "Authentification refusee : " +
                                "${result.code} - " +
                                "${result.message}"
                    )
                }


            }
        }

        val printers =
            bambu.printers()

        val h2c =
            printers.firstOrNull {
                it.type == PrinterType.H2C
            }

        println("Imprimantes liees au compte :")

        printers.forEach { printer ->
            println(
                "- ${printer.name} | " +
                        "${printer.type} | " +
                        "${printer.serial} | " +
                        "${printer.state}"
            )
        }

        println()

        try {
            println("Connexion a Bambu Cloud...")
            bambu.connect()
        } catch (
            e: BambuAuthenticationException
        ) {

            println(
                "Le token Bambu n'est plus valide."
            )

            println(
                "Une nouvelle authentification est necessaire."
            )

            return@runBlocking
        }

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