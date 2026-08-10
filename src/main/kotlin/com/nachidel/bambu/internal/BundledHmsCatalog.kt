package com.nachidel.bambu.internal

import com.nachidel.bambu.model.HmsCatalog

internal object BundledHmsCatalog {

    private val families =
        listOf(
            "093",
            "094",
            "20P",
            "22E",
            "239",
            "26A",
            "31B"
        )

    val instance: HmsCatalog by lazy {

        val entries =
            buildList {

                families.forEach { family ->

                    val path =
                        "/bambu/hms/hms_fr_$family.json"

                    val content =
                        BundledHmsCatalog::class.java
                            .getResourceAsStream(path)
                            ?.bufferedReader(
                                Charsets.UTF_8
                            )
                            ?.use {
                                it.readText()
                            }
                            ?: error(
                                "Catalogue HMS introuvable : $path"
                            )

                    addAll(
                        HmsCatalogLoader.load(
                            family = family,
                            content = content
                        )
                    )
                }
            }

        HmsCatalog(entries)
    }
}