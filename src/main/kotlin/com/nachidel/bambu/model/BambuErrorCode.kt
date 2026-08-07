package com.nachidel.bambu.model

@JvmInline
value class BambuErrorCode(
    val value: String
) {

    val isNone: Boolean
        get() =
            value == "00000000"

    override fun toString(): String =
        value

    companion object {

        fun fromRaw(
            raw: String?
        ): BambuErrorCode? {

            val value =
                raw
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null

            /*
             * Bambu nous transmet ici fail_reason
             * sous forme décimale.
             *
             * Exemple :
             * 83918958 -> 0500806E
             */
            value.toLongOrNull()?.let { decimal ->

                return BambuErrorCode(
                    "%08X".format(decimal)
                )
            }

            /*
             * On accepte également un éventuel
             * code déjà transmis en hexadécimal.
             */
            return BambuErrorCode(
                value
                    .removePrefix("0x")
                    .removePrefix("0X")
                    .uppercase()
                    .padStart(8, '0')
            )
        }
    }
}