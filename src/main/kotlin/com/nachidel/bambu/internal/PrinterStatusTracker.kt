package com.nachidel.bambu.internal

import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.model.PrinterSnapshot
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

        val newState =
            print.stringValue("gcode_state")
                ?: current.gcodeState

        val newJobId =
            print.stringValue("job_id")
                ?: current.jobId

        val newSubtaskName =
            print.stringValue("subtask_name")
                ?: current.subtaskName

        val newPercent =
            print.intValue("percent")
                ?: print.intValue("mc_percent")
                ?: current.percent

        val newCurrentLayer =
            print.intValue("layer_num")
                ?: threeD?.intValue("layer_num")
                ?: current.currentLayer

        val newTotalLayers =
            print.intValue("total_layer_num")
                ?: threeD?.intValue("total_layer_num")
                ?: current.totalLayers

        val newRemainingTime =
            print.intValue("remain_time")
                ?: print.intValue("mc_remaining_time")
                ?: current.remainingTime

        return current.copy(
            gcodeState = newState,
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

        val newState =
            current.gcodeState
                ?: return

        /*
         * Premier état connu après connexion / pushall.
         *
         * Il initialise le tracker mais ne génère PAS
         * PrinterStarted / PrinterPreparing / PrinterFinished.
         *
         * Cela évite un faux démarrage si le programme
         * est lancé alors qu'une impression est déjà en cours.
         */
        if (!stateInitialized) {

            stateInitialized = true

            return
        }

        val previousState =
            previous.gcodeState

        if (previousState == newState) {
            return
        }

        when (newState) {

            "PREPARE" -> {
                events +=
                    BambuEvent.PrinterPreparing(
                        snapshot = current
                    )
            }

            "RUNNING" -> {
                events +=
                    BambuEvent.PrinterStarted(
                        snapshot = current
                    )
            }

            "FINISH" -> {
                events +=
                    BambuEvent.PrinterFinished(
                        snapshot = current
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