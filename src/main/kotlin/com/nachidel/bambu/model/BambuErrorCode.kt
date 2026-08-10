package com.nachidel.bambu.model

@JvmInline
value class BambuErrorCode(
    val value: String
) {

    val isNone: Boolean
        get() =
            value.all { it == '0' }

    override fun toString(): String =
        value

    companion object {

        /*
         * Pour les champs MQTT tels que :
         *
         * print_error
         * fail_reason
         * mc_print_error_code
         *
         * Ceux-ci sont observés sous forme décimale.
         */
        fun fromDiagnostic(
            raw: String?
        ): BambuErrorCode? {

            val value =
                raw
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null

            val decimal =
                value.toLongOrNull()

            if (decimal != null) {

                return BambuErrorCode(
                    "%08X".format(decimal)
                )
            }

            return fromHex(value)
        }

        /*
         * Pour un ecode provenant du catalogue HMS
         * ou pour HmsEntry.fullCode.
         */
        fun fromHex(
            raw: String?
        ): BambuErrorCode? {

            val cleaned =
                raw
                    ?.trim()
                    ?.removePrefix("0x")
                    ?.removePrefix("0X")
                    ?.uppercase()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null

            if (
                cleaned.any {
                    it !in '0'..'9' &&
                            it !in 'A'..'F'
                }
            ) {
                return null
            }

            return BambuErrorCode(
                if (cleaned.length <= 8) {
                    cleaned.padStart(8, '0')
                } else {
                    cleaned
                }
            )
        }
    }
}