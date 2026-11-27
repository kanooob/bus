package com.example.data.remote

import android.util.Log
import com.example.data.model.GtfsCalendar
import com.example.data.model.GtfsCalendarDate
import com.example.data.model.GtfsTheoreticalDeparture
import com.example.data.model.IdelisPeriod
import com.example.data.repository.IdelisRepository
import com.example.data.repository.StopSearchMatcher
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

/**
 * Index en mémoire des horaires théoriques réels du GTFS Idelis filtrés pour la journée active
 * (Semaine scolaire Orange, Vacances Bleue, Samedi Grise, Dimanche/Férié Verte)
 * selon `calendar.txt` et `calendar_dates.txt` en heure Europe/Paris.
 */
object GtfsScheduleIndex {

    private const val TAG = "GtfsScheduleIndex"
    val PARIS_TZ: TimeZone = TimeZone.getTimeZone("Europe/Paris")

    data class RouteDirectionStops(
        val headsign: String,
        val stopPatterns: List<List<String>>
    )

    data class Snapshot(
        val dateStr: String,
        val activePeriod: IdelisPeriod,
        val worksNotes: List<String>,
        val departuresByCanonicalStop: Map<String, List<GtfsTheoreticalDeparture>>,
        val departuresByNormalizedStop: Map<String, List<GtfsTheoreticalDeparture>>,
        val activeServiceIds: Set<String>,
        val routeDirectionsByLine: Map<String, List<RouteDirectionStops>> = emptyMap(),
        val routeColorsByLine: Map<String, Pair<String, String>> = emptyMap()
    )

    private data class RouteMeta(
        val shortName: String,
        val longName: String,
        val colorHex: String,
        val textColorHex: String
    )

    private data class StopMeta(
        val stopName: String,
        val canonicalLower: String,
        val normalizedKey: String
    )

    private data class TripMeta(
        val routeId: String,
        val serviceId: String,
        val headsign: String,
        val directionId: String,
        val isActive: Boolean,
        val collectRouteStops: Boolean
    )

    private data class TripStopPoint(
        val sequence: Int,
        val rowOrder: Int,
        val stopName: String
    )

    @Volatile
    var current: Snapshot? = null
        private set

    @Volatile
    private var cachedZipBytes: ByteArray? = null

    fun getCachedZipBytes(): ByteArray? = cachedZipBytes

    internal fun resetForTesting() {
        current = null
        cachedZipBytes = null
    }

    fun currentDateString(cal: Calendar = Calendar.getInstance(PARIS_TZ)): String {
        return SimpleDateFormat("yyyyMMdd", Locale.FRANCE).apply {
            timeZone = PARIS_TZ
        }.format(cal.time)
    }

    fun isFrenchPublicHoliday(cal: Calendar): Boolean {
        val month = cal.get(Calendar.MONTH) + 1 // 1..12
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val fixed = setOf(
            1 to 1,   // Jour de l'an
            5 to 1,   // Fête du Travail
            5 to 8,   // Victoire 1945
            7 to 14,  // Fête Nationale
            8 to 15,  // Assomption
            11 to 1,  // Toussaint
            11 to 11, // Armistice 1918
            12 to 25  // Noël
        )
        if ((month to day) in fixed) return true

        // Jours fériés mobiles basés sur Pâques (algorithme grégorien de Meeus/Jones/Butcher)
        val year = cal.get(Calendar.YEAR)
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val easterMonth = (h + l - 7 * m + 114) / 31 // 3 = mars, 4 = avril
        val easterDay = ((h + l - 7 * m + 114) % 31) + 1

        val easterCal = Calendar.getInstance(PARIS_TZ).apply {
            clear()
            set(year, easterMonth - 1, easterDay, 12, 0, 0)
        }
        val targetDayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val targetYear = cal.get(Calendar.YEAR)

        fun matchesOffset(daysAfterEaster: Int): Boolean {
            val probe = (easterCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, daysAfterEaster) }
            return probe.get(Calendar.YEAR) == targetYear && probe.get(Calendar.DAY_OF_YEAR) == targetDayOfYear
        }

        // Lundi de Pâques (+1), Ascension (+39), Lundi de Pentecôte (+50)
        return matchesOffset(1) || matchesOffset(39) || matchesOffset(50)
    }

    fun detectDefaultPeriod(cal: Calendar = Calendar.getInstance(PARIS_TZ)): IdelisPeriod {
        if (isFrenchPublicHoliday(cal)) return IdelisPeriod.VERTE
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> IdelisPeriod.GRISE
            Calendar.SUNDAY -> IdelisPeriod.VERTE
            else -> IdelisPeriod.ORANGE
        }
    }

    fun normalizeStopKey(raw: String): String {
        val noDiacritics = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noDiacritics
            .replace(Regex("""\bquai[s]?\s+[a-z0-9]+\b"""), " ")
            .replace(Regex("""\bquai[s]?\b"""), " ")
            .replace(StopSearchMatcher.COMMUNE_PREFIX_REGEX, "")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
    }

    fun updateFromZipBytes(
        bytes: ByteArray,
        targetCal: Calendar = Calendar.getInstance(PARIS_TZ)
    ): Snapshot? {
        if (bytes.isEmpty()) return null
        cachedZipBytes = bytes
        val snapshot = buildSnapshotFromZipBytes(bytes, targetCal)
        if (snapshot != null) {
            current = snapshot
        }
        return snapshot
    }

    fun getDeparturesForStop(
        stopName: String,
        poleName: String? = null,
        targetCal: Calendar = Calendar.getInstance(PARIS_TZ)
    ): List<GtfsTheoreticalDeparture> {
        val todayStr = currentDateString(targetCal)
        var snap = current
        if (snap != null && snap.dateStr != todayStr) {
            val raw = cachedZipBytes
            if (raw != null) {
                snap = updateFromZipBytes(raw, targetCal)
            }
        }
        snap ?: return emptyList()

        val candidates = buildList {
            if (!poleName.isNullOrBlank()) {
                add(IdelisRepository.getCanonicalStopName(poleName).lowercase(Locale.ROOT))
                add(poleName.trim().lowercase(Locale.ROOT))
            }
            add(IdelisRepository.getCanonicalStopName(stopName).lowercase(Locale.ROOT))
            add(stopName.trim().lowercase(Locale.ROOT))
        }.distinct()

        for (key in candidates) {
            val found = snap.departuresByCanonicalStop[key]
            if (!found.isNullOrEmpty()) return found
        }

        val normCandidates = buildList {
            if (!poleName.isNullOrBlank()) add(normalizeStopKey(poleName))
            add(normalizeStopKey(stopName))
        }.filter { it.isNotBlank() }.distinct()

        for (normKey in normCandidates) {
            val found = snap.departuresByNormalizedStop[normKey]
            if (!found.isNullOrEmpty()) return found
        }

        return emptyList()
    }

    internal fun buildLineCodeAliases(rawCode: String): List<String> {
        val trimmed = rawCode.trim()
        if (trimmed.isBlank()) return emptyList()
        val upper = trimmed.uppercase(Locale.ROOT)
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
        return buildList {
            add(upper)
            if (clean.isNotBlank()) add(clean)
            val noLPrefix = clean.removePrefix("L").takeIf {
                clean.startsWith("L") && it.isNotBlank() && it.all { ch -> ch.isDigit() }
            }
            if (noLPrefix != null) add(noLPrefix)

            val numericPart = when {
                clean.all { it.isDigit() } -> clean
                noLPrefix != null -> noLPrefix
                else -> null
            }
            if (numericPart != null) {
                val strippedZeros = numericPart.trimStart('0').ifEmpty { "0" }
                add(strippedZeros)
                if (strippedZeros.length == 1) {
                    add("0$strippedZeros")
                }
            }
            when (clean) {
                "F", "FÉBUS", "FEBUS" -> {
                    add("F")
                    add("FÉBUS")
                    add("FEBUS")
                }
                "COXI", "COXITIS", "NAVETTE", "NCV" -> {
                    add("COXI")
                    add("COXITIS")
                    add("NAVETTE")
                    add("NCV")
                }
            }
        }.filter { it.isNotBlank() }.distinct()
    }

    fun getRouteDirectionsForLine(lineCode: String): List<RouteDirectionStops> {
        val map = current?.routeDirectionsByLine ?: return emptyList()
        for (key in buildLineCodeAliases(lineCode)) {
            val found = map[key]
            if (!found.isNullOrEmpty()) return found
        }
        return emptyList()
    }

    fun getRouteColorsForLine(lineCode: String): Pair<String, String>? {
        val map = current?.routeColorsByLine ?: return null
        for (key in buildLineCodeAliases(lineCode)) {
            val found = map[key]
            if (found != null) return found
        }
        return null
    }

    private fun cleanStopNameForRouteDisplay(rawName: String): String {
        val trimmed = rawName.trim()
        if (trimmed.isBlank()) return trimmed
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.contains("bosquet") ||
            lower.contains("gare de pau") ||
            lower.contains("gare sncf") ||
            lower.contains("verdun") ||
            lower.contains("catherine de bourbon") ||
            lower.contains("universit") ||
            lower.contains("cité multimédia") ||
            lower.contains("cite multimedia") ||
            lower.contains("hôpital") ||
            lower.contains("hopital")
        ) {
            return IdelisRepository.getCanonicalStopName(trimmed)
        }
        val withoutQuai = trimmed
            .replace(Regex("""(?i)\s+quai[s]?\s+[a-z0-9]+"""), "")
            .replace(Regex("""(?i)\s+quai[s]?"""), "")
            .trim()
        return withoutQuai.ifBlank { trimmed }
    }

    internal fun buildSnapshotFromZipBytes(
        bytes: ByteArray,
        targetCal: Calendar = Calendar.getInstance(PARIS_TZ)
    ): Snapshot? {
        return try {
            var calendarText: String? = null
            var calendarDatesText: String? = null
            var routesText: String? = null
            var stopsText: String? = null
            var tripsText: String? = null

            // Passe 1 : lire les fichiers de référence (petits) indépendamment de leur ordre dans le ZIP
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/').lowercase(Locale.ROOT)
                    when (name) {
                        "calendar.txt" -> calendarText = zis.bufferedReader(Charsets.UTF_8).readText()
                        "calendar_dates.txt" -> calendarDatesText = zis.bufferedReader(Charsets.UTF_8).readText()
                        "routes.txt" -> routesText = zis.bufferedReader(Charsets.UTF_8).readText()
                        "stops.txt" -> stopsText = zis.bufferedReader(Charsets.UTF_8).readText()
                        "trips.txt" -> tripsText = zis.bufferedReader(Charsets.UTF_8).readText()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val calendars = calendarText?.let { parseCalendars(it) }.orEmpty()
            val calendarDates = calendarDatesText?.let { parseCalendarDates(it) }.orEmpty()
            val (activeServiceIds, activePeriod, worksNotes) = resolveActiveServicesAndPeriod(
                calendars = calendars,
                calendarDates = calendarDates,
                targetCal = targetCal
            )

            val routesMap = routesText?.let { parseRoutes(it) }.orEmpty()
            val stopsMap = stopsText?.let { parseStops(it) }.orEmpty()
            val tripsMap = tripsText?.let { parseTripsForSnapshot(it, activeServiceIds) }.orEmpty()

            val byCanonical = HashMap<String, MutableList<GtfsTheoreticalDeparture>>()
            val byNormalized = HashMap<String, MutableList<GtfsTheoreticalDeparture>>()
            val tripStopPoints = HashMap<String, MutableList<TripStopPoint>>()

            // Passe 2 : streaming de stop_times.txt (horaires du jour + séquences complètes d'arrêts par trajet)
            if (tripsMap.isNotEmpty() && stopsMap.isNotEmpty()) {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.substringAfterLast('/').lowercase(Locale.ROOT)
                        if (name == "stop_times.txt") {
                            val reader = zis.bufferedReader(Charsets.UTF_8)
                            val headerLine = reader.readLine()?.removePrefix("\uFEFF") ?: break
                            val headers = parseCsvLine(headerLine).map { it.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT) }
                            val tripIdx = headers.indexOf("trip_id")
                            val arrIdx = headers.indexOf("arrival_time")
                            val depIdx = headers.indexOf("departure_time")
                            val stopIdx = headers.indexOf("stop_id")
                            val seqIdx = headers.indexOf("stop_sequence")

                            if (tripIdx >= 0 && stopIdx >= 0) {
                                var rowOrder = 0
                                var line = reader.readLine()
                                while (line != null) {
                                    if (line.isNotBlank()) {
                                        val parts = parseCsvLine(line)
                                        if (parts.size > maxOf(tripIdx, stopIdx)) {
                                            val tripId = parts[tripIdx].trim()
                                            val trip = tripsMap[tripId]
                                            if (trip != null) {
                                                val stopId = parts[stopIdx].trim()
                                                val stopMeta = stopsMap[stopId]
                                                if (stopMeta != null) {
                                                    if (trip.collectRouteStops) {
                                                        val seq = if (seqIdx in parts.indices) {
                                                            parts[seqIdx].trim().toIntOrNull() ?: Int.MAX_VALUE
                                                        } else {
                                                            Int.MAX_VALUE
                                                        }
                                                        tripStopPoints
                                                            .getOrPut(tripId) { ArrayList(24) }
                                                            .add(TripStopPoint(seq, rowOrder++, stopMeta.stopName))
                                                    }
                                                    if (trip.isActive) {
                                                        val rawDep = when {
                                                            depIdx in parts.indices && parts[depIdx].isNotBlank() -> parts[depIdx].trim()
                                                            arrIdx in parts.indices && parts[arrIdx].isNotBlank() -> parts[arrIdx].trim()
                                                            else -> ""
                                                        }
                                                        if (rawDep.isNotBlank()) {
                                                            val routeMeta = routesMap[trip.routeId]
                                                            val dep = GtfsTheoreticalDeparture(
                                                                stopId = stopId,
                                                                stopName = stopMeta.stopName,
                                                                routeId = trip.routeId,
                                                                routeShortName = routeMeta?.shortName ?: trip.routeId,
                                                                routeLongName = routeMeta?.longName ?: "Ligne ${trip.routeId}",
                                                                routeColor = routeMeta?.colorHex ?: "009BD9",
                                                                routeTextColor = routeMeta?.textColorHex ?: "FFFFFF",
                                                                tripHeadsign = trip.headsign,
                                                                departureTime = rawDep
                                                            )
                                                            byCanonical.getOrPut(stopMeta.canonicalLower) { mutableListOf() }.add(dep)
                                                            if (stopMeta.normalizedKey.isNotBlank()) {
                                                                byNormalized.getOrPut(stopMeta.normalizedKey) { mutableListOf() }.add(dep)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    line = reader.readLine()
                                }
                            }
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            val routeDirectionsByLine = buildRouteDirectionsByLine(
                tripsMap = tripsMap,
                routesMap = routesMap,
                tripStopPoints = tripStopPoints
            )

            val routeColorsByLine = HashMap<String, Pair<String, String>>()
            for ((routeId, meta) in routesMap) {
                val colorPair = meta.colorHex to meta.textColorHex
                for (alias in buildLineCodeAliases(meta.shortName) + buildLineCodeAliases(routeId)) {
                    if (alias.isNotBlank()) {
                        routeColorsByLine.putIfAbsent(alias, colorPair)
                    }
                }
            }

            val todayStr = currentDateString(targetCal)
            Log.d(
                TAG,
                "Index GTFS prêt pour $todayStr (${activePeriod.label}) : " +
                    "${activeServiceIds.size} services actifs, ${tripsMap.size} trajets, " +
                    "${byCanonical.size} arrêts indexés, ${routeDirectionsByLine.size} lignes ordonnées"
            )

            Snapshot(
                dateStr = todayStr,
                activePeriod = activePeriod,
                worksNotes = worksNotes,
                departuresByCanonicalStop = byCanonical,
                departuresByNormalizedStop = byNormalized,
                activeServiceIds = activeServiceIds,
                routeDirectionsByLine = routeDirectionsByLine,
                routeColorsByLine = routeColorsByLine
            )
        } catch (e: Exception) {
            Log.w(TAG, "Erreur indexation GTFS : ${e.message}")
            null
        }
    }

    private fun buildRouteDirectionsByLine(
        tripsMap: Map<String, TripMeta>,
        routesMap: Map<String, RouteMeta>,
        tripStopPoints: Map<String, List<TripStopPoint>>
    ): Map<String, List<RouteDirectionStops>> {
        if (tripStopPoints.isEmpty()) return emptyMap()

        // Regrouper par (code ligne normalisé, direction/headsign)
        val grouped = HashMap<String, MutableMap<String, MutableList<List<String>>>>()
        val displayHeadsigns = HashMap<String, String>()
        val rawRouteIdsByLine = HashMap<String, MutableSet<String>>()

        for ((tripId, points) in tripStopPoints) {
            if (points.isEmpty()) continue
            val trip = tripsMap[tripId] ?: continue
            val sorted = points.sortedWith(compareBy<TripStopPoint>({ it.sequence }, { it.rowOrder }))

            val orderedClean = ArrayList<String>(sorted.size)
            var prevCanon: String? = null
            for (pt in sorted) {
                val cleaned = cleanStopNameForRouteDisplay(pt.stopName)
                val canon = IdelisRepository.getCanonicalStopName(cleaned).lowercase(Locale.ROOT)
                if (canon != prevCanon) {
                    orderedClean.add(cleaned)
                    prevCanon = canon
                }
            }
            if (orderedClean.isEmpty()) continue

            val rawShort = routesMap[trip.routeId]?.shortName ?: trip.routeId
            val aliases = buildLineCodeAliases(rawShort)
            val lineKey = aliases.firstOrNull { !it.startsWith("LIGNE") && !it.startsWith("CHRONOBUS") }
                ?: aliases.firstOrNull()
                ?: continue
            rawRouteIdsByLine.getOrPut(lineKey) { HashSet() }.add(trip.routeId)

            val rawHead = trip.headsign.trim()
            val headsign = rawHead.ifBlank { orderedClean.last() }
            // Inclure direction_id si le headsign était vide dans trips.txt pour ne jamais fusionner Aller et Retour
            val dirSuffix = if (rawHead.isBlank()) "|dir:${trip.directionId}" else ""
            val dirNormKey = "${lineKey}|${headsign.lowercase(Locale.ROOT)}$dirSuffix"
            displayHeadsigns.putIfAbsent(dirNormKey, headsign)

            val dirMap = grouped.getOrPut(lineKey) { LinkedHashMap() }
            dirMap.getOrPut(dirNormKey) { mutableListOf() }.add(orderedClean)
        }

        val result = HashMap<String, List<RouteDirectionStops>>()
        for ((lineKey, dirMap) in grouped) {
            val dirList = dirMap.entries.mapNotNull { (dirNormKey, rawPatterns) ->
                val headsign = displayHeadsigns[dirNormKey] ?: return@mapNotNull null
                val uniquePatterns = LinkedHashMap<String, List<String>>()
                for (pattern in rawPatterns) {
                    val signature = pattern.joinToString("|") {
                        IdelisRepository.getCanonicalStopName(it).lowercase(Locale.ROOT)
                    }
                    val existing = uniquePatterns[signature]
                    if (existing == null || pattern.size > existing.size) {
                        uniquePatterns[signature] = pattern
                    }
                }
                val sortedPatterns = uniquePatterns.values
                    .sortedByDescending { it.size }
                    .take(24)
                RouteDirectionStops(
                    headsign = headsign,
                    stopPatterns = sortedPatterns
                )
            }
            if (dirList.isNotEmpty()) {
                for (alias in buildLineCodeAliases(lineKey)) {
                    result.putIfAbsent(alias, dirList)
                }
                rawRouteIdsByLine[lineKey]?.forEach { routeId ->
                    for (alias in buildLineCodeAliases(routeId)) {
                        result.putIfAbsent(alias, dirList)
                    }
                }
            }
        }
        return result
    }

    internal data class ResolvedCalendar(
        val activeServiceIds: Set<String>,
        val period: IdelisPeriod,
        val worksNotes: List<String>
    )

    internal fun resolveActiveServicesAndPeriod(
        calendars: List<GtfsCalendar>,
        calendarDates: List<GtfsCalendarDate>,
        targetCal: Calendar = Calendar.getInstance(PARIS_TZ)
    ): ResolvedCalendar {
        val todayStr = currentDateString(targetCal)
        val dayOfWeek = targetCal.get(Calendar.DAY_OF_WEEK)
        val worksNotes = mutableListOf<String>()

        fun runsOnDay(c: GtfsCalendar, dow: Int): Boolean = when (dow) {
            Calendar.MONDAY -> c.monday == 1
            Calendar.TUESDAY -> c.tuesday == 1
            Calendar.WEDNESDAY -> c.wednesday == 1
            Calendar.THURSDAY -> c.thursday == 1
            Calendar.FRIDAY -> c.friday == 1
            Calendar.SATURDAY -> c.saturday == 1
            Calendar.SUNDAY -> c.sunday == 1
            else -> false
        }

        val activeServices = mutableSetOf<String>()
        for (cal in calendars) {
            if (runsOnDay(cal, dayOfWeek) && todayStr >= cal.startDate && todayStr <= cal.endDate) {
                activeServices.add(cal.serviceId)
            }
        }

        var hasWeekdayRemovalToday = false
        for (exc in calendarDates) {
            if (exc.date == todayStr) {
                when (exc.exceptionType) {
                    1 -> activeServices.add(exc.serviceId)
                    2 -> {
                        activeServices.remove(exc.serviceId)
                        hasWeekdayRemovalToday = true
                        worksNotes.add("Service adapté / exception pour ${exc.serviceId}")
                    }
                }
            }
        }

        // Fallback si l'archive GTFS provient d'une capture antérieure (ex: Wayback Machine dont endDate < todayStr)
        if (activeServices.isEmpty() && calendars.isNotEmpty()) {
            val matchingDayCals = calendars.filter { runsOnDay(it, dayOfWeek) }
            val latestEnd = matchingDayCals.maxOfOrNull { it.endDate }
            val latestMatches = if (latestEnd != null) {
                matchingDayCals.filter { it.endDate == latestEnd }
            } else {
                matchingDayCals
            }
            val nonPvs = latestMatches.filterNot { it.serviceId.contains("PVS", ignoreCase = true) }
            val chosen = if (nonPvs.isNotEmpty()) nonPvs else latestMatches
            chosen.forEach { activeServices.add(it.serviceId) }
        }

        // Fallback supplémentaire si le GTFS utilise uniquement calendar_dates.txt sans calendar.txt
        if (activeServices.isEmpty() && calendarDates.isNotEmpty()) {
            val parseDateFmt = SimpleDateFormat("yyyyMMdd", Locale.FRANCE).apply { timeZone = PARIS_TZ }
            val probeCal = Calendar.getInstance(PARIS_TZ)
            val addedByDate = calendarDates.filter { it.exceptionType == 1 }.groupBy { it.date }
            val bestDate = addedByDate.keys.sortedDescending().firstOrNull { dateStr ->
                try {
                    val d = parseDateFmt.parse(dateStr) ?: return@firstOrNull false
                    probeCal.time = d
                    probeCal.get(Calendar.DAY_OF_WEEK) == dayOfWeek
                } catch (_: Exception) {
                    false
                }
            }
            if (bestDate != null) {
                addedByDate[bestDate].orEmpty().forEach { activeServices.add(it.serviceId) }
            }
        }

        val isHoliday = isFrenchPublicHoliday(targetCal)
        val hasDimOrFerie = activeServices.any {
            it.contains("DIM", ignoreCase = true) ||
                it.contains("FERIE", ignoreCase = true) ||
                it.contains("VERTE", ignoreCase = true)
        }
        val hasScolaire = activeServices.any {
            it.contains("SCO", ignoreCase = true) || it.contains("ORANGE", ignoreCase = true)
        }
        val hasNonScolaire = activeServices.any {
            it.contains("PVS", ignoreCase = true) ||
                it.contains("VAC", ignoreCase = true) ||
                it.contains("BLEU", ignoreCase = true) ||
                it.contains("ETE", ignoreCase = true) ||
                it.contains("NON_SCO", ignoreCase = true)
        }
        val hasBlanche = activeServices.any {
            it.contains("BLANCHE", ignoreCase = true)
        }

        val period = when {
            isHoliday -> IdelisPeriod.VERTE
            dayOfWeek == Calendar.SUNDAY -> IdelisPeriod.VERTE
            dayOfWeek == Calendar.SATURDAY -> IdelisPeriod.GRISE
            hasDimOrFerie && !hasScolaire && !hasNonScolaire && activeServices.none {
                it.contains("SEM", ignoreCase = true)
            } -> IdelisPeriod.VERTE
            hasWeekdayRemovalToday && activeServices.isEmpty() -> IdelisPeriod.VERTE
            hasBlanche && !hasScolaire && !hasNonScolaire -> IdelisPeriod.BLANCHE
            hasNonScolaire && !hasScolaire -> IdelisPeriod.BLEUE
            else -> IdelisPeriod.ORANGE
        }

        return ResolvedCalendar(
            activeServiceIds = activeServices,
            period = period,
            worksNotes = worksNotes.distinct()
        )
    }

    private fun parseCalendars(content: String): List<GtfsCalendar> {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return emptyList()
        val headers = parseCsvLine(lines.next().removePrefix("\uFEFF")).map { it.trim().removePrefix("\uFEFF") }
        val serviceIdIdx = headers.indexOf("service_id")
        val monIdx = headers.indexOf("monday")
        val tueIdx = headers.indexOf("tuesday")
        val wedIdx = headers.indexOf("wednesday")
        val thuIdx = headers.indexOf("thursday")
        val friIdx = headers.indexOf("friday")
        val satIdx = headers.indexOf("saturday")
        val sunIdx = headers.indexOf("sunday")
        val startIdx = headers.indexOf("start_date")
        val endIdx = headers.indexOf("end_date")
        if (serviceIdIdx < 0) return emptyList()

        val out = mutableListOf<GtfsCalendar>()
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            if (parts.size <= serviceIdIdx) continue
            val serviceId = parts[serviceIdIdx].trim()
            if (serviceId.isBlank()) continue
            out.add(
                GtfsCalendar(
                    serviceId = serviceId,
                    monday = parts.getOrNull(monIdx)?.toIntOrNull() ?: 0,
                    tuesday = parts.getOrNull(tueIdx)?.toIntOrNull() ?: 0,
                    wednesday = parts.getOrNull(wedIdx)?.toIntOrNull() ?: 0,
                    thursday = parts.getOrNull(thuIdx)?.toIntOrNull() ?: 0,
                    friday = parts.getOrNull(friIdx)?.toIntOrNull() ?: 0,
                    saturday = parts.getOrNull(satIdx)?.toIntOrNull() ?: 0,
                    sunday = parts.getOrNull(sunIdx)?.toIntOrNull() ?: 0,
                    startDate = parts.getOrNull(startIdx)?.trim().orEmpty(),
                    endDate = parts.getOrNull(endIdx)?.trim().orEmpty()
                )
            )
        }
        return out
    }

    private fun parseCalendarDates(content: String): List<GtfsCalendarDate> {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return emptyList()
        val headers = parseCsvLine(lines.next().removePrefix("\uFEFF")).map { it.trim().removePrefix("\uFEFF") }
        val serviceIdIdx = headers.indexOf("service_id")
        val dateIdx = headers.indexOf("date")
        val excIdx = headers.indexOf("exception_type")
        if (serviceIdIdx < 0 || dateIdx < 0) return emptyList()

        val out = mutableListOf<GtfsCalendarDate>()
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            if (parts.size <= maxOf(serviceIdIdx, dateIdx)) continue
            val serviceId = parts[serviceIdIdx].trim()
            val date = parts[dateIdx].trim()
            val exc = parts.getOrNull(excIdx)?.trim()?.toIntOrNull() ?: 1
            if (serviceId.isNotBlank() && date.isNotBlank()) {
                out.add(GtfsCalendarDate(serviceId = serviceId, date = date, exceptionType = exc))
            }
        }
        return out
    }

    private fun parseRoutes(content: String): Map<String, RouteMeta> {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return emptyMap()
        val headers = parseCsvLine(lines.next().removePrefix("\uFEFF")).map { it.trim().removePrefix("\uFEFF") }
        val idIdx = headers.indexOf("route_id")
        val shortIdx = headers.indexOf("route_short_name")
        val longIdx = headers.indexOf("route_long_name")
        val colorIdx = headers.indexOf("route_color")
        val textIdx = headers.indexOf("route_text_color")
        if (idIdx < 0) return emptyMap()

        val map = HashMap<String, RouteMeta>()
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            val id = parts.getOrNull(idIdx)?.trim().orEmpty()
            if (id.isBlank()) continue
            val shortName = parts.getOrNull(shortIdx)?.trim()?.takeIf { it.isNotBlank() } ?: id
            val longName = parts.getOrNull(longIdx)?.trim()?.takeIf { it.isNotBlank() } ?: "Ligne $shortName"
            val color = parts.getOrNull(colorIdx)?.trim()?.removePrefix("#")?.takeIf { it.isNotBlank() } ?: "009BD9"
            val text = parts.getOrNull(textIdx)?.trim()?.removePrefix("#")?.takeIf { it.isNotBlank() } ?: "FFFFFF"
            map[id] = RouteMeta(shortName, longName, color, text)
        }
        return map
    }

    private fun parseStops(content: String): Map<String, StopMeta> {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return emptyMap()
        val headers = parseCsvLine(lines.next().removePrefix("\uFEFF")).map { it.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT) }
        val idIdx = headers.indexOf("stop_id")
        val nameIdx = headers.indexOf("stop_name")
        if (idIdx < 0 || nameIdx < 0) return emptyMap()

        val map = HashMap<String, StopMeta>()
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            val id = parts.getOrNull(idIdx)?.trim().orEmpty()
            val name = parts.getOrNull(nameIdx)?.trim().orEmpty()
            if (id.isNotBlank() && name.isNotBlank()) {
                val canonical = IdelisRepository.getCanonicalStopName(name).lowercase(Locale.ROOT)
                val normalized = normalizeStopKey(name)
                map[id] = StopMeta(name, canonical, normalized)
            }
        }
        return map
    }

    private fun parseTripsForSnapshot(
        content: String,
        activeServiceIds: Set<String>
    ): Map<String, TripMeta> {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return emptyMap()
        val headers = parseCsvLine(lines.next().removePrefix("\uFEFF")).map { it.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT) }
        val tripIdIdx = headers.indexOf("trip_id")
        val routeIdIdx = headers.indexOf("route_id")
        val serviceIdIdx = headers.indexOf("service_id")
        val headsignIdx = headers.indexOf("trip_headsign")
        val directionIdIdx = headers.indexOf("direction_id")
        if (tripIdIdx < 0 || routeIdIdx < 0) return emptyMap()

        data class RawTrip(
            val tripId: String,
            val routeId: String,
            val serviceId: String,
            val headsign: String,
            val directionId: String,
            val isActive: Boolean
        )

        val rawTrips = ArrayList<RawTrip>(1024)
        val activeRouteHeadsigns = HashSet<String>()
        val filterByService = activeServiceIds.isNotEmpty()

        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            val tripId = parts.getOrNull(tripIdIdx)?.trim().orEmpty()
            val routeId = parts.getOrNull(routeIdIdx)?.trim().orEmpty()
            val serviceId = parts.getOrNull(serviceIdIdx)?.trim().orEmpty()
            val headsign = parts.getOrNull(headsignIdx)?.trim().orEmpty()
            val directionId = parts.getOrNull(directionIdIdx)?.trim().orEmpty()
            if (tripId.isNotBlank() && routeId.isNotBlank()) {
                val isActive = !filterByService || serviceId in activeServiceIds
                if (isActive) {
                    activeRouteHeadsigns.add("$routeId|$directionId|${headsign.lowercase(Locale.ROOT)}")
                }
                rawTrips.add(RawTrip(tripId, routeId, serviceId, headsign, directionId, isActive))
            }
        }

        val map = HashMap<String, TripMeta>(rawTrips.size)
        val inactiveSampleCounts = HashMap<String, Int>()
        for (rt in rawTrips) {
            val routeHeadKey = "${rt.routeId}|${rt.directionId}|${rt.headsign.lowercase(Locale.ROOT)}"
            val count = inactiveSampleCounts[routeHeadKey] ?: 0
            // Collecter tous les trajets actifs, et jusqu'à 16 trajets par sens/destination si aucun service actif
            // afin de capturer le tracé complet même si les premiers trajets du fichier sont des courses partielles.
            val collectStops = when {
                rt.isActive -> true
                routeHeadKey !in activeRouteHeadsigns && count < 16 -> {
                    inactiveSampleCounts[routeHeadKey] = count + 1
                    true
                }
                else -> false
            }
            if (rt.isActive || collectStops) {
                map[rt.tripId] = TripMeta(
                    routeId = rt.routeId,
                    serviceId = rt.serviceId,
                    headsign = rt.headsign,
                    directionId = rt.directionId,
                    isActive = rt.isActive,
                    collectRouteStops = collectStops
                )
            }
        }
        return map
    }

    private fun parseCsvLine(line: String): List<String> {
        if (!line.contains('"')) {
            return line.split(',')
        }
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
