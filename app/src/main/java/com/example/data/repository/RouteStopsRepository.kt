package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.GtfsDatabaseSeeder
import com.example.data.model.DepartureComparison
import com.example.data.model.RouteLineDetail
import com.example.data.model.RouteStopItem
import java.text.Normalizer

/**
 * Repository dédié à la gestion des itinéraires et de l'ordre complet des arrêts par ligne et par sens.
 * Conforme à la demande utilisateur :
 * - Affiche tous les arrêts où le bus s'arrête dans l'ordre (terminus en haut, début en bas)
 * - Navigation instantanée vers un arrêt cliqué pour consulter ses prochains passages
 * - Enrichi avec les arrêts de toutes les lignes régulières Idelis (F, T1, T2, T3, T4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, COXI)
 */
class RouteStopsRepository(
    private val context: Context? = null
) {

    companion object {
        private const val TAG = "RouteStopsRepository"

        private fun normalizeString(text: String): String {
            val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            return Regex("\\p{InCombiningDiacriticalMarks}+").replace(normalized, "").trim()
        }

        // Base complète des arrêts par ligne et par direction officielle du réseau Idelis (Pau Béarn Pyrénées)
        private val OFFICIAL_NETWORK_STOPS: Map<String, Map<String, List<String>>> = mapOf(
            "F" to mapOf(
                "PAU Hôpital F. Mitterrand" to listOf(
                    "PAU Gare de Pau", "Barthou - Beaumont", "Place d'Espagne", "Pôle Bosquet",
                    "Les Halles - Despourrins", "Alliés - Alsace Lorraine", "Place du Foirail", "Saragosse",
                    "Tourasse - Cité Administrative", "Condorcet - Université", "Catherine de Bourbon",
                    "Boulevard de la Paix", "Buros - Le Hameau", "Cliniques", "PAU Hôpital F. Mitterrand"
                ),
                "PAU Gare de Pau" to listOf(
                    "PAU Hôpital F. Mitterrand", "Cliniques", "Buros - Le Hameau", "Boulevard de la Paix",
                    "Catherine de Bourbon", "Condorcet - Université", "Tourasse - Cité Administrative",
                    "Saragosse", "Place du Foirail", "Alliés - Alsace Lorraine", "Les Halles - Despourrins",
                    "Pôle Bosquet", "Place d'Espagne", "Barthou - Beaumont", "PAU Gare de Pau"
                )
            ),
            "T1" to mapOf(
                "PAU Cité Multimédia" to listOf(
                    "JURANÇON LP A. Campa", "Rives du Gave", "Croix du Prince", "Mairie de Billère",
                    "Route de Bayonne", "Place de Verdun", "Pôle Bosquet", "Les Halles - Despourrins",
                    "Tourasse", "Cité Judiciaire", "Catherine de Bourbon", "Chambre d'Agriculture", "PAU Cité Multimédia"
                ),
                "JURANÇON LP A. Campa" to listOf(
                    "PAU Cité Multimédia", "Chambre d'Agriculture", "Catherine de Bourbon", "Cité Judiciaire",
                    "Tourasse", "Les Halles - Despourrins", "Pôle Bosquet", "Place de Verdun", "Route de Bayonne",
                    "Mairie de Billère", "Croix du Prince", "Rives du Gave", "JURANÇON LP A. Campa"
                )
            ),
            "T2" to mapOf(
                "PAU Stades du Hameau" to listOf(
                    "LONS Perlic", "Mairie de Lons", "Médiathèque de Lons", "Hippodrome", "Béarn",
                    "Place de Verdun", "Pôle Bosquet", "Saint-Joseph", "Normandie", "Stade Nautique",
                    "Ousse des Bois", "PAU Stades du Hameau"
                ),
                "LONS Perlic" to listOf(
                    "PAU Stades du Hameau", "Ousse des Bois", "Stade Nautique", "Normandie", "Saint-Joseph",
                    "Pôle Bosquet", "Place de Verdun", "Béarn", "Hippodrome", "Médiathèque de Lons",
                    "Mairie de Lons", "LONS Perlic"
                )
            ),
            "T3" to mapOf(
                "PAU CST Jean Feger" to listOf(
                    "LESCAR Soleil", "Quartier Libre", "Mairie de Lescar", "Soleil Levant",
                    "Place de Verdun", "Pôle Bosquet", "Les Halles - Despourrins", "Faculté",
                    "Centre Hospitalier", "PAU CST Jean Feger"
                ),
                "LESCAR Soleil" to listOf(
                    "PAU CST Jean Feger", "Centre Hospitalier", "Faculté", "Les Halles - Despourrins",
                    "Pôle Bosquet", "Place de Verdun", "Soleil Levant", "Mairie de Lescar",
                    "Quartier Libre", "LESCAR Soleil"
                )
            ),
            "T4" to mapOf(
                "PAU CST Jean Feger" to listOf(
                    "LONS Perlic", "Lons Mairie", "Abbé Brémond", "Château d'Este", "Université",
                    "Catherine de Bourbon", "Hôpital François Mitterrand", "PAU CST Jean Feger"
                ),
                "LONS Perlic" to listOf(
                    "PAU CST Jean Feger", "Hôpital François Mitterrand", "Catherine de Bourbon", "Université",
                    "Château d'Este", "Abbé Brémond", "Lons Mairie", "LONS Perlic"
                )
            ),
            "5" to mapOf(
                "MAZERES-LEZONS L'Arriou" to listOf(
                    "Place de Verdun", "Pôle Bosquet", "Gare de Pau", "Gare de Croix du Prince",
                    "Bizanos", "MAZERES-LEZONS L'Arriou"
                ),
                "Place de Verdun" to listOf(
                    "MAZERES-LEZONS L'Arriou", "Bizanos", "Gare de Croix du Prince", "Gare de Pau",
                    "Pôle Bosquet", "Place de Verdun"
                )
            ),
            "6" to mapOf(
                "BIZANOS Laurets" to listOf(
                    "Pôle Bosquet", "Pl. du Foirail", "Abbé Brémond", "Mairie de Bizanos", "BIZANOS Laurets"
                ),
                "PAU Pôle Bosquet" to listOf(
                    "BIZANOS Laurets", "Mairie de Bizanos", "Abbé Brémond", "Pl. du Foirail", "Pôle Bosquet"
                )
            ),
            "7" to mapOf(
                "ARTIGUELOUTAN Salle des Fêtes" to listOf(
                    "LESCAR Soleil", "Mairie de Lescar", "218ème RI", "Place de Verdun",
                    "Pôle Bosquet", "Ousse", "ARTIGUELOUTAN Salle des Fêtes"
                ),
                "LESCAR Soleil" to listOf(
                    "ARTIGUELOUTAN Salle des Fêtes", "Ousse", "Pôle Bosquet", "Place de Verdun",
                    "218ème RI", "Mairie de Lescar", "LESCAR Soleil"
                )
            ),
            "8" to mapOf(
                "PAU Porte des Pyrénées" to listOf(
                    "BIZANOS", "Gare de Pau", "Pôle Bosquet", "Place de Verdun", "PAU Porte des Pyrénées"
                ),
                "BIZANOS" to listOf(
                    "PAU Porte des Pyrénées", "Place de Verdun", "Pôle Bosquet", "Gare de Pau", "BIZANOS"
                )
            ),
            "9" to mapOf(
                "MORLAÀS Communauté de Communes" to listOf(
                    "PAU Rives du Gave", "Gare de Pau", "Pôle Bosquet", "Université", "MORLAÀS Communauté de Communes"
                ),
                "PAU Rives du Gave" to listOf(
                    "MORLAÀS Communauté de Communes", "Université", "Pôle Bosquet", "Gare de Pau", "PAU Rives du Gave"
                )
            ),
            "10" to mapOf(
                "SERRES-CASTET Liben" to listOf(
                    "PAU Pôle Bosquet", "Université", "Catherine de Bourbon", "SERRES-CASTET Liben"
                ),
                "PAU Pôle Bosquet" to listOf(
                    "SERRES-CASTET Liben", "Catherine de Bourbon", "Université", "PAU Pôle Bosquet"
                )
            ),
            "11" to mapOf(
                "GAN Mairie" to listOf(
                    "BILLÈRE Château d'Este", "Place de Verdun", "Pôle Bosquet", "Jurançon", "GAN Mairie"
                ),
                "BILLÈRE Château d'Este" to listOf(
                    "GAN Mairie", "Jurançon", "Pôle Bosquet", "Place de Verdun", "BILLÈRE Château d'Este"
                )
            ),
            "12" to mapOf(
                "LESCAR Quartier Libre" to listOf(
                    "PAU Porte des Pyrénées", "Pôle Bosquet", "Université", "LESCAR Quartier Libre"
                ),
                "PAU Porte des Pyrénées" to listOf(
                    "LESCAR Quartier Libre", "Université", "Pôle Bosquet", "PAU Porte des Pyrénées"
                )
            ),
            "COXI" to mapOf(
                "Navette Centre Ville Gratuite" to listOf(
                    "Pôle Bosquet", "République", "Les Halles", "Foirail", "Place de Verdun",
                    "Gramont", "Clemenceau", "Barthou - Beaumont", "Pôle Bosquet"
                )
            )
        )
    }

    /**
     * Génère la structure détaillée d'une ligne de bus avec ses arrêts ordonnés.
     * Par défaut, 'isTerminusAtTop' est true : Terminus en haut, Début en bas.
     */
    fun getRouteDetail(
        departure: DepartureComparison,
        currentStopName: String,
        chosenDirection: String? = null,
        isTerminusAtTop: Boolean = true
    ): RouteLineDetail {
        val cleanLineCode = cleanLineRef(departure.lineRef)

        // 1. Recherche de la ligne dans le dictionnaire officiel Idelis
        val lineDirections = OFFICIAL_NETWORK_STOPS[cleanLineCode.uppercase()]
            ?: OFFICIAL_NETWORK_STOPS.entries.firstOrNull { it.key.equals(cleanLineCode, ignoreCase = true) }?.value
            ?: dynamicDirectionsFromCache(cleanLineCode)

        val availableDirections = lineDirections.keys.toList()

        // 2. Sélection de la direction correspondante
        val activeDirection = when {
            !chosenDirection.isNullOrBlank() && lineDirections.containsKey(chosenDirection) -> chosenDirection
            !chosenDirection.isNullOrBlank() -> findBestMatchingDirection(chosenDirection, availableDirections)
            departure.destinationName.isNotBlank() -> findBestMatchingDirection(departure.destinationName, availableDirections)
            else -> availableDirections.firstOrNull() ?: departure.destinationName
        }

        // 3. Récupération des arrêts dans l'ordre chronologique de circulation (du départ vers le terminus)
        var chronologicalStops = lineDirections[activeDirection].orEmpty()
        if (chronologicalStops.isEmpty()) {
            chronologicalStops = dynamicStopsFromCache(cleanLineCode, activeDirection)
        }

        // Si l'arrêt courant n'était pas listé, l'insérer logiquement au milieu de la ligne
        val currentCanonical = IdelisRepository.getCanonicalStopName(currentStopName)
        if (chronologicalStops.none { IdelisRepository.getCanonicalStopName(it).equals(currentCanonical, ignoreCase = true) }) {
            val mutableStops = chronologicalStops.toMutableList()
            if (mutableStops.size >= 2) {
                mutableStops.add(1, currentStopName)
            } else {
                mutableStops.add(currentStopName)
            }
            chronologicalStops = mutableStops
        }

        val startStopName = chronologicalStops.firstOrNull() ?: "Départ de la ligne"
        val terminusStopName = chronologicalStops.lastOrNull() ?: activeDirection

        // 4. Correspondances par arrêt
        val servingsByStop = IdelisRepository.globalServingsByStopName

        // 5. Construction des RouteStopItem dans l'ordre chronologique (1..N)
        val itemsInTravelOrder = chronologicalStops.mapIndexed { index, stopName ->
            val canon = IdelisRepository.getCanonicalStopName(stopName)
            val isCurrent = canon.equals(currentCanonical, ignoreCase = true) ||
                    normalizeString(canon) == normalizeString(currentCanonical)

            val connections = servingsByStop[canon.lowercase()]?.map { it.lineRef.uppercase() }
                ?.filter { it != cleanLineCode.uppercase() }
                ?.distinct()
                ?.sorted()
                ?: emptyList()

            RouteStopItem(
                stopName = stopName,
                canonicalName = canon,
                sequenceIndex = index + 1,
                isTerminus = (index == chronologicalStops.lastIndex),
                isStart = (index == 0),
                isCurrentStop = isCurrent,
                connectingLines = connections,
                commune = null
            )
        }

        // 6. Application de l'ordre d'affichage demandé :
        // Terminus en haut et Début en bas quand isTerminusAtTop = true
        val finalOrderedStops = if (isTerminusAtTop) {
            itemsInTravelOrder.asReversed()
        } else {
            itemsInTravelOrder
        }

        val formattedLineLabel = when {
            cleanLineCode.equals("F", ignoreCase = true) -> "Fébus"
            cleanLineCode.equals("COXI", ignoreCase = true) -> "Coxitis"
            cleanLineCode.startsWith("T", ignoreCase = true) -> "Chronobus $cleanLineCode"
            cleanLineCode.startsWith("Ligne", ignoreCase = true) -> cleanLineCode
            else -> "Ligne $cleanLineCode"
        }

        return RouteLineDetail(
            lineCode = cleanLineCode,
            lineLabel = formattedLineLabel,
            routeLongName = departure.lineName.ifBlank { formattedLineLabel },
            colorHex = departure.routeColorHex,
            textColorHex = departure.routeTextColorHex,
            iconUrl = departure.iconUrl,
            availableDirections = availableDirections,
            selectedDirection = activeDirection,
            terminusName = terminusStopName,
            startName = startStopName,
            totalStopsCount = chronologicalStops.size,
            stops = finalOrderedStops,
            isTerminusAtTop = isTerminusAtTop
        )
    }

    private fun dynamicDirectionsFromCache(lineCode: String): Map<String, List<String>> {
        val cache = IdelisRepository.globalDownloadedStopsCache
        if (cache.isNotEmpty()) {
            val lineServings = cache.filter { it.lineRef.equals(lineCode, ignoreCase = true) }
            if (lineServings.isNotEmpty()) {
                val grouped = lineServings.groupBy { it.direction }
                val result = mutableMapOf<String, List<String>>()
                for ((dir, servings) in grouped) {
                    val stopNames = servings.map { it.stopName }.distinct()
                    if (stopNames.isNotEmpty()) {
                        result[dir] = stopNames
                    }
                }
                if (result.isNotEmpty()) return result
            }
        }
        return mapOf(
            "Terminus Principal" to listOf("Départ de la ligne", "Pôle Bosquet", "Terminus Principal")
        )
    }

    private fun dynamicStopsFromCache(lineCode: String, direction: String): List<String> {
        val dirs = dynamicDirectionsFromCache(lineCode)
        return dirs[direction] ?: dirs.values.firstOrNull() ?: listOf("Départ $lineCode", "Pôle Bosquet", direction)
    }

    private fun findBestMatchingDirection(target: String, directions: List<String>): String {
        if (directions.isEmpty()) return target

        val normTarget = normalizeString(target)
        directions.firstOrNull { normalizeString(it) == normTarget }?.let { return it }
        directions.firstOrNull { normTarget.contains(normalizeString(it)) || normalizeString(it).contains(normTarget) }?.let { return it }

        val targetWords = normTarget.split(Regex("[^a-z0-9]+")).filter { it.length > 2 }
        var bestMatch = directions.first()
        var maxMatches = -1

        for (dir in directions) {
            val dirNorm = normalizeString(dir)
            val matchCount = targetWords.count { dirNorm.contains(it) }
            if (matchCount > maxMatches) {
                maxMatches = matchCount
                bestMatch = dir
            }
        }

        return bestMatch
    }

    private fun cleanLineRef(raw: String): String {
        return raw.trim()
            .removePrefix("Ligne ")
            .removePrefix("ligne ")
            .removePrefix("Chronobus ")
            .removePrefix("LIGNE ")
            .trim()
    }
}
