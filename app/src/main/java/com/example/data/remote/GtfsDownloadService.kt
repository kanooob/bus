package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.IdelisDatabase
import com.example.data.model.GtfsRoute
import com.example.data.model.GtfsStop
import com.example.data.model.GtfsStopTime
import com.example.data.model.GtfsSyncReport
import com.example.data.model.GtfsTrip
import com.example.data.model.IdelisPeriod
import com.example.data.repository.IdelisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Service dédié au téléchargement et à l'analyse complète du fichier ZIP GTFS d'Idelis
 * (https://opendata.idelis.fr/?file=GTFS_MultiRoutes/Data.GTFS.zip ou URL alternative)
 * et des autres jeux de données complémentaires OpenDataSoft (liste des PR, vélos, véhicules, grilles).
 *
 * Extrait et parse en mémoire/streaming :
 * - stops.txt -> Arrêts géolocalisés avec regroupement canonique des quais
 * - routes.txt -> Lignes avec codes, noms longs, couleurs hex
 * - trips.txt -> Trajets et destinations (trip_headsign)
 * - stop_times.txt -> Horaires théoriques de passage
 * - calendar.txt -> Périodes de circulation (Bleue, Orange, Verte, Grise)
 * - calendar_dates.txt -> Travaux et suppressions d'exceptions
 */
class GtfsDownloadService(
    private val database: IdelisDatabase,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "GtfsDownloadService"
        const val DEFAULT_GTFS_ZIP_URL = "https://opendata.idelis.fr/?file=GTFS_MultiRoutes/Data.GTFS.zip"
        
        // URLs directes des autres fichiers utiles du réseau Idelis
        const val URL_PR_EXPORT = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/liste-des-pr/exports/json"
        const val URL_IDECYCLE_EXPORT = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/stations-velo-en-libre-service-idecycle/exports/json"
        const val URL_FLEET_EXPORT = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/liste-des-vehicules-du-parc-idelis/exports/json"
        const val URL_STOPS_GEOJSON = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/liste-des-points-darret-de-bus-du-reseau-idelis/exports/geojson"
    }

    /**
     * Télécharge le fichier Zip GTFS depuis l'URL spécifiée, extrait chaque table standard
     * et met à jour le référentiel Room local.
     */
    suspend fun downloadAndProcessGtfsZip(
        url: String = DEFAULT_GTFS_ZIP_URL
    ): GtfsSyncReport = withContext(Dispatchers.IO) {
        val downloadedFiles = mutableListOf<String>()
        var detectedPeriod = IdelisPeriod.VERTE
        val worksList = mutableListOf<String>()

        val rawStops = mutableListOf<GtfsStop>()
        val rawRoutes = mutableListOf<GtfsRoute>()
        val rawTrips = mutableListOf<GtfsTrip>()
        val rawStopTimes = mutableListOf<GtfsStopTime>()
        val rawCalendars = mutableListOf<com.example.data.model.GtfsCalendar>()
        val rawCalendarDates = mutableListOf<com.example.data.model.GtfsCalendarDate>()

        try {
            Log.d(TAG, "Téléchargement du ZIP GTFS Idelis depuis : $url")
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "IdelisPauInfo-Android/${BuildConfig.VERSION_NAME}")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Échec téléchargement HTTP ${response.code}")
                return@withContext GtfsSyncReport(
                    success = false,
                    errorMessage = "Erreur HTTP ${response.code} lors du téléchargement du GTFS"
                )
            }

            val bytes = response.body?.bytes()
            if (bytes == null || bytes.isEmpty()) {
                return@withContext GtfsSyncReport(
                    success = false,
                    errorMessage = "Fichier GTFS ZIP reçu vide"
                )
            }

            Log.d(TAG, "Archive reçue (${bytes.size / 1024} Ko). Décompression et analyse...")
            val nowCal = Calendar.getInstance()
            val dayOfWeek = nowCal.get(Calendar.DAY_OF_WEEK)
            val todayStr = SimpleDateFormat("yyyyMMdd", Locale.FRANCE).format(nowCal.time)

            // Détection de période selon les règles Idelis :
            // - Orange : Lundi au vendredi (scolaire)
            // - Bleue : Lundi au vendredi (non scolaire / vacances)
            // - Grise : Samedi
            // - Verte : Dimanche et jours fériés
            detectedPeriod = when (dayOfWeek) {
                Calendar.SATURDAY -> IdelisPeriod.GRISE
                Calendar.SUNDAY -> IdelisPeriod.VERTE
                else -> IdelisPeriod.ORANGE // Lundi au vendredi scolaire par défaut
            }

            val zipStream = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zipStream.nextEntry

            while (entry != null) {
                val name = entry.name.substringAfterLast("/")
                if (name.endsWith(".txt")) {
                    downloadedFiles.add(name)
                }

                when (name) {
                    "stops.txt" -> {
                        parseStopsTxt(zipStream.bufferedReader(Charsets.UTF_8).readText(), rawStops)
                    }
                    "routes.txt" -> {
                        parseRoutesTxt(zipStream.bufferedReader(Charsets.UTF_8).readText(), rawRoutes)
                    }
                    "trips.txt" -> {
                        parseTripsTxt(zipStream.bufferedReader(Charsets.UTF_8).readText(), rawTrips)
                    }
                    "stop_times.txt" -> {
                        parseStopTimesTxt(zipStream.bufferedReader(Charsets.UTF_8).readText(), rawStopTimes)
                    }
                    "calendar.txt" -> {
                        val text = zipStream.bufferedReader(Charsets.UTF_8).readText()
                        parseCalendarTxt(text, rawCalendars)
                        // Analyse PVS (Petites Vacances Scolaires -> Période Bleue non scolaire)
                        if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                            for (line in text.lineSequence()) {
                                val parts = parseCsvLine(line)
                                if (parts.size >= 10) {
                                    val serviceId = parts[0]
                                    val startDate = parts[8]
                                    val endDate = parts[9]
                                    if (serviceId.contains("PVS", ignoreCase = true) && todayStr in startDate..endDate) {
                                        detectedPeriod = IdelisPeriod.BLEUE
                                        break
                                    }
                                }
                            }
                        }
                    }
                    "calendar_dates.txt" -> {
                        val text = zipStream.bufferedReader(Charsets.UTF_8).readText()
                        parseCalendarDatesTxt(text, rawCalendarDates)
                        for (line in text.lineSequence()) {
                            val parts = parseCsvLine(line)
                            if (parts.size >= 3) {
                                val serviceId = parts[0]
                                val date = parts[1]
                                val exceptionType = parts[2].trim()
                                if (date == todayStr && exceptionType == "2") {
                                    worksList.add("Déviation / travaux en cours : service adapté pour $serviceId")
                                }
                            }
                        }
                    }
                }

                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()

            Log.d(TAG, "Fichiers GTFS analysés : ${downloadedFiles.joinToString()}")
            Log.d(TAG, "Extraits : ${rawStops.size} arrêts, ${rawRoutes.size} lignes, ${rawTrips.size} trips, ${rawStopTimes.size} horaires")

            // Sauvegarde dans la base de données Room locale
            // Les arrêts théoriques ne sont pas insérés dans stopDao (seuls les arrêts réels reçus par requête sont conservés)
            Log.d(TAG, "${rawStops.size} arrêts du référentiel GTFS analysés")

            if (rawRoutes.isNotEmpty()) {
                database.routeDao().clearAllRoutes()
                database.routeDao().insertRoutes(rawRoutes)
            }

            if (rawTrips.isNotEmpty()) {
                database.stopTimeDao().clearAllTrips()
                database.stopTimeDao().insertTrips(rawTrips.take(3000))
            }

            if (rawStopTimes.isNotEmpty()) {
                database.stopTimeDao().clearAllStopTimes()
                database.stopTimeDao().insertStopTimes(rawStopTimes.take(5000))
            }

            if (rawCalendars.isNotEmpty()) {
                database.calendarDao().clearAllCalendars()
                database.calendarDao().insertCalendars(rawCalendars)
            }

            if (rawCalendarDates.isNotEmpty()) {
                database.calendarDao().clearAllCalendarDates()
                database.calendarDao().insertCalendarDates(rawCalendarDates)
            }

            GtfsSyncReport(
                stopsCount = if (rawStops.isNotEmpty()) rawStops.size else database.stopDao().getStopCount(),
                routesCount = if (rawRoutes.isNotEmpty()) rawRoutes.size else database.routeDao().getRouteCount(),
                tripsCount = rawTrips.size,
                stopTimesCount = rawStopTimes.size,
                activePeriod = detectedPeriod,
                detectedWorks = worksList,
                downloadedFiles = downloadedFiles,
                success = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur traitement GTFS : ${e.message}", e)
            GtfsSyncReport(
                success = false,
                errorMessage = e.message ?: "Erreur inconnue lors du traitement du GTFS"
            )
        }
    }

    /**
     * Télécharge d'autres jeux de données Open Data Idelis complémentaires :
     * - Parkings Relais (P+R)
     * - Stations Vélos IdeCycle
     * - Liste des véhicules du parc
     */
    suspend fun downloadSupplementaryOpenDataFiles(): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        val endpoints = listOf(
            "Parkings Relais (P+R)" to URL_PR_EXPORT,
            "Stations IdeCycle" to URL_IDECYCLE_EXPORT,
            "Véhicules du Parc" to URL_FLEET_EXPORT
        )

        for ((label, url) in endpoints) {
            try {
                val req = Request.Builder().url(url).addHeader("Accept", "application/json").build()
                val res = okHttpClient.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    results.add("$label : ${body.length} octets téléchargés")
                } else {
                    results.add("$label : HTTP ${res.code}")
                }
            } catch (e: Exception) {
                results.add("$label : Erreur (${e.message})")
            }
        }
        results
    }

    private fun parseStopsTxt(content: String, outList: MutableList<GtfsStop>) {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return

        val headerLine = lines.next()
        val headers = parseCsvLine(headerLine)
        val idIdx = headers.indexOf("stop_id")
        val nameIdx = headers.indexOf("stop_name")
        val latIdx = headers.indexOf("stop_lat")
        val lonIdx = headers.indexOf("stop_lon")
        val descIdx = headers.indexOf("stop_desc")

        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            if (parts.size > maxOf(idIdx, nameIdx) && idIdx >= 0 && nameIdx >= 0) {
                val id = parts[idIdx].trim()
                val name = parts[nameIdx].trim()
                val lat = if (latIdx in parts.indices) parts[latIdx].toDoubleOrNull() ?: 0.0 else 0.0
                val lon = if (lonIdx in parts.indices) parts[lonIdx].toDoubleOrNull() ?: 0.0 else 0.0
                val desc = if (descIdx in parts.indices) parts[descIdx] else null

                if (id.isNotBlank() && name.isNotBlank()) {
                    outList.add(
                        GtfsStop(
                            stopId = id,
                            stopName = name,
                            stopDesc = desc,
                            stopLat = lat,
                            stopLon = lon
                        )
                    )
                }
            }
        }
    }

    private fun parseRoutesTxt(content: String, outList: MutableList<GtfsRoute>) {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return

        val headerLine = lines.next()
        val headers = parseCsvLine(headerLine)
        val idIdx = headers.indexOf("route_id")
        val shortNameIdx = headers.indexOf("route_short_name")
        val longNameIdx = headers.indexOf("route_long_name")
        val colorIdx = headers.indexOf("route_color")
        val textColorIdx = headers.indexOf("route_text_color")

        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            if (parts.size > idIdx && idIdx >= 0) {
                val id = parts[idIdx].trim()
                val short = if (shortNameIdx in parts.indices) parts[shortNameIdx].trim() else id
                val long = if (longNameIdx in parts.indices) parts[longNameIdx].trim() else ""
                val color = if (colorIdx in parts.indices && parts[colorIdx].isNotBlank()) parts[colorIdx].trim().removePrefix("#") else "009BD9"
                val text = if (textColorIdx in parts.indices && parts[textColorIdx].isNotBlank()) parts[textColorIdx].trim().removePrefix("#") else "FFFFFF"

                if (id.isNotBlank()) {
                    outList.add(
                        GtfsRoute(
                            routeId = id,
                            routeShortName = short.ifBlank { id },
                            routeLongName = long.ifBlank { "Ligne $short" },
                            routeColor = color,
                            routeTextColor = text
                        )
                    )
                }
            }
        }
    }

    private fun parseTripsTxt(content: String, outList: MutableList<GtfsTrip>) {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return

        val headerLine = lines.next()
        val headers = parseCsvLine(headerLine)
        val tripIdIdx = headers.indexOf("trip_id")
        val routeIdIdx = headers.indexOf("route_id")
        val serviceIdIdx = headers.indexOf("service_id")
        val headsignIdx = headers.indexOf("trip_headsign")

        var count = 0
        while (lines.hasNext() && count < 2500) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            if (parts.size > maxOf(tripIdIdx, routeIdIdx) && tripIdIdx >= 0 && routeIdIdx >= 0) {
                val tripId = parts[tripIdIdx].trim()
                val routeId = parts[routeIdIdx].trim()
                val serviceId = if (serviceIdIdx in parts.indices) parts[serviceIdIdx].trim() else "default"
                val headsign = if (headsignIdx in parts.indices) parts[headsignIdx].trim() else "Direction"

                if (tripId.isNotBlank() && routeId.isNotBlank()) {
                    outList.add(
                        GtfsTrip(
                            tripId = tripId,
                            routeId = routeId,
                            serviceId = serviceId,
                            tripHeadsign = headsign
                        )
                    )
                    count++
                }
            }
        }
    }

    private fun parseStopTimesTxt(content: String, outList: MutableList<GtfsStopTime>) {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return

        val headerLine = lines.next()
        val headers = parseCsvLine(headerLine)
        val tripIdIdx = headers.indexOf("trip_id")
        val arrTimeIdx = headers.indexOf("arrival_time")
        val depTimeIdx = headers.indexOf("departure_time")
        val stopIdIdx = headers.indexOf("stop_id")
        val seqIdx = headers.indexOf("stop_sequence")

        var count = 0
        while (lines.hasNext() && count < 4000) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            if (parts.size > maxOf(tripIdIdx, stopIdIdx) && tripIdIdx >= 0 && stopIdIdx >= 0) {
                val tripId = parts[tripIdIdx].trim()
                val stopId = parts[stopIdIdx].trim()
                val arr = if (arrTimeIdx in parts.indices) parts[arrTimeIdx].trim() else "00:00:00"
                val dep = if (depTimeIdx in parts.indices) parts[depTimeIdx].trim() else arr
                val seq = if (seqIdx in parts.indices) parts[seqIdx].trim().toIntOrNull() ?: count else count

                if (tripId.isNotBlank() && stopId.isNotBlank()) {
                    outList.add(
                        GtfsStopTime(
                            tripId = tripId,
                            stopId = stopId,
                            arrivalTime = arr,
                            departureTime = dep,
                            stopSequence = seq
                        )
                    )
                    count++
                }
            }
        }
    }

    private fun parseCalendarTxt(content: String, outList: MutableList<com.example.data.model.GtfsCalendar>) {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return

        val headerLine = lines.next()
        val headers = parseCsvLine(headerLine)
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

        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            if (parts.size > serviceIdIdx && serviceIdIdx >= 0) {
                val serviceId = parts[serviceIdIdx].trim()
                if (serviceId.isNotBlank()) {
                    outList.add(
                        com.example.data.model.GtfsCalendar(
                            serviceId = serviceId,
                            monday = if (monIdx in parts.indices) parts[monIdx].toIntOrNull() ?: 0 else 0,
                            tuesday = if (tueIdx in parts.indices) parts[tueIdx].toIntOrNull() ?: 0 else 0,
                            wednesday = if (wedIdx in parts.indices) parts[wedIdx].toIntOrNull() ?: 0 else 0,
                            thursday = if (thuIdx in parts.indices) parts[thuIdx].toIntOrNull() ?: 0 else 0,
                            friday = if (friIdx in parts.indices) parts[friIdx].toIntOrNull() ?: 0 else 0,
                            saturday = if (satIdx in parts.indices) parts[satIdx].toIntOrNull() ?: 0 else 0,
                            sunday = if (sunIdx in parts.indices) parts[sunIdx].toIntOrNull() ?: 0 else 0,
                            startDate = if (startIdx in parts.indices) parts[startIdx].trim() else "",
                            endDate = if (endIdx in parts.indices) parts[endIdx].trim() else ""
                        )
                    )
                }
            }
        }
    }

    private fun parseCalendarDatesTxt(content: String, outList: MutableList<com.example.data.model.GtfsCalendarDate>) {
        val lines = content.lineSequence().iterator()
        if (!lines.hasNext()) return

        val headerLine = lines.next()
        val headers = parseCsvLine(headerLine)
        val serviceIdIdx = headers.indexOf("service_id")
        val dateIdx = headers.indexOf("date")
        val exceptionIdx = headers.indexOf("exception_type")

        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            if (parts.size > maxOf(serviceIdIdx, dateIdx) && serviceIdIdx >= 0 && dateIdx >= 0) {
                val serviceId = parts[serviceIdIdx].trim()
                val date = parts[dateIdx].trim()
                val exc = if (exceptionIdx in parts.indices) parts[exceptionIdx].toIntOrNull() ?: 1 else 1

                if (serviceId.isNotBlank() && date.isNotBlank()) {
                    outList.add(
                        com.example.data.model.GtfsCalendarDate(
                            serviceId = serviceId,
                            date = date,
                            exceptionType = exc
                        )
                    )
                }
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
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
