package com.nachidel.bambu.internal

import com.nachidel.bambu.exception.BambuAuthenticationException
import com.nachidel.bambu.model.PrintTask
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class CloudTaskService(
    private val client: HttpClient
) {

    suspend fun getLatestTask(
        accessToken: String,
        deviceId: String
    ): PrintTask? {

        val response =
            client.get(
                "https://api.bambulab.com/v1/user-service/my/tasks"
            ) {
                bearerAuth(accessToken)
                parameter("deviceId", deviceId)
                parameter("limit", 1)
            }

        checkResponse(response)

        return response
            .body<TaskListDto>()
            .hits
            .firstOrNull()
            ?.toModel()
    }

    private fun checkResponse(
        response: HttpResponse
    ) {
        if (response.status == HttpStatusCode.Unauthorized) {
            throw BambuAuthenticationException(
                "Bambu Cloud rejected the access token"
            )
        }

        if (response.status.value !in 200..299) {
            error(
                "Bambu Cloud HTTP error: " +
                        "${response.status.value} ${response.status.description}"
            )
        }
    }
}

@Serializable
private data class TaskListDto(
    val hits: List<TaskDto> = emptyList()
)

@Serializable
private data class TaskDto(
    val id: JsonElement? = null,
    val title: String? = null,
    val cover: String? = null,
    val deviceId: String? = null,
    val plateIndex: Int? = null
) {

    fun toModel(): PrintTask =
        PrintTask(
            id = (id as? JsonPrimitive)?.contentOrNull,
            title = title,
            cover = cover,
            deviceId = deviceId,
            plateIndex = plateIndex
        )
}