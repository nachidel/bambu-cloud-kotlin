package com.nachidel.bambu.model

data class PrinterDiagnostics(

    /*
     * Etat/stage interne brut Bambu.
     *
     * On ne traduit pas encore ces valeurs :
     * leur signification exacte dépend du firmware
     * et du modèle.
     */
    val stageCurrent: Int? = null,
    val machineStage: Int? = null,
    val printStage: String? = null,
    val printSubStage: Int? = null,

    val machineAction: Int? = null,
    val gcodeAction: Int? = null,

    val jobState: Int? = null,
    val machineState: Int? = null,

    /*
     * Diagnostics / erreurs brutes.
     */
    val messageCode: String? = null,
    val printErrorCode: String? = null,
    val machinePrintErrorCode: String? = null,
    val failReason: String? = null,
    val errorCode: String? = null,
    val secondaryErrorCode: String? = null,
    val xcamStatus: String? = null,

    val hms: List<HmsEntry> = emptyList()
)