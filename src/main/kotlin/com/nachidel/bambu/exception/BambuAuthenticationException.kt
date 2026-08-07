package com.nachidel.bambu.exception

class BambuAuthenticationException(
    message: String = "Bambu Cloud authentication failed"
) : RuntimeException(message)