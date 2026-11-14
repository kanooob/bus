package com.example.data.model

/**
 * Résultat détaillé de la synchronisation et de l'extraction du GTFS Zip Idelis.
 */
data class GtfsSyncReport(
    val timestamp: Long = System.currentTimeMillis(),
    val stopsCount: Int = 0,
    val routesCount: Int = 0,
    val tripsCount: Int = 0,
    val stopTimesCount: Int = 0,
    val activePeriod: IdelisPeriod = IdelisPeriod.VERTE,
    val detectedWorks: List<String> = emptyList(),
    val downloadedFiles: List<String> = emptyList(),
    val success: Boolean = true,
    val errorMessage: String? = null,
    val sourceUsed: String = "Direct",
    val usedWaybackFallback: Boolean = false
)
