package com.nachidel.bambu.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HmsCatalogLoaderTest {

    @Test
    fun `094 french HMS catalog is loaded`() {

        val content =
            loadCatalog(
                family = "094"
            )

        val entries =
            HmsCatalogLoader.load(
                family = "094",
                content = content
            )

        assertTrue(
            entries.isNotEmpty()
        )
    }

    @Test
    fun `known 094 code is loaded in french`() {

        val content =
            loadCatalog(
                family = "094"
            )

        val entries =
            HmsCatalogLoader.load(
                family = "094",
                content = content
            )

        val entry =
            entries.firstOrNull {
                it.code.value == "07FFC010"
            }

        assertNotNull(entry)

        assertEquals(
            "094",
            entry!!.family
        )

        assertEquals(
            "fr",
            entry.locale
        )

        assertTrue(
            entry.message.contains(
                "Insérez le filament"
            )
        )
    }

    @Test
    fun `all bundled french HMS catalogs are available`() {

        val families =
            listOf(
                "093",
                "094",
                "20P",
                "22E",
                "239",
                "26A",
                "31B"
            )

        families.forEach { family ->

            val content =
                loadCatalog(
                    family = family
                )

            val entries =
                HmsCatalogLoader.load(
                    family = family,
                    content = content
                )

            assertTrue(
                entries.isNotEmpty(),
                "Catalogue HMS vide : $family"
            )
        }
    }

    private fun loadCatalog(
        family: String
    ): String {

        val resource =
            "/bambu/hms/hms_fr_$family.json"

        val stream =
            requireNotNull(
                HmsCatalogLoaderTest::class.java
                    .getResourceAsStream(
                        resource
                    )
            ) {
                "Catalogue HMS introuvable : $resource"
            }

        return stream
            .bufferedReader(
                Charsets.UTF_8
            )
            .use {
                it.readText()
            }
    }
}