package com.nachidel.bambu.event

import com.nachidel.bambu.model.PrinterSnapshot

sealed interface BambuEvent {

    data object PrinterConnected : BambuEvent

    data class PrinterDisconnected(
        val cause: Throwable? = null
    ) : BambuEvent


    sealed interface PrinterStatusEvent : BambuEvent {
        val snapshot: PrinterSnapshot
    }


    data class PrinterStatusChanged(
        override val snapshot: PrinterSnapshot
    ) : PrinterStatusEvent


    data class PrinterPreparing(
        override val snapshot: PrinterSnapshot
    ) : PrinterStatusEvent


    data class PrinterStarted(
        override val snapshot: PrinterSnapshot
    ) : PrinterStatusEvent


    data class PrinterFinished(
        override val snapshot: PrinterSnapshot
    ) : PrinterStatusEvent

    data class PrinterPaused(
        override val snapshot: PrinterSnapshot
    ) : PrinterStatusEvent

    data class PrinterResumed(
        override val snapshot: PrinterSnapshot
    ) : PrinterStatusEvent
}