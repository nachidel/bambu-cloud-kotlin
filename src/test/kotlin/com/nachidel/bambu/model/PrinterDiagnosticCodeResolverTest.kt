package com.nachidel.bambu.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrinterDiagnosticCodeResolverTest {

    @Test
    fun `active print error is extracted`() {

        val diagnostics =
            PrinterDiagnostics(
                printErrorCode = "201359423",
                machinePrintErrorCode = "0",
                failReason = "0"
            )

        val codes =
            PrinterDiagnosticCodeResolver.resolve(
                diagnostics
            )

        assertEquals(
            1,
            codes.size
        )

        assertEquals(
            PrinterDiagnosticCode.Source.PRINT_ERROR,
            codes.single().source
        )

        assertEquals(
            "0C00803F",
            codes.single().code.value
        )
    }

    @Test
    fun `zero diagnostic codes are ignored`() {

        val diagnostics =
            PrinterDiagnostics(
                printErrorCode = "0",
                machinePrintErrorCode = "0",
                failReason = "0"
            )

        val codes =
            PrinterDiagnosticCodeResolver.resolve(
                diagnostics
            )

        assertEquals(
            emptyList<PrinterDiagnosticCode>(),
            codes
        )
    }

    @Test
    fun `sticky fail reason is preserved separately from print error`() {

        val diagnostics =
            PrinterDiagnostics(
                printErrorCode = "117473297",
                machinePrintErrorCode = "32785",
                failReason = "83918958"
            )

        val codes =
            PrinterDiagnosticCodeResolver.resolve(
                diagnostics
            )

        assertEquals(
            listOf(
                "07008011",
                "00008011",
                "0500806E"
            ),
            codes.map {
                it.code.value
            }
        )

        assertEquals(
            listOf(
                PrinterDiagnosticCode.Source.PRINT_ERROR,
                PrinterDiagnosticCode.Source.MACHINE_PRINT_ERROR,
                PrinterDiagnosticCode.Source.FAIL_REASON
            ),
            codes.map {
                it.source
            }
        )
    }
}