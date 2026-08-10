package com.nachidel.bambu.model

object PrinterIssueDetector {

    fun detect(
        diagnostics: PrinterDiagnostics
    ): PrinterIssue? {

        /*
         * Erreur active de l'impression.
         *
         * Les valeurs MQTT sont observées
         * sous forme décimale.
         */
        val printError =
            BambuErrorCode.fromDiagnostic(
                diagnostics.printErrorCode
            )

        when (printError?.value) {

            "0300400C" ->
                return PrinterIssue.PrintCancelled(
                    rawCode = printError.value
                )

            "0C00803F" ->
                return PrinterIssue.NozzleClogDetected(
                    rawCode = printError.value
                )

            "07008011" ->
                return PrinterIssue.FilamentRunout(
                    rawCode = printError.value
                )
        }

        /*
         * fail_reason semble pouvoir conserver
         * une ancienne valeur sur la H2C.
         *
         * On ne l'utilise donc qu'en fallback.
         */
        val failReason =
            BambuErrorCode.fromDiagnostic(
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