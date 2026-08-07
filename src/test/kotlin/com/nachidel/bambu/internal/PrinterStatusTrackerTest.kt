package com.nachidel.bambu.internal

import com.nachidel.bambu.event.BambuEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class PrinterStatusTrackerTest {

    @Test
    fun `initial FINISH synchronizes state without emitting finished event`() {

        val tracker =
            PrinterStatusTracker()

        val events =
            tracker.update(
                """
                {
                  "print": {
                    "gcode_state": "FINISH",
                    "job_id": "job-1",
                    "subtask_name": "Old print",
                    "percent": 100,
                    "layer_num": 149,
                    "total_layer_num": 149,
                    "remain_time": 0
                  }
                }
                """.trimIndent()
            )

        assertEquals(
            "FINISH",
            tracker.snapshot.gcodeState
        )

        assertFalse(
            events.any {
                it is BambuEvent.PrinterFinished
            }
        )
    }


    @Test
    fun `new PREPARE emits preparing and resets previous job data`() {

        val tracker =
            PrinterStatusTracker()

        tracker.update(
            """
            {
              "print": {
                "gcode_state": "FINISH",
                "job_id": "job-1",
                "subtask_name": "Old print",
                "percent": 100,
                "layer_num": 149,
                "total_layer_num": 149,
                "remain_time": 0
              }
            }
            """.trimIndent()
        )

        val events =
            tracker.update(
                """
                {
                  "print": {
                    "gcode_state": "PREPARE",
                    "job_id": "job-2",
                    "subtask_name": "New print"
                  }
                }
                """.trimIndent()
            )

        assertTrue(
            events.any {
                it is BambuEvent.PrinterPreparing
            }
        )

        assertEquals(
            "job-2",
            tracker.snapshot.jobId
        )

        assertEquals(
            "New print",
            tracker.snapshot.subtaskName
        )

        assertNull(
            tracker.snapshot.currentLayer
        )

        assertNull(
            tracker.snapshot.totalLayers
        )

        assertNull(
            tracker.snapshot.remainingTime
        )
    }


    @Test
    fun `PREPARE to RUNNING emits started`() {

        val tracker =
            PrinterStatusTracker()

        tracker.update(
            """
            {
              "print": {
                "gcode_state": "FINISH",
                "job_id": "job-1"
              }
            }
            """.trimIndent()
        )

        tracker.update(
            """
            {
              "print": {
                "gcode_state": "PREPARE",
                "job_id": "job-2"
              }
            }
            """.trimIndent()
        )

        val events =
            tracker.update(
                """
                {
                  "print": {
                    "gcode_state": "RUNNING",
                    "percent": 0,
                    "layer_num": 0,
                    "total_layer_num": 149
                  }
                }
                """.trimIndent()
            )

        assertTrue(
            events.any {
                it is BambuEvent.PrinterStarted
            }
        )
    }


    @Test
    fun `repeated RUNNING does not emit another started event`() {

        val tracker =
            PrinterStatusTracker()

        tracker.update(
            """
            {
              "print": {
                "gcode_state": "PREPARE",
                "job_id": "job-1"
              }
            }
            """.trimIndent()
        )

        tracker.update(
            """
            {
              "print": {
                "gcode_state": "RUNNING",
                "percent": 1
              }
            }
            """.trimIndent()
        )

        val events =
            tracker.update(
                """
                {
                  "print": {
                    "gcode_state": "RUNNING",
                    "percent": 2
                  }
                }
                """.trimIndent()
            )

        assertFalse(
            events.any {
                it is BambuEvent.PrinterStarted
            }
        )

        assertTrue(
            events.any {
                it is BambuEvent.PrinterStatusChanged
            }
        )

        assertEquals(
            2,
            tracker.snapshot.percent
        )
    }

    @Test
    fun `RUNNING to FINISH emits finished`() {

        val tracker = PrinterStatusTracker()

        // Etat initial
        tracker.update(
            """
        {
          "print": {
            "gcode_state": "PREPARE",
            "job_id": "job-1",
            "subtask_name": "Test print"
          }
        }
        """.trimIndent()
        )

        // L'impression démarre
        tracker.update(
            """
        {
          "print": {
            "gcode_state": "RUNNING",
            "percent": 50,
            "layer_num": 75,
            "total_layer_num": 149,
            "remain_time": 60
          }
        }
        """.trimIndent()
        )

        // L'impression se termine
        val events =
            tracker.update(
                """
            {
              "print": {
                "gcode_state": "FINISH",
                "percent": 100,
                "layer_num": 149,
                "total_layer_num": 149,
                "remain_time": 0
              }
            }
            """.trimIndent()
            )

        assertTrue(
            events.any {
                it is BambuEvent.PrinterFinished
            }
        )

        assertEquals(
            "FINISH",
            tracker.snapshot.gcodeState
        )

        assertEquals(
            100,
            tracker.snapshot.percent
        )

        assertEquals(
            149,
            tracker.snapshot.currentLayer
        )

        assertEquals(
            149,
            tracker.snapshot.totalLayers
        )

        assertEquals(
            0,
            tracker.snapshot.remainingTime
        )
    }

    @Test
    fun `partial MQTT update preserves previous snapshot values`() {

        val tracker = PrinterStatusTracker()

        tracker.update(
            """
        {
          "print": {
            "gcode_state": "RUNNING",
            "job_id": "job-1",
            "subtask_name": "Test print",
            "percent": 50,
            "layer_num": 75,
            "total_layer_num": 149,
            "remain_time": 60
          }
        }
        """.trimIndent()
        )

        tracker.update(
            """
        {
          "print": {
            "percent": 51,
            "remain_time": 58
          }
        }
        """.trimIndent()
        )

        val snapshot =
            tracker.snapshot

        assertEquals(
            "RUNNING",
            snapshot.gcodeState
        )

        assertEquals(
            "job-1",
            snapshot.jobId
        )

        assertEquals(
            "Test print",
            snapshot.subtaskName
        )

        assertEquals(
            51,
            snapshot.percent
        )

        assertEquals(
            75,
            snapshot.currentLayer
        )

        assertEquals(
            149,
            snapshot.totalLayers
        )

        assertEquals(
            58,
            snapshot.remainingTime
        )
    }
}