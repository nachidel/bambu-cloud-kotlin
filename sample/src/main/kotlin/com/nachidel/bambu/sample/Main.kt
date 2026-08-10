package com.nachidel.bambu.sample

import com.nachidel.bambu.api.BambuCloudClient
import com.nachidel.bambu.auth.AuthenticationResult
import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.exception.BambuAuthenticationException
import com.nachidel.bambu.model.PrinterDiagnosticCatalogResolver
import com.nachidel.bambu.model.PrinterDiagnosticCode
import com.nachidel.bambu.model.PrinterIssue
import com.nachidel.bambu.model.PrinterState
import com.nachidel.bambu.model.PrinterType
import com.nachidel.bambu.value.AccessToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private val logger =
    LoggerFactory.getLogger("BambuSample")

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
            logger.info("Fermeture du client Bambu...")
            bambu.close()
        }
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            closeClient()
        }
    )

    try {

        val diagnosticCatalogResolver =
            PrinterDiagnosticCatalogResolver()

        val eventJob =
            launch(Dispatchers.Default) {

                bambu.events.collect { event ->

                    when (event) {

                        BambuEvent.PrinterConnected -> {
                            logger.info("Connecte au cloud Bambu")
                        }

                        is BambuEvent.PrinterDisconnected -> {
                            logger.warn(
                                "Deconnecte du cloud Bambu : {}",
                                event.cause?.message ?: "cause inconnue"
                            )
                        }

                        is BambuEvent.PrinterPreparing -> {
                            logger.info(
                                "Preparation : {}",
                                event.snapshot.subtaskName
                            )
                        }

                        is BambuEvent.PrinterStarted -> {
                            logger.info(
                                "Impression demarree : {}",
                                event.snapshot.subtaskName
                            )
                        }

                        is BambuEvent.PrinterFinished -> {
                            logger.info(
                                "Impression terminee : {}",
                                event.snapshot.subtaskName
                            )
                        }

                        is BambuEvent.PrinterStatusChanged -> {

                            val s = event.snapshot

                            if (
                                s.state == PrinterState.PAUSED ||
                                s.state == PrinterState.FAILED
                            ) {

                                val d = s.diagnostics

                                logger.debug(
                                    "DIAG stage={} mcStage={} printStage={} subStage={} jobState={} state={} mcAction={} gcodeAction={}",
                                    d.stageCurrent,
                                    d.machineStage,
                                    d.printStage,
                                    d.printSubStage,
                                    d.jobState,
                                    d.machineState,
                                    d.machineAction,
                                    d.gcodeAction
                                )

                                logger.debug(
                                    "ERROR msg={} print={} mc={} fail={} err={} err2={} xcam={}",
                                    d.messageCode,
                                    d.printErrorCode,
                                    d.machinePrintErrorCode,
                                    d.failReason,
                                    d.errorCode,
                                    d.secondaryErrorCode,
                                    d.xcamStatus
                                )

                                logger.debug(
                                    "HMS {}",
                                    d.hms.joinToString {
                                        "attr=${it.attr},code=${it.code}"
                                    }
                                )
                            }

                            logger.debug(
                                "Etat={} Bambu={} Progression={}% Couche={}/{} Restant={} Projet={}",
                                s.state,
                                s.rawGcodeState,
                                s.percent,
                                s.currentLayer,
                                s.totalLayers,
                                s.remainingTime,
                                s.subtaskName
                            )
                        }

                        is BambuEvent.PrinterPaused -> {

                            logger.warn(
                                "Impression en pause : {}",
                                event.snapshot.subtaskName
                            )

                            when (val issue = event.issue) {

                                is PrinterIssue.FilamentRunout -> {
                                    logger.warn(
                                        "Cause : filament epuise [{}]",
                                        issue.rawCode
                                    )
                                }

                                is PrinterIssue.ForeignObjectOnBuildPlate -> {
                                    logger.warn(
                                        "Cause : objet detecte sur le plateau [{}]",
                                        issue.rawCode
                                    )
                                }

                                is PrinterIssue.NozzleClogDetected -> {
                                    logger.warn(
                                        "Cause : obstruction de buse detectee [{}]",
                                        issue.rawCode
                                    )
                                }

                                is PrinterIssue.PrintCancelled -> {
                                    logger.warn(
                                        "Cause : impression annulee [{}]",
                                        issue.rawCode
                                    )
                                }

                                is PrinterIssue.Unknown -> {
                                    logger.warn(
                                        "Cause inconnue [{}]",
                                        issue.rawCode
                                    )
                                }

                                null -> {
                                    logger.warn(
                                        "Aucune cause identifiee pour la pause"
                                    )
                                }
                            }
                        }

                        is BambuEvent.PrinterResumed -> {
                            logger.info(
                                "Impression reprise : {}",
                                event.snapshot.subtaskName
                            )
                        }

                        is BambuEvent.PrinterFailed -> {
                            logger.error(
                                "Impression interrompue : {}",
                                event.snapshot.subtaskName
                            )
                        }

                        is BambuEvent.PrinterDiagnosticsChanged -> {

                            val matches =
                                diagnosticCatalogResolver.resolve(
                                    event.snapshot.diagnostics
                                )

                            if (matches.isEmpty()) {

                                logger.info(
                                    "Diagnostic : plus aucun code actif"
                                )

                            } else {

                                matches.forEach { match ->

                                    val diagnostic =
                                        match.diagnostic

                                    val historical =
                                        diagnostic.source ==
                                                PrinterDiagnosticCode.Source.FAIL_REASON

                                    if (historical) {
                                        logger.warn(
                                            "Diagnostic [{}] code={} (historique possible)",
                                            diagnostic.source,
                                            diagnostic.code.value
                                        )
                                    } else {
                                        logger.warn(
                                            "Diagnostic [{}] code={}",
                                            diagnostic.source,
                                            diagnostic.code.value
                                        )
                                    }

                                    val message =
                                        match.unambiguousMessage

                                    when {

                                        message != null -> {
                                            logger.warn(
                                                "Diagnostic Bambu : {}",
                                                message
                                            )
                                        }

                                        match.messages.isNotEmpty() -> {
                                            match.messages.forEach { possibleMessage ->
                                                logger.warn(
                                                    "Diagnostic Bambu possible : {}",
                                                    possibleMessage
                                                )
                                            }
                                        }

                                        else -> {
                                            logger.debug(
                                                "Aucune description HMS connue pour {}",
                                                diagnostic.code.value
                                            )
                                        }
                                    }
                                }
                            }
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
                    ?: error("BAMBU_EMAIL absent")

            val password =
                System.getenv("BAMBU_PASSWORD")
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?: error("BAMBU_PASSWORD absent")

            when (
                val result =
                    bambu.login(
                        email,
                        password
                    )
            ) {

                is AuthenticationResult.Authenticated -> {
                    logger.info("Authentification reussie")
                }

                AuthenticationResult.VerificationCodeRequired -> {

                    logger.info("Code de verification demande")

                    print("Code recu par email : ")

                    val code =
                        withContext(Dispatchers.IO) {
                            readln().trim()
                        }

                    when (
                        val verification =
                            bambu.verifyCode(
                                email,
                                code
                            )
                    ) {

                        is AuthenticationResult.Authenticated -> {
                            logger.info("Verification reussie")
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

        if (h2c == null) {
            logger.warn(
                "Aucune H2C trouvee parmi les imprimantes liees au compte"
            )
        }

        logger.info(
            "Imprimantes liees au compte : {}",
            printers.size
        )

        printers.forEach { printer ->
            logger.info(
                "{} | {} | {} | {}",
                printer.name,
                printer.type,
                printer.serial,
                printer.state
            )
        }

        try {

            logger.info("Connexion a Bambu Cloud...")
            bambu.connect()

        } catch (
            e: BambuAuthenticationException
        ) {

            logger.error(
                "Le token Bambu n'est plus valide. Une nouvelle authentification est necessaire."
            )

            return@runBlocking
        }

        logger.info(
            "Client actif - tape q puis Entree pour quitter"
        )

        withContext(Dispatchers.IO) {

            while (true) {

                val command =
                    readlnOrNull()
                        ?.trim()
                        ?.lowercase()
                        ?: break

                if (command == "q") {
                    break
                }
            }
        }

        logger.info("Arret demande...")

        eventJob.cancelAndJoin()

    } finally {
        closeClient()
    }
}