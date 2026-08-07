package com.nachidel.bambu.api

import com.nachidel.bambu.value.AccessToken

class BambuConfiguration {

    var accessToken: AccessToken? = null

    var reconnectAutomatically = true

    var region = Region.GLOBAL

    var timeoutMillis = 30_000L

}