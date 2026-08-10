package com.nachidel.bambu.model

data class PrinterDiagnosticCatalogMatch(
    val diagnostic: PrinterDiagnosticCode,
    val entries: List<HmsCatalogEntry>
) {

    val messages: List<String>
        get() =
            entries
                .map {
                    it.message
                }
                .distinct()

    val unambiguousMessage: String?
        get() =
            messages.singleOrNull()
}