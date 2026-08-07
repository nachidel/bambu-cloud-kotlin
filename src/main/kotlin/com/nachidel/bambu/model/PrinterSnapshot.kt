package com.nachidel.bambu.model

data class PrinterSnapshot(

    val state: PrinterState = PrinterState.UNKNOWN,

    /**
     * Valeur brute reçue de Bambu.
     *
     * Conservée volontairement pour pouvoir supporter
     * de nouveaux états sans perdre l'information.
     */
    val rawGcodeState: String? = null,

    val jobId: String? = null,

    val subtaskName: String? = null,

    val percent: Int? = null,

    val currentLayer: Int? = null,

    val totalLayers: Int? = null,

    val remainingTime: Int? = null
)