package com.nachidel.bambu.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PrinterTypeTest {

    @Test
    fun `H2C product name maps to H2C`() {

        assertEquals(
            PrinterType.H2C,
            PrinterType.fromProductName("H2C")
        )
    }

    @Test
    fun `unknown product maps to UNKNOWN`() {

        assertEquals(
            PrinterType.UNKNOWN,
            PrinterType.fromProductName(
                "Future Bambu Printer"
            )
        )
    }
}