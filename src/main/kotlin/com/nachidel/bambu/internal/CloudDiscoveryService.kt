package com.nachidel.bambu.internal

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class CloudDiscoveryService(
    private val client: HttpClient
) {

    suspend fun getUserId(
        accessToken: String
    ): Long {

        val profile =
            client.get(
                "https://api.bambulab.com/v1/design-user-service/my/preference"
            ) {
                bearerAuth(accessToken)
            }.body<ProfileDto>()

        return profile.uid
    }


    suspend fun getDevices(
        accessToken: String
    ): List<CloudDevice> {

        val response =
            client.get(
                "https://api.bambulab.com/v1/iot-service/api/user/bind"
            ) {
                bearerAuth(accessToken)
            }.body<DeviceListDto>()

        return response.devices.map { device ->

            CloudDevice(
                id = device.id,
                name = device.name,
                productName = device.productName,
                online = device.online
            )
        }
    }
}


@Serializable
private data class ProfileDto(
    val uid: Long
)


@Serializable
private data class DeviceListDto(
    val devices: List<DeviceDto> = emptyList()
)


@Serializable
private data class DeviceDto(

    @SerialName("dev_id")
    val id: String,

    val name: String,

    val online: Boolean = false,

    @SerialName("dev_product_name")
    val productName: String? = null
)