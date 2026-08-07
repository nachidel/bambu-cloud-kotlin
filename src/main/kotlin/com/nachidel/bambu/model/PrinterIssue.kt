package com.nachidel.bambu.model

sealed interface PrinterIssue {

    val rawCode: String?

    data class FilamentRunout(
        override val rawCode: String?,
        val ams: Int? = null,
        val slot: Int? = null
    ) : PrinterIssue

    data class ForeignObjectOnBuildPlate(
        override val rawCode: String?
    ) : PrinterIssue

    data class Unknown(
        override val rawCode: String?
    ) : PrinterIssue
}