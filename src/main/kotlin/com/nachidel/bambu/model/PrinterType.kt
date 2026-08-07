package com.nachidel.bambu.model

enum class PrinterType {
    H2D,
    H2C,
    X1,
    X1C,
    P1P,
    P1S,
    A1,
    A1_MINI,
    UNKNOWN;

    companion object {

        fun fromProductName(
            value: String?
        ): PrinterType {

            return when (
                value
                    ?.trim()
                    ?.uppercase()
            ) {
                "H2D" -> H2D
                "H2C" -> H2C
                "X1" -> X1
                "X1C" -> X1C
                "P1P" -> P1P
                "P1S" -> P1S
                "A1" -> A1
                "A1 MINI",
                "A1_MINI" -> A1_MINI

                else -> UNKNOWN
            }
        }
    }
}