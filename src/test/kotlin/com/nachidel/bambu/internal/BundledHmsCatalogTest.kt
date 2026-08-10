package com.nachidel.bambu.internal

import com.nachidel.bambu.model.BambuErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BundledHmsCatalogTest {

    private val catalog =
        BundledHmsCatalog.instance

    @Test
    fun `cancelled task code is available`() {

        val code =
            BambuErrorCode.fromHex(
                "0300400C"
            )

        val entries =
            catalog.resolveAll(
                code
            )

        assertTrue(
            entries.isNotEmpty()
        )

        assertEquals(
            7,
            entries.size
        )
    }

    @Test
    fun `cancelled task has one unambiguous french message`() {

        val code =
            BambuErrorCode.fromHex(
                "0300400C"
            )

        assertEquals(
            "La tâche a été annulée.",
            catalog.unambiguousMessage(
                code
            )
        )
    }

    @Test
    fun `foreign object code contains several french variants`() {

        val code =
            BambuErrorCode.fromHex(
                "0500806E"
            )

        val messages =
            catalog.messages(
                code
            )

        assertTrue(
            messages.size > 1
        )

        assertNull(
            catalog.unambiguousMessage(
                code
            )
        )
    }

    @Test
    fun `nozzle detection code exists in several families`() {

        val code =
            BambuErrorCode.fromHex(
                "0C00803F"
            )

        val entries =
            catalog.resolveAll(
                code
            )

        assertEquals(
            setOf(
                "094",
                "20P",
                "239",
                "31B"
            ),
            entries
                .map {
                    it.family
                }
                .toSet()
        )
    }

    @Test
    fun `filament runout exists in all bundled families`() {

        val code =
            BambuErrorCode.fromHex(
                "07008011"
            )

        val entries =
            catalog.resolveAll(
                code
            )

        assertEquals(
            7,
            entries.size
        )
    }
}