package com.nachidel.bambu.model

import com.nachidel.bambu.internal.BundledHmsCatalog

class PrinterDiagnosticCatalogResolver internal constructor(
    private val catalog: HmsCatalog
) {

    constructor() : this(
        BundledHmsCatalog.instance
    )

    fun resolve(
        diagnostics: PrinterDiagnostics
    ): List<PrinterDiagnosticCatalogMatch> {

        return PrinterDiagnosticCodeResolver
            .resolve(diagnostics)
            .map { diagnostic ->

                PrinterDiagnosticCatalogMatch(
                    diagnostic = diagnostic,
                    entries =
                        catalog.resolveAll(
                            diagnostic.code
                        )
                )
            }
    }
}