package com.nachidel.bambu.model

data class PrinterDiagnosticCode(
    val source: Source,
    val code: BambuErrorCode
) {

    enum class Source {
        PRINT_ERROR,
        MACHINE_PRINT_ERROR,
        FAIL_REASON
    }
}