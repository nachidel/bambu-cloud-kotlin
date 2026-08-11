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

    val remainingTime: Int? = null,

    /**
     * Températures remontées par le flux Bambu Cloud / MQTT.
     *
     * Les valeurs restent null tant qu'elles n'ont pas encore
     * été reçues dans un push_status.
     */
    /**
     * Champs génériques Bambu au niveau racine du push_status.
     * Leur association à une tête précise n'est volontairement
     * pas déduite ici.
     */
    val nozzleTemperature: Double? = null,

    val nozzleTargetTemperature: Double? = null,

    /**
     * H2C : températures des deux entrées d'extrudeur physiques.
     *
     * Bambu les transmet dans device.extruder.info avec id 0 et 1.
     * Le champ temp est un entier compacté :
     * - 16 bits bas   = température actuelle
     * - 16 bits hauts = température cible
     *
     * IMPORTANT :
     * nozzleTemperature/nozzleTargetTemperature (champs racine
     * nozzle_temper / nozzle_target_temper) sont conservés pour
     * compatibilité, mais on ne suppose PAS qu'ils représentent
     * systématiquement la tête active.
     */
    val head0Temperature: Double? = null,

    val head0TargetTemperature: Double? = null,

    val head1Temperature: Double? = null,

    val head1TargetTemperature: Double? = null,

    val bedTemperature: Double? = null,

    val bedTargetTemperature: Double? = null,

    /**
     * URL signée du projet 3MF envoyée par Bambu au démarrage
     * d'une impression. Elle est temporaire : le consommateur
     * doit la télécharger dès qu'elle apparaît.
     */
    val projectFileUrl: String? = null,

    /**
     * Plateau réellement lancé dans le projet 3MF.
     * Utilisé notamment pour choisir Metadata/plate_N.png.
     */
    val plateIndex: Int? = null,

    val diagnostics: PrinterDiagnostics =
        PrinterDiagnostics()
)