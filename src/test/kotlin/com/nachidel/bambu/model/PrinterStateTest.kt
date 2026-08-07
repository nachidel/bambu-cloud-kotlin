package com.nachidel.bambu.model

import com.nachidel.bambu.event.BambuEvent
import com.nachidel.bambu.internal.PrinterStatusTracker
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
class PrinterStateTest {
    @Test
    fun `PAUSE maps to paused`() {

        assertEquals(
            PrinterState.PAUSED,
            PrinterState.fromGcodeState(
                "PAUSE"
            )
        )
    }

    @Test
    fun `FAILED maps to failed`() {

        assertEquals(
            PrinterState.FAILED,
            PrinterState.fromGcodeState(
                "FAILED"
            )
        )
    }

    @Test
    fun `RUNNING to FAILED emits failed`() {

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
            "gcode_state": "RUNNING"
          }
        }
        """.trimIndent()
        )

        val events =
            tracker.update(
                """
            {
              "print": {
                "gcode_state": "FAILED"
              }
            }
            """.trimIndent()
            )

        assertTrue(
            events.any {
                it is BambuEvent.PrinterFailed
            }
        )

        assertEquals(
            PrinterState.FAILED,
            tracker.snapshot.state
        )
    }
}