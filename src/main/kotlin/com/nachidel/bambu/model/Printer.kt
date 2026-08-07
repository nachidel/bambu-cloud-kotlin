package com.nachidel.bambu.model

import com.nachidel.bambu.value.SerialNumber

data class Printer(
    val id: String,
    val serial: SerialNumber,
    val name: String,
    val type: PrinterType,
    val state: PrinterState
)