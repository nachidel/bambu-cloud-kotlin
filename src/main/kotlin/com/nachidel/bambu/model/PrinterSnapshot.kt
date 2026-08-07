package com.nachidel.bambu.model

data class PrinterSnapshot(
    val gcodeState: String? = null,
    val jobId: String? = null,
    val subtaskName: String? = null,
    val percent: Int? = null,
    val currentLayer: Int? = null,
    val totalLayers: Int? = null,
    val remainingTime: Int? = null
)