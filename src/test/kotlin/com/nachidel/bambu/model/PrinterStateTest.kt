package com.nachidel.bambu.model

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
}