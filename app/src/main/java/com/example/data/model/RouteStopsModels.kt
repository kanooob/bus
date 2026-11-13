package com.example.data.model

/**
 * Modèle représentant un arrêt individuel sur l'itinéraire ordonné d'une ligne de bus.
 */
data class RouteStopItem(
    val stopName: String,
    val canonicalName: String,
    val sequenceIndex: Int, // Numéro d'ordre officiel dans le sens du parcours (1 = début, N = terminus)
    val isTerminus: Boolean = false,
    val isStart: Boolean = false,
    val isCurrentStop: Boolean = false,
    val connectingLines: List<String> = emptyList(),
    val commune: String? = null
)

/**
 * Détail complet d'une ligne de bus avec l'ensemble de ses arrêts ordonnés
 * selon la direction choisie.
 */
data class RouteLineDetail(
    val lineCode: String,
    val lineLabel: String,
    val routeLongName: String,
    val colorHex: String,
    val textColorHex: String,
    val iconUrl: String? = null,
    val availableDirections: List<String>,
    val selectedDirection: String,
    val terminusName: String,
    val startName: String,
    val totalStopsCount: Int,
    val stops: List<RouteStopItem>, // Liste ordonnée selon isTerminusAtTop
    val isTerminusAtTop: Boolean = true // Par défaut : Terminus en haut et Début en bas
)
