package com.nachidel.bambu.model

object PrinterDiagnosticCodeResolver {

    fun resolve(
        diagnostics: PrinterDiagnostics
    ): List<PrinterDiagnosticCode> {

        return buildList {

            addDiagnostic(
                source = PrinterDiagnosticCode.Source.PRINT_ERROR,
                raw = diagnostics.printErrorCode
            )

            addDiagnostic(
                source = PrinterDiagnosticCode.Source.MACHINE_PRINT_ERROR,
                raw = diagnostics.machinePrintErrorCode
            )

            addDiagnostic(
                source = PrinterDiagnosticCode.Source.FAIL_REASON,
                raw = diagnostics.failReason
            )
        }
    }

    private fun MutableList<PrinterDiagnosticCode>.addDiagnostic(
        source: PrinterDiagnosticCode.Source,
        raw: String?
    ) {

        val code =
            BambuErrorCode.fromDiagnostic(
                raw
            ) ?: return

        if (code.isNone) {
            return
        }

        add(
            PrinterDiagnosticCode(
                source = source,
                code = code
            )
        )
    }
}