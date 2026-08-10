package com.nachidel.bambu.internal

import com.nachidel.bambu.model.BambuErrorCode
import com.nachidel.bambu.model.HmsCatalogEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object HmsCatalogLoader {

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun load(
        family: String,
        content: String
    ): List<HmsCatalogEntry> {

        val root =
            runCatching {
                json.parseToJsonElement(content)
                        as? JsonObject
            }.getOrNull()
                ?: return emptyList()

        val data =
            root["data"] as? JsonObject
                ?: return emptyList()

        val deviceError =
            data["device_error"] as? JsonObject
                ?: return emptyList()

        val frenchEntries =
            deviceError["fr"] as? JsonArray
                ?: return emptyList()

        return frenchEntries.mapNotNull { element ->

            val obj =
                element as? JsonObject
                    ?: return@mapNotNull null

            val rawCode =
                (obj["ecode"] as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?: return@mapNotNull null

            val intro =
                (obj["intro"] as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?: return@mapNotNull null

            val code =
                BambuErrorCode.fromHex(
                    rawCode
                ) ?: return@mapNotNull null

            HmsCatalogEntry(
                code = code,
                message = intro,
                locale = "fr",
                family = family
            )
        }
    }
}