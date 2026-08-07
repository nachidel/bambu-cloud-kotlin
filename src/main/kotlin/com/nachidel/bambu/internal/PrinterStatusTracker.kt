package com.nachidel.bambu.internal

import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.model.PrinterSnapshot
import com.nachidel.bambu.model.PrinterState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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


    fun update(payload: String): List<BambuEvent> {

        val root = runCatching {
            json.parseToJsonElement(payload).jsonObject
        }.getOrNull()
            ?: return emptyList()

        val print = root["print"]?.jsonObject
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

        currentSnapshot = newSnapshot

        val events =
            mutableListOf<BambuEvent>()

        handleStateTransition(
            previous = previousSnapshot,
            current = newSnapshot,
            events = events
        )

        events +=
            BambuEvent.PrinterStatusChanged(
                snapshot = newSnapshot
            )

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

        return base.copy(
            state = newState,
            rawGcodeState = newRawGcodeState,
            jobId = newJobId,
            subtaskName = newSubtaskName,
            percent = newPercent,
            currentLayer = newCurrentLayer,
            totalLayers = newTotalLayers,
            remainingTime = newRemainingTime
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
                        current
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
}