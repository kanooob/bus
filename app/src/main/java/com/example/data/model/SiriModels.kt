package com.example.data.model

/**
 * Modèle extrait du flux XML SIRI (siri:MonitoredStopVisit).
 */
data class SiriMonitoredStopVisit(
    val lineRef: String,
    val publishedLineName: String,
    val destinationName: String,
    val aimedDepartureTime: String,      // Horodatage ISO (ex: "2026-11-06T17:16:00+01:00")
    val expectedDepartureTime: String,   // Horodatage ISO (ex: "2026-11-06T17:17:00+01:00")
    val departureStatus: String,         // "onTime", "delayed", "early", etc.
    val stopPointRef: String? = null,
    val vehicleRef: String? = null
)

/**
 * Statut de ponctualité calculé entre le théorique et le temps réel.
 */
enum class DepartureStatus(val label: String) {
    ON_TIME("À l'heure"),
    DELAYED("En retard"),
    EARLY("En avance"),
    THEORETICAL_ONLY("Horaire théorique (GTFS)"),
    CANCELLED("Supprimé")
}

/**
 * Modèle comparatif unifié affiché sur le panneau d'affichage.
 * Met en évidence :
 * 1. L'heure théorique (GTFS / AimedDepartureTime)
 * 2. L'heure estimée temps réel (SIRI / ExpectedDepartureTime)
 * 3. L'écart exact en minutes (diff = Expected - Aimed)
 */
data class DepartureComparison(
    val id: String,
    val lineRef: String,
    val lineName: String,
    val destinationName: String,
    val routeColorHex: String,
    val routeTextColorHex: String,
    val aimedDepartureTimeFormatted: String,      // "17:16"
    val expectedDepartureTimeFormatted: String?,   // "17:17" ou null si non dispo
    val delayMinutes: Int,                         // 0, +1, +5, -2, etc.
    val status: DepartureStatus,
    val isRealTime: Boolean,                       // true si SIRI actif, false si fallback GTFS
    val minutesUntilDeparture: Long,               // Estimation de temps d'attente
    val comparisonLabel: String,                   // "17:16 GTFS ➔ 17:17 Temps réel [+1 min]"
    val iconUrl: String? = null                    // URL du PNG officiel (liste-des-icones-des-lignes)
)
