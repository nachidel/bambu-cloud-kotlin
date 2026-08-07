package com.nachidel.bambu.explorer

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun saveToken(token: String) {
    val gradleDirectory = Path.of(
        System.getProperty("user.home"),
        ".gradle"
    )

    Files.createDirectories(gradleDirectory)

    val propertiesFile = gradleDirectory.resolve("gradle.properties")

    val existingLines =
        if (Files.exists(propertiesFile)) {
            Files.readAllLines(propertiesFile)
                .filterNot { it.startsWith("bambu.token=") }
        } else {
            emptyList()
        }

    val updatedContent = buildString {
        existingLines.forEach {
            appendLine(it)
        }

        appendLine("bambu.token=$token")
    }

    Files.writeString(
        propertiesFile,
        updatedContent,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    )
}

fun main(): Unit = runBlocking {

    val existingToken = System.getenv("BAMBU_TOKEN")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    ExplorerHttpClient().use { client ->

        val token: String = existingToken ?: run {
            println("Aucun token existant, authentification necessaire.")

            val email = System.getenv("BAMBU_EMAIL")
                ?.takeIf { it.isNotBlank() }
                ?: error("Variable BAMBU_EMAIL absente")

            val password = System.getenv("BAMBU_PASSWORD")
                ?.takeIf { it.isNotBlank() }
                ?: error("Variable BAMBU_PASSWORD absente")

            val firstResponse = client.login(
                account = email,
                password = password
            )

            val finalResponse =
                if (firstResponse.loginType == "verifyCode") {

                    print("Code recu par e-mail : ")

                    val code = readlnOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: error("Impossible de lire le code de verification")

                    client.loginWithCode(
                        account = email,
                        code = code
                    )

                } else {
                    firstResponse
                }

            val newToken = finalResponse.accessToken
                ?.takeIf { it.isNotBlank() }
                ?: error(
                    "Echec de connexion : " +
                            "loginType=${finalResponse.loginType}, " +
                            "code=${finalResponse.code}, " +
                            "message=${finalResponse.message}"
                )

            saveToken(newToken)

            println("Connexion reussie.")
            println("Token sauvegarde.")

            newToken
        }

        if (existingToken != null) {
            println("Token existant utilise.")
        }

        val profileJson = client.getRaw(
            path = "/v1/design-user-service/my/preference",
            accessToken = token
        )

        println("Profil recupere.")
        println(profileJson)

        val devicesJson = client.getRaw(
            path = "/v1/iot-service/api/user/bind",
            accessToken = token
        )

        val json = Json {
            ignoreUnknownKeys = true
        }

        val profile =
            json.parseToJsonElement(profileJson)
                .jsonObject

        val userId =
            profile["uid"]
                ?.jsonPrimitive
                ?.content
                ?.toLongOrNull()
                ?: error("UID introuvable")

        val devices =
            json.parseToJsonElement(devicesJson)
                .jsonObject["devices"]
                ?.jsonArray
                ?: error("Liste devices introuvable")

        val h2c =
            devices
                .map { it.jsonObject }
                .firstOrNull {
                    it["dev_product_name"]
                        ?.jsonPrimitive
                        ?.content == "H2C"
                }
                ?: error("H2C introuvable")

        val deviceId =
            h2c["dev_id"]
                ?.jsonPrimitive
                ?.content
                ?: error("dev_id introuvable")

        println()
        println("H2C trouvee.")
        println("Connexion MQTT...")

        CloudMqttExplorer(
            userId = userId,
            deviceId = deviceId,
            accessToken = token
        ).use{ mqtt ->
            mqtt.connect()

            println()
            println("MQTT actif.")
            println("Laisse tourner le programme.")
            println("Ctrl+C pour quitter.")

            awaitCancellation()
        }

    }
}
