package com.nachidel.bambu.internal

import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.model.PrinterSnapshot
import com.nachidel.bambu.model.PrinterState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.nachidel.bambu.model.HmsEntry
import com.nachidel.bambu.model.PrinterDiagnostics
import com.nachidel.bambu.model.PrinterIssueDetector
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import com.nachidel.bambu.model.PrinterDiagnosticCodeResolver

internal class PrinterStatusTracker {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var currentSnapshot =
        PrinterSnapshot()

    private var stateInitialized = false


    val snapshot: PrinterSnapshot
        get() = currentSnapshot


    fun update(
        payload: String
    ): List<BambuEvent> {

        val root =
            runCatching {
                json.parseToJsonElement(payload)
                    .jsonObject
            }.getOrNull()
                ?: return emptyList()

        val print =
            root["print"]
                ?.jsonObject
                ?: return emptyList()

        val previousSnapshot =
            currentSnapshot

        val newSnapshot =
            applyPatch(
                current = previousSnapshot,
                print = print
            )

        if (newSnapshot == previousSnapshot) {
            return emptyList()
        }

        /*
         * On mémorise TOUJOURS le nouveau snapshot,
         * y compris lorsque seuls les diagnostics
         * ont changé.
         */
        currentSnapshot =
            newSnapshot

        val events =
            mutableListOf<BambuEvent>()

        /*
         * On mémorise si le tracker était déjà synchronisé
         * AVANT de traiter ce nouveau snapshot.
         *
         * Le premier snapshot sert uniquement à synchroniser
         * l'état local et ne doit pas déclencher artificiellement
         * un événement diagnostic.
         */
        val wasInitialized =
            stateInitialized

        handleStateTransition(
            previous = previousSnapshot,
            current = newSnapshot,
            events = events
        )

        /*
         * Les codes de diagnostic peuvent évoluer sans que
         * l'état de l'impression change.
         *
         * Exemple réellement important :
         *
         * FAILED
         * print_error = 0
         *
         * puis plus tard :
         *
         * FAILED
         * print_error = 50348044
         *
         * soit 0300400C = tâche annulée.
         */
        if (
            wasInitialized &&
            hasDiagnosticsChanged(
                previous = previousSnapshot.diagnostics,
                current = newSnapshot.diagnostics
            )
        ) {
            events +=
                BambuEvent.PrinterDiagnosticsChanged(
                    snapshot = newSnapshot
                )
        }

        /*
         * On ne publie PrinterStatusChanged que
         * lorsqu'une information métier visible
         * de l'impression a réellement changé.
         *
         * Une simple modification de diagnostics
         * ne doit pas produire :
         *
         * Etat=UNKNOWN | Bambu=null | ...
         */
        if (
            hasStatusChanged(
                previous = previousSnapshot,
                current = newSnapshot
            )
        ) {
            events +=
                BambuEvent.PrinterStatusChanged(
                    snapshot = newSnapshot
                )
        }

        return events
    }


    private fun applyPatch(
        current: PrinterSnapshot,
        print: JsonObject
    ): PrinterSnapshot {

        val threeD =
            print["3D"]?.runCatching {
                jsonObject
            }?.getOrNull()

        val incomingJobId =
            print.stringValue("job_id")

        val incomingSubtaskName =
            print.stringValue("subtask_name")

        val incomingRawGcodeState =
            print.stringValue("gcode_state")

        val jobChanged =
            incomingJobId != null &&
                    current.jobId != null &&
                    incomingJobId != current.jobId

        /*
         * PREPARE est également une frontière fiable
         * entre deux impressions.
         *
         * On réinitialise à l'entrée dans PREPARE afin
         * d'éviter de conserver les couches/progression
         * de l'impression précédente.
         */
        val enteringPrepare =
            incomingRawGcodeState
                ?.equals(
                    "PREPARE",
                    ignoreCase = true
                ) == true &&
                    current.state != PrinterState.PREPARING

        val base =
            if (
                jobChanged ||
                enteringPrepare
            ) {

                PrinterSnapshot(
                    jobId =
                        incomingJobId
                            ?: current.jobId,

                    subtaskName =
                        incomingSubtaskName
                            ?: current.subtaskName
                )

            } else {
                current
            }

        val newRawGcodeState =
            incomingRawGcodeState
                ?: base.rawGcodeState

        val newState =
            PrinterState.fromGcodeState(
                newRawGcodeState
            )

        val newJobId =
            incomingJobId
                ?: base.jobId

        val newSubtaskName =
            incomingSubtaskName
                ?: base.subtaskName

        val newPercent =
            print.intValue("percent")
                ?: print.intValue("mc_percent")
                ?: base.percent

        val newCurrentLayer =
            print.intValue("layer_num")
                ?: threeD?.intValue("layer_num")
                ?: base.currentLayer

        val newTotalLayers =
            print.intValue("total_layer_num")
                ?: threeD?.intValue("total_layer_num")
                ?: base.totalLayers

        val newRemainingTime =
            print.intValue("remain_time")
                ?: print.intValue("mc_remaining_time")
                ?: base.remainingTime

        val newNozzleTemperature =
            print.doubleValue("nozzle_temper")
                ?: base.nozzleTemperature

        val newNozzleTargetTemperature =
            print.doubleValue("nozzle_target_temper")
                ?: base.nozzleTargetTemperature

        val newBedTemperature =
            print.doubleValue("bed_temper")
                ?: base.bedTemperature

        val newBedTargetTemperature =
            print.doubleValue("bed_target_temper")
                ?: base.bedTargetTemperature

        /*
         * Au lancement d'une impression, Bambu peut envoyer
         * une URL signée vers le projet .3mf.
         *
         * On la conserve dans le snapshot afin que
         * bambu-live-automation puisse télécharger la vignette
         * immédiatement, avant expiration de l'URL.
         */
        val newProjectFileUrl =
            print.stringValue("url")
                ?: base.projectFileUrl

        val newPlateIndex =
            print.intValue("plate_idx")
                ?: base.plateIndex

        val diagnostics =
            applyDiagnosticsPatch(
                base = base.diagnostics,
                print = print
            )

        return base.copy(
            state = newState,
            rawGcodeState = newRawGcodeState,
            jobId = newJobId,
            subtaskName = newSubtaskName,
            percent = newPercent,
            currentLayer = newCurrentLayer,
            totalLayers = newTotalLayers,
            remainingTime = newRemainingTime,
            nozzleTemperature = newNozzleTemperature,
            nozzleTargetTemperature = newNozzleTargetTemperature,
            bedTemperature = newBedTemperature,
            bedTargetTemperature = newBedTargetTemperature,
            projectFileUrl = newProjectFileUrl,
            plateIndex = newPlateIndex,
            diagnostics = diagnostics
        )
    }

    private fun applyDiagnosticsPatch(
        base: PrinterDiagnostics,
        print: JsonObject
    ): PrinterDiagnostics {

        val job =
            print["job"] as? JsonObject

        val err2 =
            print["err2"] as? JsonObject

        val hms =
            if (print.containsKey("hms")) {
                parseHms(
                    print["hms"]
                )
            } else {
                base.hms
            }

        return base.copy(

            stageCurrent =
                print.intValue("stg_cur")
                    ?: base.stageCurrent,

            machineStage =
                print.intValue("mc_stage")
                    ?: base.machineStage,

            printStage =
                print.rawValue("mc_print_stage")
                    ?: base.printStage,

            printSubStage =
                print.intValue("mc_print_sub_stage")
                    ?: base.printSubStage,

            machineAction =
                print.intValue("mc_action")
                    ?: base.machineAction,

            gcodeAction =
                print.intValue("print_gcode_action")
                    ?: base.gcodeAction,

            jobState =
                job?.intValue("job_state")
                    ?: base.jobState,

            machineState =
                print.intValue("state")
                    ?: base.machineState,

            messageCode =
                print.rawValue("msg")
                    ?: base.messageCode,

            printErrorCode =
                print.rawValue("print_error")
                    ?: base.printErrorCode,

            machinePrintErrorCode =
                print.rawValue(
                    "mc_print_error_code"
                ) ?: base.machinePrintErrorCode,

            failReason =
                print.rawValue("fail_reason")
                    ?: base.failReason,

            errorCode =
                print.rawValue("err")
                    ?: base.errorCode,

            secondaryErrorCode =
                err2?.rawValue("err_code")
                    ?: base.secondaryErrorCode,

            xcamStatus =
                print.rawValue("xcam_status")
                    ?: base.xcamStatus,

            hms = hms
        )
    }

    private fun handleStateTransition(
        previous: PrinterSnapshot,
        current: PrinterSnapshot,
        events: MutableList<BambuEvent>
    ) {

        if (!stateInitialized) {
            stateInitialized = true
            return
        }

        val previousState =
            previous.state

        val currentState =
            current.state

        if (previousState == currentState) {
            return
        }

        when {

            /*
             * Nouveau travail en préparation.
             */
            currentState == PrinterState.PREPARING -> {

                events +=
                    BambuEvent.PrinterPreparing(
                        current
                    )
            }

            /*
             * PREPARE -> RUNNING
             *
             * C'est le véritable début d'impression
             * observé sur la H2C.
             */
            currentState == PrinterState.PRINTING &&
                    previousState != PrinterState.PAUSED -> {

                /*
                 * RUNNING confirme qu'une impression est en cours.
                 *
                 * PREPARE peut ne pas avoir été observé.
                 * Dans ce cas, par exemple FINISHED -> RUNNING,
                 * on considère quand même qu'une nouvelle impression
                 * vient de démarrer.
                 *
                 * PAUSED -> RUNNING est exclu ici car il s'agit
                 * d'une reprise et non d'un nouveau démarrage.
                 */
                events +=
                    BambuEvent.PrinterStarted(
                        current
                    )
            }

            /*
             * RUNNING -> PAUSE
             *
             * Observé réellement sur la H2C.
             */
            previousState == PrinterState.PRINTING &&
                    currentState == PrinterState.PAUSED -> {

                events +=
                    BambuEvent.PrinterPaused(
                        current,
                        issue =
                            PrinterIssueDetector.detect(
                                current.diagnostics
                            )
                    )
            }

            /*
             * PAUSE -> RUNNING
             *
             * C'est une reprise, PAS un nouveau démarrage.
             */
            previousState == PrinterState.PAUSED &&
                    currentState == PrinterState.PRINTING -> {

                events +=
                    BambuEvent.PrinterResumed(
                        current
                    )
            }

            /*
             * Transition vers FINISH.
             */
            currentState == PrinterState.FINISHED &&
                    (
                            previousState == PrinterState.PRINTING ||
                                    previousState == PrinterState.PAUSED
                            ) -> {

                events +=
                    BambuEvent.PrinterFinished(
                        current
                    )
            }

            currentState == PrinterState.FAILED -> {

                events +=
                    BambuEvent.PrinterFailed(
                        current
                    )
            }
        }
    }

    private fun hasDiagnosticsChanged(
        previous: PrinterDiagnostics,
        current: PrinterDiagnostics
    ): Boolean {

        /*
         * On compare les codes normalisés plutôt que
         * PrinterDiagnostics en entier.
         *
         * Cela évite notamment de considérer :
         *
         * null -> "0"
         *
         * comme l'apparition d'une erreur.
         */
        return PrinterDiagnosticCodeResolver
            .resolve(previous) !=
                PrinterDiagnosticCodeResolver
                    .resolve(current)
    }

    private fun hasStatusChanged(
        previous: PrinterSnapshot,
        current: PrinterSnapshot
    ): Boolean {

        return previous.state != current.state ||
                previous.rawGcodeState != current.rawGcodeState ||
                previous.jobId != current.jobId ||
                previous.subtaskName != current.subtaskName ||
                previous.percent != current.percent ||
                previous.currentLayer != current.currentLayer ||
                previous.totalLayers != current.totalLayers ||
                previous.remainingTime != current.remainingTime ||
                previous.nozzleTemperature != current.nozzleTemperature ||
                previous.nozzleTargetTemperature != current.nozzleTargetTemperature ||
                previous.bedTemperature != current.bedTemperature ||
                previous.bedTargetTemperature != current.bedTargetTemperature ||
                previous.projectFileUrl != current.projectFileUrl ||
                previous.plateIndex != current.plateIndex
    }

    private fun parseHms(
        element: JsonElement?
    ): List<HmsEntry> {

        val array =
            element as? JsonArray
                ?: return emptyList()

        return array.mapNotNull { item ->

            val obj =
                item as? JsonObject
                    ?: return@mapNotNull null

            val attr =
                (obj["attr"] as? JsonPrimitive)
                    ?.longOrNull

            val code =
                (obj["code"] as? JsonPrimitive)
                    ?.longOrNull

            if (
                attr == null &&
                code == null
            ) {
                null
            } else {
                HmsEntry(
                    attr = attr,
                    code = code
                )
            }
        }
    }

    private fun JsonObject.stringValue(
        name: String
    ): String? {

        return this[name]
            ?.jsonPrimitive
            ?.content
            ?.takeIf { it.isNotBlank() }
    }


    private fun JsonObject.intValue(
        name: String
    ): Int? {

        return this[name]
            ?.jsonPrimitive
            ?.intOrNull
    }

    private fun JsonObject.doubleValue(
        name: String
    ): Double? {

        return this[name]
            ?.jsonPrimitive
            ?.doubleOrNull
    }

    private fun JsonObject.rawValue(
        key: String
    ): String? {

        return (this[key] as? JsonPrimitive)
            ?.contentOrNull
    }


}