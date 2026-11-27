package com.example.data.repository

import android.content.Context
import com.example.data.model.DepartureComparison
import com.example.data.model.RouteLineDetail
import com.example.data.model.RouteStopItem
import com.example.data.remote.GtfsScheduleIndex
import com.example.data.remote.IdelisWebScraperService
import com.example.data.remote.StopServing
import java.text.Normalizer
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Repository dédié à la gestion des itinéraires et de l'ordre complet des arrêts par ligne et par sens.
 * Priorités des sources :
 * 1. Séquence officielle `stop_sequence` extraite du GTFS Idelis (`GtfsScheduleIndex`) — tous les arrêts réels dans l'ordre exact.
 * 2. Référentiel officiel embarqué (`OFFICIAL_NETWORK_STOPS`) si le GTFS n'est pas encore indexé.
 * 3. Cache OpenData `StopServing` ordonné géographiquement vers le terminus en dernier recours.
 */
class RouteStopsRepository(
    private val context: Context? = null
) {

    companion object {
        private val QUAI_PAREN_SUFFIX_REGEX = Regex("""\s*\(Quai\s+[^)]+\)\s*$""", RegexOption.IGNORE_CASE)
        private val GENERIC_PAREN_SUFFIX_REGEX = Regex("""\s*\([^)]*\)\s*$""")
        private val QUAI_WORD_REGEX = Regex("""(?i)\bquai[s]?\s+[a-z0-9]+\b|\bquai[s]?\b""")

        private fun normalizeString(text: String): String {
            val normalized = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            return Regex("\\p{InCombiningDiacriticalMarks}+").replace(normalized, "").trim()
        }

        private fun stripQuaiSuffix(raw: String): String {
            return raw.replace(QUAI_PAREN_SUFFIX_REGEX, "").trim()
        }

        private fun normalizeForStopMatch(raw: String): String {
            val noParen = raw.replace(QUAI_PAREN_SUFFIX_REGEX, "")
            val noDiacritics = normalizeString(noParen)
            val withoutQuai = noDiacritics.replace(QUAI_WORD_REGEX, " ").replace(Regex("\\s+"), " ").trim()
            val withoutCity = StopSearchMatcher.stripLeadingCommune(
                withoutQuai,
                IdelisRepository.getDynamicCommunes()
            )
            return withoutCity
                .replace("f. mitterrand", "mitterrand")
                .replace("f.mitterrand", "mitterrand")
                .replace("francois mitterrand", "mitterrand")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
        }

        // Base de secours embarquée des arrêts par ligne et par direction officielle du réseau Idelis
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
     * Génère la structure détaillée d'une ligne de bus avec l'ensemble de ses arrêts ordonnés
     * selon le sens de circulation du bus sélectionné.
     * Par défaut, 'isTerminusAtTop' est true : Terminus en haut, Début en bas.
     */
    fun getRouteDetail(
        departure: DepartureComparison,
        currentStopName: String,
        chosenDirection: String? = null,
        isTerminusAtTop: Boolean = true,
        isLoadingStops: Boolean = false
    ): RouteLineDetail {
        val cleanLineCode = cleanLineRef(departure.lineRef)
        val rawTargetDirection = chosenDirection?.takeIf { it.isNotBlank() } ?: departure.destinationName
        val cleanTargetDirection = stripQuaiSuffix(rawTargetDirection)
        val quaiLabel = IdelisWebScraperService.extractQuaiLabel(departure.destinationName)

        var availableDirections: List<String> = emptyList()
        var activeDirection = cleanTargetDirection
        var chronologicalStops: List<String> = emptyList()

        // 1. Priorité absolue : séquences officielles GTFS (stop_sequence complet)
        val gtfsDirections = GtfsScheduleIndex.getRouteDirectionsForLine(cleanLineCode)
        if (gtfsDirections.isNotEmpty()) {
            val bestDir = selectBestGtfsDirection(
                targetDirection = cleanTargetDirection,
                currentStopName = currentStopName,
                directions = gtfsDirections
            )
            if (bestDir != null && bestDir.stopPatterns.isNotEmpty()) {
                val chosenPattern = selectBestPatternForCurrentStop(
                    patterns = bestDir.stopPatterns,
                    currentStopName = currentStopName
                )
                if (chosenPattern.size >= 2) {
                    availableDirections = gtfsDirections.map { it.headsign }
                    activeDirection = bestDir.headsign
                    chronologicalStops = chosenPattern
                }
            }
        }

        // 2. Priorité 2 : référentiel complet OpenDataSoft (tous les arrêts réels par ligne/sens)
        //    avant les extraits statiques partiels (`OFFICIAL_NETWORK_STOPS`).
        // 3. Priorité 3 : référentiel officiel embarqué (repli si OpenData et GTFS absents ou partiels).
        if (chronologicalStops.isEmpty()) {
            val cacheDirections = dynamicDirectionsFromCache(cleanLineCode, currentStopName)
            val staticDirections = resolveStaticDirections(cleanLineCode)

            val (cacheDirName, cacheStops) = pickBestDirectionAndStops(
                directionsMap = cacheDirections,
                targetDirection = cleanTargetDirection,
                currentStopName = currentStopName
            )
            val (staticDirName, staticStops) = pickBestDirectionAndStops(
                directionsMap = staticDirections,
                targetDirection = cleanTargetDirection,
                currentStopName = currentStopName
            )

            val useCache = cacheStops.size >= 2 && cacheStops.size >= staticStops.size
            val chosenMap = if (useCache) cacheDirections else staticDirections
            val chosenDir = if (useCache) cacheDirName else staticDirName
            val chosenStops = if (useCache) cacheStops else staticStops

            if (chosenStops.isNotEmpty()) {
                availableDirections = chosenMap.keys.toList()
                activeDirection = chosenDir.ifBlank { cleanTargetDirection }
                chronologicalStops = chosenStops
            }
        }

        // 4. Recherche exacte/floue de l'arrêt actuel dans l'ordre réel (sans jamais injecter à l'index 1)
        val currentIdx = findBestMatchingStopIndex(chronologicalStops, currentStopName)

        val formattedLineLabel = when {
            cleanLineCode.equals("F", ignoreCase = true) -> "Fébus"
            cleanLineCode.equals("COXI", ignoreCase = true) -> "Coxitis"
            cleanLineCode.startsWith("T", ignoreCase = true) && cleanLineCode.drop(1).all { it.isDigit() } ->
                "Chronobus $cleanLineCode"
            cleanLineCode.startsWith("Ligne", ignoreCase = true) -> cleanLineCode
            cleanLineCode.isNotBlank() -> "Ligne $cleanLineCode"
            else -> departure.lineName.ifBlank { "Ligne de bus" }
        }

        val startStopName = chronologicalStops.firstOrNull() ?: ""
        val terminusStopName = chronologicalStops.lastOrNull()
            ?: cleanTargetDirection.ifBlank { activeDirection.ifBlank { formattedLineLabel } }
        val stopsRemaining = if (chronologicalStops.isNotEmpty() && currentIdx >= 0) {
            (chronologicalStops.lastIndex - currentIdx).coerceAtLeast(0)
        } else {
            null
        }

        // 5. Correspondances & communes par arrêt
        val servingsByStop = IdelisRepository.globalServingsByStopName

        // 6. Construction des RouteStopItem dans l'ordre chronologique officiel (1 = début .. N = terminus)
        val itemsInTravelOrder = chronologicalStops.mapIndexed { index, stopName ->
            val canon = IdelisRepository.getCanonicalStopName(stopName)
            val stopServings = servingsByStop[canon.lowercase(Locale.ROOT)]
            val lineAliases = GtfsScheduleIndex.buildLineCodeAliases(cleanLineCode).toSet()
            val connections = stopServings
                ?.map { cleanLineRef(it.lineRef).uppercase(Locale.ROOT) }
                ?.filter { conn ->
                    conn.isNotBlank() && GtfsScheduleIndex.buildLineCodeAliases(conn).none { it in lineAliases }
                }
                ?.distinct()
                ?.sorted()
                ?: emptyList()
            val commune = stopServings?.firstOrNull { !it.commune.isNullOrBlank() }?.commune

            RouteStopItem(
                stopName = stopName,
                canonicalName = canon,
                sequenceIndex = index + 1,
                isTerminus = (index == chronologicalStops.lastIndex),
                isStart = (index == 0),
                isCurrentStop = (index == currentIdx),
                isPassedBeforeCurrent = (currentIdx >= 0 && index < currentIdx),
                connectingLines = connections,
                commune = commune
            )
        }

        // 7. Terminus en haut et Début en bas par défaut
        val finalOrderedStops = if (isTerminusAtTop) {
            itemsInTravelOrder.asReversed()
        } else {
            itemsInTravelOrder
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
            isTerminusAtTop = isTerminusAtTop,
            quaiLabel = quaiLabel,
            waitMinutes = departure.minutesUntilDeparture,
            aimedTimeFormatted = departure.aimedDepartureTimeFormatted.takeIf { it.isNotBlank() },
            expectedTimeFormatted = departure.expectedDepartureTimeFormatted?.takeIf { it.isNotBlank() },
            delayMinutes = departure.delayMinutes,
            departureStatus = departure.status,
            isRealTime = departure.isRealTime,
            isDelayIndeterminate = departure.isDelayIndeterminate,
            rawDelayMinutes = departure.rawDelayMinutes,
            stopsRemainingToTerminus = stopsRemaining,
            isLoadingStops = isLoadingStops && chronologicalStops.isEmpty()
        )
    }

    private fun resolveStaticDirections(cleanLineCode: String): Map<String, List<String>> {
        val aliases = GtfsScheduleIndex.buildLineCodeAliases(cleanLineCode)
        for (alias in aliases) {
            OFFICIAL_NETWORK_STOPS[alias]?.let { return it }
            OFFICIAL_NETWORK_STOPS.entries.firstOrNull { it.key.equals(alias, ignoreCase = true) }?.value?.let {
                return it
            }
        }
        return emptyMap()
    }

    private fun pickBestDirectionAndStops(
        directionsMap: Map<String, List<String>>,
        targetDirection: String,
        currentStopName: String
    ): Pair<String, List<String>> {
        if (directionsMap.isEmpty()) return "" to emptyList()
        if (targetDirection.isNotBlank()) {
            directionsMap.entries.firstOrNull { it.key.equals(targetDirection, ignoreCase = true) }?.let {
                if (it.value.isNotEmpty()) return it.key to it.value
            }
            val bestEntry = directionsMap.entries.maxByOrNull { (dir, stops) ->
                val dirScore = directionMatchScore(targetDirection, dir) * 100
                val termScore = stops.lastOrNull()?.let { directionMatchScore(targetDirection, it) * 90 } ?: 0
                val currentBonus = if (findBestMatchingStopIndex(stops, currentStopName) >= 0) 40 else 0
                maxOf(dirScore, termScore) + currentBonus + stops.size.coerceAtMost(39)
            }
            if (bestEntry != null && bestEntry.value.isNotEmpty()) {
                return bestEntry.key to bestEntry.value
            }
        }
        val withCurrent = directionsMap.entries.firstOrNull { (_, stops) ->
            findBestMatchingStopIndex(stops, currentStopName) >= 0 && stops.isNotEmpty()
        }
        val fallback = withCurrent ?: directionsMap.entries.maxByOrNull { it.value.size }
        return if (fallback != null) fallback.key to fallback.value else "" to emptyList()
    }

    private fun selectBestGtfsDirection(
        targetDirection: String,
        currentStopName: String,
        directions: List<GtfsScheduleIndex.RouteDirectionStops>
    ): GtfsScheduleIndex.RouteDirectionStops? {
        if (directions.isEmpty()) return null
        if (targetDirection.isBlank()) {
            return directions.firstOrNull { dir ->
                dir.stopPatterns.any { findBestMatchingStopIndex(it, currentStopName) >= 0 }
            } ?: directions.first()
        }

        val scored = directions.map { dir ->
            val headsignScore = directionMatchScore(targetDirection, dir.headsign)
            val terminusScore = dir.stopPatterns.firstOrNull()?.lastOrNull()?.let { term ->
                directionMatchScore(targetDirection, term)
            } ?: 0
            val containsCurrentBonus = if (dir.stopPatterns.any { findBestMatchingStopIndex(it, currentStopName) >= 0 }) 50 else 0
            val bestDirScore = maxOf(headsignScore, terminusScore)
            // Le score de direction prime largement sur le bonus d'arrêt courant pour ne jamais inverser de sens
            val total = bestDirScore * 100 + containsCurrentBonus + (dir.stopPatterns.firstOrNull()?.size ?: 0).coerceAtMost(99)
            dir to total
        }

        return scored.maxByOrNull { it.second }?.first
    }

    private fun selectBestPatternForCurrentStop(
        patterns: List<List<String>>,
        currentStopName: String
    ): List<String> {
        if (patterns.isEmpty()) return emptyList()
        val withCurrent = patterns.filter { findBestMatchingStopIndex(it, currentStopName) >= 0 }
        return if (withCurrent.isNotEmpty()) {
            withCurrent.maxByOrNull { it.size } ?: patterns.first()
        } else {
            patterns.maxByOrNull { it.size } ?: patterns.first()
        }
    }

    private fun findBestMatchingStopIndex(stops: List<String>, targetStopName: String): Int {
        if (stops.isEmpty() || targetStopName.isBlank()) return -1

        val targetCanon = IdelisRepository.getCanonicalStopName(targetStopName)
        val targetCanonNorm = normalizeString(targetCanon)
        val targetKey = GtfsScheduleIndex.normalizeStopKey(targetStopName)
        val targetMatchNorm = normalizeForStopMatch(targetStopName)
        val targetTokens = targetMatchNorm.split(' ').filter { it.length >= 3 }.toSet()

        // 1. Correspondance canonique directe
        stops.indexOfFirst { stop ->
            val canon = IdelisRepository.getCanonicalStopName(stop)
            canon.equals(targetCanon, ignoreCase = true) ||
                normalizeString(canon) == targetCanonNorm
        }.takeIf { it >= 0 }?.let { return it }

        // 2. Correspondance par clé GTFS normalisée (sans quai ni commune)
        if (targetKey.isNotBlank()) {
            stops.indexOfFirst { stop ->
                GtfsScheduleIndex.normalizeStopKey(stop) == targetKey
            }.takeIf { it >= 0 }?.let { return it }
        }

        // 3. Correspondance souple par inclusion ou recouvrement lexical
        var bestIdx = -1
        var bestScore = 0
        for ((index, stop) in stops.withIndex()) {
            val stopNorm = normalizeForStopMatch(stop)
            if (stopNorm.isBlank()) continue
            val score = when {
                stopNorm == targetMatchNorm -> 1000
                targetMatchNorm.length >= 4 && (stopNorm.contains(targetMatchNorm) || targetMatchNorm.contains(stopNorm)) ->
                    500 + minOf(stopNorm.length, targetMatchNorm.length)
                targetTokens.isNotEmpty() -> {
                    val stopTokens = stopNorm.split(' ').filter { it.length >= 3 }.toSet()
                    val overlap = targetTokens.intersect(stopTokens).size
                    if (overlap > 0 && (overlap == targetTokens.size || overlap == stopTokens.size || overlap >= 2)) {
                        overlap * 100 - kotlin.math.abs(stopTokens.size - targetTokens.size)
                    } else {
                        0
                    }
                }
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                bestIdx = index
            }
        }
        return if (bestScore > 0) bestIdx else -1
    }

    private fun matchesLineCode(candidateLine: String, targetCleanCode: String): Boolean {
        val targetAliases = GtfsScheduleIndex.buildLineCodeAliases(targetCleanCode)
        if (targetAliases.isEmpty()) return false
        val targetSet = targetAliases.toSet()
        return GtfsScheduleIndex.buildLineCodeAliases(candidateLine).any { it in targetSet }
    }

    private fun dynamicDirectionsFromCache(
        lineCode: String,
        currentStopName: String = ""
    ): Map<String, List<String>> {
        val cache = IdelisRepository.globalDownloadedStopsCache
        if (cache.isEmpty()) return emptyMap()

        val lineServings = cache.filter { matchesLineCode(it.lineRef, lineCode) }
        if (lineServings.isEmpty()) return emptyMap()

        // Regrouper par sens normalisé pour fusionner d'éventuelles variations mineures d'intitulé
        val grouped = lineServings.groupBy { s ->
            val cleanDir = stripQuaiSuffix(s.direction)
            val normDir = IdelisRepository.normalizeDirection(cleanDir)
            normDir.ifBlank { "sens_${s.sens}" }
        }
        val result = LinkedHashMap<String, List<String>>()
        for ((_, servings) in grouped) {
            val displayDir = servings.map { stripQuaiSuffix(it.direction) }
                .firstOrNull { it.isNotBlank() }
                ?: "Direction ${servings.first().sens}"
            val orderedNames = orderServingsGeographicallyTowardTerminus(
                servings = servings,
                direction = displayDir,
                currentStopName = currentStopName
            )
            if (orderedNames.isNotEmpty()) {
                result[displayDir] = orderedNames
            }
        }
        return result
    }

    private fun orderServingsGeographicallyTowardTerminus(
        servings: List<StopServing>,
        direction: String,
        currentStopName: String = ""
    ): List<String> {
        data class UniqueStop(
            val displayName: String,
            val canonLower: String,
            val lat: Double,
            val lon: Double
        )

        val uniqueByCanon = LinkedHashMap<String, UniqueStop>()
        for (s in servings) {
            val canon = IdelisRepository.getCanonicalStopName(s.stopName)
            val key = canon.lowercase(Locale.ROOT)
            val existing = uniqueByCanon[key]
            val hasCoords = s.lat != 0.0 && s.lon != 0.0
            if (existing == null || ((existing.lat == 0.0 || existing.lon == 0.0) && hasCoords)) {
                uniqueByCanon[key] = UniqueStop(
                    displayName = if (s.stopName.contains("Quai", ignoreCase = true)) canon else s.stopName.trim(),
                    canonLower = key,
                    lat = s.lat,
                    lon = s.lon
                )
            }
        }

        val allUnique = uniqueByCanon.values.toList()
        if (allUnique.size <= 2) return allUnique.map { it.displayName }

        val withCoords = allUnique.filter { it.lat != 0.0 && it.lon != 0.0 }
        if (withCoords.size < 2) return allUnique.map { it.displayName }

        val cleanDir = stripQuaiSuffix(direction)
        val stopNamesWithCoords = withCoords.map { it.displayName }
        val terminusIdx = findBestMatchingStopIndex(stopNamesWithCoords, cleanDir)
        val terminusStop: UniqueStop = if (terminusIdx >= 0) {
            withCoords[terminusIdx]
        } else {
            // Si le libellé de direction ne correspond exactement à aucun nom d'arrêt,
            // identifier les deux extrémités géographiques de la ligne puis orienter vers le terminus.
            var endA = withCoords.first()
            var endB = withCoords.last()
            var maxDist = -1.0
            for (i in 0 until withCoords.size) {
                for (j in i + 1 until withCoords.size) {
                    val d = haversineMeters(withCoords[i].lat, withCoords[i].lon, withCoords[j].lat, withCoords[j].lon)
                    if (d > maxDist) {
                        maxDist = d
                        endA = withCoords[i]
                        endB = withCoords[j]
                    }
                }
            }
            val scoreA = directionMatchScore(cleanDir, endA.displayName)
            val scoreB = directionMatchScore(cleanDir, endB.displayName)
            when {
                scoreB > scoreA -> endB
                scoreA > scoreB -> endA
                currentStopName.isNotBlank() -> {
                    val curIdx = findBestMatchingStopIndex(stopNamesWithCoords, currentStopName)
                    if (curIdx >= 0) {
                        val cur = withCoords[curIdx]
                        val dToA = haversineMeters(cur.lat, cur.lon, endA.lat, endA.lon)
                        val dToB = haversineMeters(cur.lat, cur.lon, endB.lat, endB.lon)
                        if (dToB >= dToA) endB else endA
                    } else {
                        endB
                    }
                }
                else -> endB
            }
        }

        // Chaînage par plus proche voisin en remontant depuis le terminus
        val remaining = withCoords.toMutableList()
        val reversedChain = ArrayList<UniqueStop>(withCoords.size)
        var current: UniqueStop = terminusStop
        remaining.remove(current)
        reversedChain.add(current)

        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { candidate ->
                haversineMeters(current.lat, current.lon, candidate.lat, candidate.lon)
            } ?: break
            remaining.remove(next)
            reversedChain.add(next)
            current = next
        }

        val orderedGeo = reversedChain.asReversed().toMutableList()
        val withoutCoords = allUnique.filter { it.lat == 0.0 || it.lon == 0.0 }
        if (withoutCoords.isNotEmpty()) {
            val insertIdx = (orderedGeo.size - 1).coerceAtLeast(0)
            orderedGeo.addAll(insertIdx, withoutCoords)
        }
        return orderedGeo.map { it.displayName }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun directionMatchScore(target: String, candidate: String): Int {
        val cleanedTarget = target.replace(GENERIC_PAREN_SUFFIX_REGEX, "").trim().ifBlank { target }
        val normTarget = normalizeString(cleanedTarget)
        val normCand = normalizeString(candidate)
        if (normTarget.isBlank() || normCand.isBlank()) return 0

        if (normTarget == normCand) return 1000
        val idelisTarget = IdelisRepository.normalizeDirection(cleanedTarget)
        val idelisCand = IdelisRepository.normalizeDirection(candidate)
        if (idelisTarget.isNotBlank() && idelisTarget == idelisCand) return 950
        if (idelisTarget.length >= 4 && idelisCand.length >= 4 &&
            (idelisTarget.contains(idelisCand) || idelisCand.contains(idelisTarget))
        ) {
            return 750 + minOf(idelisTarget.length, idelisCand.length)
        }
        if (normTarget.contains(normCand) || normCand.contains(normTarget)) {
            return 650 + minOf(normTarget.length, normCand.length)
        }

        val targetWords = normalizeForStopMatch(cleanedTarget).split(' ').filter { it.length > 2 }.toSet()
        val candWords = normalizeForStopMatch(candidate).split(' ').filter { it.length > 2 }.toSet()
        if (targetWords.isEmpty() || candWords.isEmpty()) return 0
        val overlap = targetWords.intersect(candWords).size
        return overlap * 100 - kotlin.math.abs(targetWords.size - candWords.size)
    }

    private fun cleanLineRef(raw: String): String {
        val upper = raw.trim().uppercase(Locale.ROOT)
        if (upper.isBlank()) return ""
        val clean = upper
            .removePrefix("LIGNE ")
            .removePrefix("LIGNE_")
            .removePrefix("LIGNE-")
            .removePrefix("CHRONOBUS ")
            .removePrefix("CHRONOBUS_")
            .removePrefix("CHRONOBUS-")
            .removePrefix("PICTO ")
            .removePrefix("PICTO_")
            .removePrefix("PICTO-")
            .removePrefix("BUS ")
            .trim()
        val noL = clean.removePrefix("L").takeIf {
            clean.startsWith("L") && it.isNotBlank() && it.all { ch -> ch.isDigit() }
        } ?: clean
        return when {
            noL == "F" || noL == "FÉBUS" || noL == "FEBUS" -> "F"
            noL == "COXI" || noL == "COXITIS" || noL == "NAVETTE" || noL == "NCV" -> "COXI"
            noL.all { it.isDigit() } -> noL.trimStart('0').ifEmpty { "0" }
            else -> noL
        }
    }
}
