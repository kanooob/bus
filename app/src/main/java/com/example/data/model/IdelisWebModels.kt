package com.example.data.model

/**
 * Modèle pour un pôle ou arrêt issu de l'autocomplétion Idelis (?type=777).
 * @param pole Identifiant du pôle pour la requête stop_monitoring (attribut data-pole ou data-value)
 * @param name Libellé complet affiché pour l'arrêt
 */
data class IdelisWebStop(
    val pole: String,
    val name: String
)

/**
 * Modèle représentant un prochain passage extrait du HTML de stop_monitoring.
 */
data class IdelisWebDeparture(
    val lineRef: String,
    val lineName: String,
    val linePictoUrl: String?,
    val direction: String,
    val stopQuai: String?,
    val timeText: String,
    val isRealTime: Boolean,
    val waitMinutes: Int? = null
)

/**
 * Représente une ligne et direction dont certains horaires indiquent que l'arrêt n'est pas desservi.
 */
data class NotServedEntry(
    val lineRef: String,
    val lineName: String,
    val linePictoUrl: String? = null,
    val lineColorHex: String = "#8C935D",
    val direction: String,
    val times: List<String>
)

/**
 * Résultat complet d'un appel stop_monitoring.
 */
data class IdelisWebMonitoringResult(
    val pole: String,
    val departures: List<IdelisWebDeparture>,
    val alertMessage: String? = null,
    val isStopNotServed: Boolean = false,
    val notServedMessage: String? = null,
    val notServedEntries: List<NotServedEntry> = emptyList()
)

/**
 * Modèle d'alerte info trafic extrait de la requête POST requete=infostrafic sur idelis.fr (?type=777).
 */
data class IdelisWebTrafficAlert(
    val id: String,
    val title: String,
    val description: String,
    val affectedLines: List<String>,
    val affectedLinePictos: List<String>,
    val deviationLink: String?,
    val publishedDate: String = ""
)
