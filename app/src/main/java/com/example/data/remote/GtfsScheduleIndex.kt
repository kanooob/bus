package com.example.data.remote

import android.util.Log
import com.example.data.model.GtfsCalendar
import com.example.data.model.GtfsCalendarDate
import com.example.data.model.GtfsTheoreticalDeparture
import com.example.data.model.IdelisPeriod
import com.example.data.repository.IdelisRepository
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

    data class Snapshot(
        val dateStr: String,
        val activePeriod: IdelisPeriod,
        val worksNotes: List<String>,
        val departuresByCanonicalStop: Map<String, List<GtfsTheoreticalDeparture>>,
        val departuresByNormalizedStop: Map<String, List<GtfsTheoreticalDeparture>>,
        val activeServiceIds: Set<String>
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
        val headsign: String
    )

    @Volatile
    var current: Snapshot? = null
        private set

    @Volatile
    private var cachedZipBytes: ByteArray? = null

    fun getCachedZipBytes(): ByteArray? = cachedZipBytes

    fun currentDateString(cal: Calendar = Calendar.getInstance(PARIS_TZ)): String {
        return SimpleDateFormat("yyyyMMdd", Locale.FRANCE).apply {
            timeZone = PARIS_TZ
        }.format(cal.time)
    }

    fun detectDefaultPeriod(cal: Calendar = Calendar.getInstance(PARIS_TZ)): IdelisPeriod {
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
            .replace(Regex("""^(pau|lescar|lons|billere|bizanos|jurancon|serres-castet|artigueloutan|mazeres)\s+"""), "")
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
                    val name = entry.name.substringAfterLast('/')
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
            val activeTripsMap = tripsText?.let { parseActiveTrips(it, activeServiceIds) }.orEmpty()

            val byCanonical = HashMap<String, MutableList<GtfsTheoreticalDeparture>>()
            val byNormalized = HashMap<String, MutableList<GtfsTheoreticalDeparture>>()

            // Passe 2 : streaming de stop_times.txt en filtrant uniquement les trajets actifs du jour
            if (activeTripsMap.isNotEmpty() && stopsMap.isNotEmpty()) {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.substringAfterLast('/')
                        if (name == "stop_times.txt") {
                            val reader = zis.bufferedReader(Charsets.UTF_8)
                            val headerLine = reader.readLine()?.removePrefix("\uFEFF") ?: break
                            val headers = parseCsvLine(headerLine).map { it.trim().removePrefix("\uFEFF") }
                            val tripIdx = headers.indexOf("trip_id")
                            val arrIdx = headers.indexOf("arrival_time")
                            val depIdx = headers.indexOf("departure_time")
                            val stopIdx = headers.indexOf("stop_id")

                            if (tripIdx >= 0 && stopIdx >= 0) {
                                var line = reader.readLine()
                                while (line != null) {
                                    if (line.isNotBlank()) {
                                        val parts = parseCsvLine(line)
                                        if (parts.size > maxOf(tripIdx, stopIdx)) {
                                            val tripId = parts[tripIdx].trim()
                                            val trip = activeTripsMap[tripId]
                                            if (trip != null) {
                                                val stopId = parts[stopIdx].trim()
                                                val stopMeta = stopsMap[stopId]
                                                if (stopMeta != null) {
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

            val todayStr = currentDateString(targetCal)
            Log.d(
                TAG,
                "Index GTFS prêt pour $todayStr (${activePeriod.label}) : " +
                    "${activeServiceIds.size} services actifs, ${activeTripsMap.size} trajets, " +
                    "${byCanonical.size} arrêts indexés"
            )

            Snapshot(
                dateStr = todayStr,
                activePeriod = activePeriod,
                worksNotes = worksNotes,
                departuresByCanonicalStop = byCanonical,
                departuresByNormalizedStop = byNormalized,
                activeServiceIds = activeServiceIds
            )
        } catch (e: Exception) {
            Log.w(TAG, "Erreur indexation GTFS : ${e.message}")
            null
        }
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

        val period = when {
            dayOfWeek == Calendar.SATURDAY -> IdelisPeriod.GRISE
            dayOfWeek == Calendar.SUNDAY -> IdelisPeriod.VERTE
            activeServices.any {
                it.contains("DIM", ignoreCase = true) || it.contains("FERIE", ignoreCase = true)
            } && activeServices.none {
                it.contains("SEM", ignoreCase = true) || it.contains("SCO", ignoreCase = true)
            } -> IdelisPeriod.VERTE
            hasWeekdayRemovalToday && activeServices.isEmpty() -> IdelisPeriod.VERTE
            activeServices.any {
                it.contains("PVS", ignoreCase = true) || it.contains("VAC", ignoreCase = true)
            } -> IdelisPeriod.BLEUE
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
        val headers = parseCsvLine(lines.next().removePrefix("\uFEFF")).map { it.trim().removePrefix("\uFEFF") }
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

    private fun parseActiveTrips(
        content: String,
        activeServiceIds: Set<String>
    ): Map<String, TripMeta> {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return emptyMap()
        val headers = parseCsvLine(lines.next().removePrefix("\uFEFF")).map { it.trim().removePrefix("\uFEFF") }
        val tripIdIdx = headers.indexOf("trip_id")
        val routeIdIdx = headers.indexOf("route_id")
        val serviceIdIdx = headers.indexOf("service_id")
        val headsignIdx = headers.indexOf("trip_headsign")
        if (tripIdIdx < 0 || routeIdIdx < 0) return emptyMap()

        val map = HashMap<String, TripMeta>()
        val filterByService = activeServiceIds.isNotEmpty()
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            val tripId = parts.getOrNull(tripIdIdx)?.trim().orEmpty()
            val routeId = parts.getOrNull(routeIdIdx)?.trim().orEmpty()
            val serviceId = parts.getOrNull(serviceIdIdx)?.trim().orEmpty()
            val headsign = parts.getOrNull(headsignIdx)?.trim().orEmpty()
            if (tripId.isNotBlank() && routeId.isNotBlank()) {
                if (!filterByService || serviceId in activeServiceIds) {
                    map[tripId] = TripMeta(routeId, serviceId, headsign)
                }
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
