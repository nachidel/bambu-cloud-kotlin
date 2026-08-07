package com.nachidel.bambu.api

import com.nachidel.bambu.value.AccessToken
import com.nachidel.bambu.value.SerialNumber

class BambuConfiguration {

    var accessToken: AccessToken? = null

    var printer: SerialNumber? = null

    var reconnectAutomatically = true

    var region = Region.GLOBAL

    var timeoutMillis = 30_000L

}