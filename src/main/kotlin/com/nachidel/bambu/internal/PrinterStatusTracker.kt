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
import com.nachidel.bambu.model.PauseReason
import com.nachidel.bambu.model.PrinterDiagnostics
import com.nachidel.bambu.model.PrinterIssueDetector
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

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
         * Les transitions d'état restent traitées
         * indépendamment de PrinterStatusChanged.
         */
        handleStateTransition(
            previous = previousSnapshot,
            current = newSnapshot,
            events = events
        )

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

        val jobChanged =
            incomingJobId != null &&
                    current.jobId != null &&
                    incomingJobId != current.jobId

        /*
         * Nouveau job :
         * on efface toutes les informations appartenant
         * à l'impression précédente.
         */
        val base =
            if (jobChanged) {
                PrinterSnapshot(
                    jobId = incomingJobId
                )
            } else {
                current
            }

        val newRawGcodeState =
            print.stringValue("gcode_state")
                ?: base.rawGcodeState

        val newState =
            PrinterState.fromGcodeState(
                newRawGcodeState
            )

        val newJobId =
            incomingJobId
                ?: base.jobId

        val newSubtaskName =
            print.stringValue("subtask_name")
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
            previousState == PrinterState.PREPARING &&
                    currentState == PrinterState.PRINTING -> {

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
            currentState == PrinterState.FINISHED -> {

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
                previous.remainingTime != current.remainingTime
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

    private fun JsonObject.rawValue(
        key: String
    ): String? {

        return (this[key] as? JsonPrimitive)
            ?.contentOrNull
    }


}