package com.nachidel.bambu.model

data class HmsCatalogEntry(
    val code: BambuErrorCode,
    val message: String,
    val locale: String,
    val family: String
)