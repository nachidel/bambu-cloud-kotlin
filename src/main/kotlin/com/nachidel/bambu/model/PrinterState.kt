package com.nachidel.bambu.model

enum class PrinterState {

    UNKNOWN,
    OFFLINE,
    IDLE,
    PREPARING,
    PRINTING,
    PAUSED,
    FINISHED,
    FAILED,
    ERROR;

    companion object {

        fun fromGcodeState(
            value: String?
        ): PrinterState {

            return when (
                value
                    ?.trim()
                    ?.uppercase()
            ) {
                "PREPARE" -> PREPARING
                "RUNNING" -> PRINTING
                "PAUSE" -> PAUSED
                "FINISH" -> FINISHED
                "FAILED" -> FAILED

                else -> UNKNOWN
            }
        }
    }
}