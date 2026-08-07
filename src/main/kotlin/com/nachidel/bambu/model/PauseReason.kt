package com.nachidel.bambu.model

enum class PauseReason {

    /**
     * Objet étranger détecté sur le plateau.
     *
     * Code Bambu observé sur H2C :
     * 0500806E
     */
    FOREIGN_OBJECT_ON_BUILD_PLATE,

    FILAMENT_RUNOUT,

    /**
     * La machine est en pause mais nous ne
     * connaissons pas encore précisément la cause.
     */
    UNKNOWN;

    companion object {

        fun fromDiagnostics(
            diagnostics: PrinterDiagnostics
        ): PauseReason {

            /*
             * 1. print_error semble représenter
             *    l'erreur actuellement active.
             */
            val printError =
                BambuErrorCode.fromRaw(
                    diagnostics.printErrorCode
                )

            when (printError?.value) {

                /*
                 * AMS1 filament ran out.
                 *
                 * Il existe aussi les variantes
                 * AMS2/AMS3/AMS4.
                 */
                "07008011",
                "07018011",
                "07028011",
                "07038011",
                "07FF8011" ->
                    return FILAMENT_RUNOUT
            }

            /*
             * 2. HMS donne souvent une information
             *    beaucoup plus précise.
             */
            if (
                diagnostics.hms.any {
                    isFilamentRunoutHms(
                        it.fullCode
                    )
                }
            ) {
                return FILAMENT_RUNOUT
            }

            /*
             * 3. fail_reason est un fallback.
             *
             * Attention : nous avons observé sur H2C
             * qu'il peut conserver une ancienne valeur.
             */
            val failReason =
                BambuErrorCode.fromRaw(
                    diagnostics.failReason
                )

            if (
                failReason?.value ==
                "0500806E"
            ) {
                return FOREIGN_OBJECT_ON_BUILD_PLATE
            }

            return UNKNOWN
        }


        private fun isFilamentRunoutHms(
            code: String?
        ): Boolean {

            if (code == null) {
                return false
            }

            /*
             * xxxx20/21/22/23 + 00020001
             *
             * Slot filament runout.
             */
            return code.matches(
                Regex(
                    """07[0-3]02[0-3]0000020001"""
                )
            )
        }
    }
}