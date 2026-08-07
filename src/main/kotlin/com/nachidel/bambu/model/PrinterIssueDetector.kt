package com.nachidel.bambu.model

object PrinterIssueDetector {

    fun detect(
        diagnostics: PrinterDiagnostics
    ): PrinterIssue? {

        val printError =
            BambuErrorCode.fromRaw(
                diagnostics.printErrorCode
            )

        /*
         * Observé lors du manque de filament.
         */
        if (printError?.value == "07008011") {
            return PrinterIssue.FilamentRunout(
                rawCode = printError.value
            )
        }

        /*
         * Observé lors de la détection
         * d'une pièce sur le plateau.
         *
         * Attention : fail_reason semble persistant,
         * donc cette valeur reste un fallback.
         */
        val failReason =
            BambuErrorCode.fromRaw(
                diagnostics.failReason
            )

        if (
            failReason?.value == "0500806E" &&
            diagnostics.printErrorCode.isZeroOrNull() &&
            diagnostics.machinePrintErrorCode.isZeroOrNull()
        ) {
            return PrinterIssue.ForeignObjectOnBuildPlate(
                rawCode = failReason.value
            )
        }

        return null
    }

    private fun String?.isZeroOrNull(): Boolean {

        return this == null ||
                this == "0" ||
                this == "00000000"
    }
}