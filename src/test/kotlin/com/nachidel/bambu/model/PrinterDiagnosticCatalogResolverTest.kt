package com.nachidel.bambu.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrinterDiagnosticCatalogResolverTest {

    private val resolver =
        PrinterDiagnosticCatalogResolver()

    @Test
    fun `filament runout is resolved against bundled catalog`() {

        val diagnostics =
            PrinterDiagnostics(
                printErrorCode = "117473297"
            )

        val results =
            resolver.resolve(
                diagnostics
            )

        assertEquals(
            1,
            results.size
        )

        val result =
            results.single()

        assertEquals(
            PrinterDiagnosticCode.Source.PRINT_ERROR,
            result.diagnostic.source
        )

        assertEquals(
            "07008011",
            result.diagnostic.code.value
        )

        assertTrue(
            result.entries.isNotEmpty()
        )

        assertTrue(
            result.messages.isNotEmpty()
        )
    }

    @Test
    fun `cancelled task has unambiguous catalog message`() {

        val diagnostics =
            PrinterDiagnostics(
                printErrorCode = "50348044"
            )

        val result =
            resolver.resolve(
                diagnostics
            ).single()

        assertEquals(
            "0300400C",
            result.diagnostic.code.value
        )

        assertEquals(
            "La tâche a été annulée.",
            result.unambiguousMessage
        )
    }

    @Test
    fun `nozzle clog preserves multiple catalog variants`() {

        val diagnostics =
            PrinterDiagnostics(
                printErrorCode = "201359423"
            )

        val result =
            resolver.resolve(
                diagnostics
            ).single()

        assertEquals(
            "0C00803F",
            result.diagnostic.code.value
        )

        assertEquals(
            setOf(
                "094",
                "20P",
                "239",
                "31B"
            ),
            result.entries
                .map {
                    it.family
                }
                .toSet()
        )

        assertTrue(
            result.messages.size > 1
        )

        assertNull(
            result.unambiguousMessage
        )
    }

    @Test
    fun `unknown catalog code is still preserved`() {

        val diagnostics =
            PrinterDiagnostics(
                printErrorCode = "305419896"
            )

        val result =
            resolver.resolve(
                diagnostics
            ).single()

        // 305419896 decimal = 12345678 hex
        assertEquals(
            "12345678",
            result.diagnostic.code.value
        )

        assertTrue(
            result.entries.isEmpty()
        )

        assertTrue(
            result.messages.isEmpty()
        )

        assertNull(
            result.unambiguousMessage
        )
    }
}