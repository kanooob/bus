package com.example.data.model

/**
 * Modèle pour un Parking Relais (P+R) du réseau Idelis (dataset liste-des-pr).
 */
data class ParkAndRide(
    val id: String,
    val name: String,
    val commune: String,
    val capacity: Int,
    val connectedLines: List<String>,
    val address: String? = null,
    val isFreeWithTicket: Boolean = true
)

/**
 * Modèle pour une station vélo en libre-service IdeCycle (dataset stations-velo-en-libre-service-idecycle).
 */
data class IdeCycleStation(
    val id: String,
    val name: String,
    val commune: String,
    val totalCapacity: Int,
    val availableBikes: Int,
    val availableDocks: Int,
    val address: String? = null
)

/**
 * Modèle pour les véhicules du parc Idelis (dataset liste-des-vehicules-du-parc-idelis ou SIRI GetVehicleMonitoring).
 */
data class BusVehicle(
    val vehicleId: String,
    val lineRef: String,
    val lineName: String,
    val destination: String,
    val modelType: String, // Ex: "Van Hool A330 FC (Hydrogène)", "Heuliez GX 337", etc.
    val energyType: String, // "Hydrogène", "Hybride", "Diesel Euro 6"
    val delayMinutes: Int = 0,
    val speedKmh: Int = 0,
    val lastStopPassed: String = ""
)

/**
 * Message d'alerte / perturbation trafic (SIRI GetGeneralMessage ou Open Data).
 */
data class TrafficAlert(
    val id: String,
    val title: String,
    val messageText: String,
    val affectedLines: List<String>,
    val severity: String, // "INFO", "WARNING", "DISRUPTION"
    val publishedDate: String,
    val affectedLinePictos: List<String> = emptyList(),
    val deviationLink: String? = null
)

/**
 * Périodes de circulation officielles du réseau Idelis (selon spécification Idelis) :
 * - Orange : Du lundi au vendredi (période scolaire)
 * - Bleue : Du lundi au vendredi (vacances / non scolaire)
 * - Grise : Samedi
 * - Verte : Dimanche et jours fériés
 */
enum class IdelisPeriod(
    val label: String,
    val colorHex: String,
    val colorName: String,
    val description: String
) {
    ORANGE("Période Orange", "F57C00", "Orange", "Lundi au vendredi (scolaire)"),
    BLEUE("Période Bleue", "0288D1", "Bleue", "Lundi au vendredi (non scolaire)"),
    GRISE("Période Grise", "757575", "Grise", "Samedi"),
    VERTE("Période Verte", "388E3C", "Verte", "Dimanche et jours fériés")
}

