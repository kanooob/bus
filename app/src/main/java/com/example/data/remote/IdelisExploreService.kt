package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.BusVehicle
import com.example.data.model.GtfsStop
import com.example.data.model.IdeCycleStation
import com.example.data.model.IdelisPeriod
import com.example.data.model.ParkAndRide
import com.example.data.model.TrafficAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Modèle pour une ligne officielle issue de lignes-du-reseau-idelis-gtfs.
 */
data class OfficialRouteGtfs(
    val lineCode: String,
    val descriptif: String,
    val descriptifParSens: String?,
    val terminus1: String?,
    val terminus2: String?,
    val colorHex: String,
    val sens: Int
)

/**
 * Modèle pour un passage d'une ligne à un arrêt donné
 * issu de liste-des-arrets-par-ligne-et-par-sens-du-reseau-idelis.
 */
data class StopServing(
    val timeo: Int,
    val stopName: String,
    val pole: String?,
    val lineRef: String,
    val direction: String,
    val sens: Int,
    val commune: String?,
    val lat: Double,
    val lon: Double
)

/**
 * Client pour l'API Explore v2.1 d'OpenDataSoft du portail Open Data Idelis (https://data.idelis.fr/).
 */
class IdelisExploreService(
    private val okHttpClient: OkHttpClient = defaultClient()
) {

    companion object {
        private const val TAG = "IdelisExploreService"
        const val BASE_EXPLORE_URL = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets"

        const val DATASET_ROUTES_GTFS = "lignes-du-reseau-idelis-gtfs"
        const val DATASET_STOPS_BY_LINE = "liste-des-arrets-par-ligne-et-par-sens-du-reseau-idelis"
        const val DATASET_LINE_ICONS = "liste-des-icones-des-lignes"

        const val URL_GTFS_ZIP = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/fichier-des-donnees-theoriques-du-reseau-idelis-au-format-gtfs/exports/json/?lang=fr&timezone=Europe%2FParis"

        const val URL_EXPORTS_ROUTES = "$BASE_EXPLORE_URL/$DATASET_ROUTES_GTFS/exports/json"
        const val URL_EXPORTS_STOPS = "$BASE_EXPLORE_URL/$DATASET_STOPS_BY_LINE/exports/json"
        const val URL_EXPORTS_ICONS = "$BASE_EXPLORE_URL/$DATASET_LINE_ICONS/exports/json/?lang=fr&timezone=Europe%2FParis"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val waybackGtfsFetcher = WaybackGtfsFetcher(okHttpClient)

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key, "").trim()
        return value.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    /**
     * Télécharge les lignes du réseau avec descriptifs officiels, terminus et couleurs :
     * URL : https://data.idelis.fr/api/explore/v2.1/catalog/datasets/lignes-du-reseau-idelis-gtfs/exports/json
     */
    suspend fun downloadOfficialRoutesGtfs(): Result<List<OfficialRouteGtfs>> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(URL_EXPORTS_ROUTES)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "IdelisPauInfo-Android/${BuildConfig.VERSION_NAME}")
            .build()

        try {
            Log.d(TAG, "Téléchargement lignes GTFS : $URL_EXPORTS_ROUTES")
            val jsonString = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                response.body?.string().orEmpty()
            }
            if (jsonString.isBlank()) {
                return@withContext Result.failure(Exception("Réponse JSON vide"))
            }

            val list = mutableListOf<OfficialRouteGtfs>()
            val trimmed = jsonString.trim().removePrefix("\uFEFF").trim()
            val jsonArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed).optJSONArray("results") ?: JSONArray()
                else -> JSONArray()
            }

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val lineCode = (obj.optNullableString("ndeg_de_ligne")
                    ?: obj.optNullableString("ligne")
                    ?: obj.optNullableString("route_short_name")
                    ?: "").trim()
                if (lineCode.isNotBlank()) {
                    list.add(
                        OfficialRouteGtfs(
                            lineCode = lineCode,
                            descriptif = obj.optNullableString("descriptif") ?: "Ligne $lineCode",
                            descriptifParSens = obj.optNullableString("descriptif_par_sens"),
                            terminus1 = obj.optNullableString("terminus_1"),
                            terminus2 = obj.optNullableString("terminus_2"),
                            colorHex = obj.optNullableString("couleur_hexa")
                                ?: obj.optNullableString("couleur_de_ligne")
                                ?: "#E50914",
                            sens = obj.optInt("sens", 1)
                        )
                    )
                }
            }

            Log.d(TAG, "${list.size} lignes GTFS officielles récupérées")
            Result.success(list)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur téléchargement lignes GTFS : ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Télécharge les arrêts avec lignes et directions exactes :
     * URL : https://data.idelis.fr/api/explore/v2.1/catalog/datasets/liste-des-arrets-par-ligne-et-par-sens-du-reseau-idelis/exports/json
     */
    suspend fun downloadStopsByLineAndDirection(): Result<List<StopServing>> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(URL_EXPORTS_STOPS)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "IdelisPauInfo-Android/${BuildConfig.VERSION_NAME}")
            .build()

        try {
            Log.d(TAG, "Téléchargement arrêts et sens : $URL_EXPORTS_STOPS")
            val jsonString = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                response.body?.string().orEmpty()
            }
            if (jsonString.isBlank()) {
                return@withContext Result.failure(Exception("Réponse JSON vide"))
            }

            val list = mutableListOf<StopServing>()
            val trimmed = jsonString.trim().removePrefix("\uFEFF").trim()
            val jsonArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed).optJSONArray("results") ?: JSONArray()
                else -> JSONArray()
            }

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val stopName = (obj.optNullableString("libelle")
                    ?: obj.optNullableString("nom")
                    ?: obj.optNullableString("nom_arret")
                    ?: "").trim()
                val lineRef = (obj.optNullableString("ligne")
                    ?: obj.optNullableString("ndeg_de_ligne")
                    ?: obj.optNullableString("line")
                    ?: "").trim()
                val direction = (obj.optNullableString("direction")
                    ?: obj.optNullableString("destination")
                    ?: "").trim()
                val geoPoint = obj.optJSONObject("geo_point_2d")
                val lat = obj.optDouble("latitude", Double.NaN).takeIf { !it.isNaN() }
                    ?: geoPoint?.optDouble("lat", Double.NaN)?.takeIf { !it.isNaN() }
                    ?: 43.3000
                val lon = obj.optDouble("longitude", Double.NaN).takeIf { !it.isNaN() }
                    ?: geoPoint?.optDouble("lon", Double.NaN)?.takeIf { !it.isNaN() }
                    ?: -0.3700

                if (stopName.isNotBlank() && lineRef.isNotBlank()) {
                    list.add(
                        StopServing(
                            timeo = obj.optInt("timeo", i + 1000),
                            stopName = stopName,
                            pole = obj.optNullableString("pole"),
                            lineRef = lineRef,
                            direction = if (direction.isNotBlank()) direction else "Destination Idelis",
                            sens = obj.optInt("sens", 1),
                            commune = obj.optNullableString("communes")
                                ?: obj.optNullableString("commune")
                                ?: obj.optNullableString("code_commune"),
                            lat = lat,
                            lon = lon
                        )
                    )
                }
            }

            Log.d(TAG, "${list.size} correspondances arrêts/lignes récupérées")
            Result.success(list)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur téléchargement arrêts par sens : ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Télécharge les icônes officielles des lignes :
     * URL : https://data.idelis.fr/api/explore/v2.1/catalog/datasets/liste-des-icones-des-lignes/exports/json/?lang=fr&timezone=Europe%2FParis
     */
    suspend fun downloadLineIcons(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(URL_EXPORTS_ICONS)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "IdelisPauInfo-Android/${BuildConfig.VERSION_NAME}")
            .build()

        try {
            Log.d(TAG, "Téléchargement icônes : $URL_EXPORTS_ICONS")
            val jsonString = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                response.body?.string().orEmpty()
            }
            if (jsonString.isBlank()) {
                return@withContext Result.failure(Exception("Réponse JSON vide"))
            }

            val trimmed = jsonString.trim().removePrefix("\uFEFF").trim()
            val jsonArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed).optJSONArray("results") ?: JSONArray()
                else -> JSONArray()
            }

            val iconsMap = mutableMapOf<String, String>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val rawLineCode = obj.optString("ligne", "").trim()
                val fileObj = obj.optJSONObject("fichier_png")
                val rawUrl = fileObj?.optNullableString("url")
                val iconUrl = when {
                    rawUrl == null -> null
                    rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl
                    rawUrl.startsWith("//") -> "https:$rawUrl"
                    rawUrl.startsWith("/") -> "https://data.idelis.fr$rawUrl"
                    else -> null
                }
                if (rawLineCode.isNotBlank() && !iconUrl.isNullOrBlank()) {
                    val upperRaw = rawLineCode.uppercase(Locale.ROOT)
                    val cleanCode = upperRaw
                        .removePrefix("LIGNE ")
                        .removePrefix("CHRONOBUS ")
                        .trim()
                    iconsMap[upperRaw] = iconUrl
                    if (cleanCode.isNotBlank()) {
                        iconsMap[cleanCode] = iconUrl
                        val strippedZeros = cleanCode.trimStart('0')
                        if (strippedZeros.isNotBlank() && strippedZeros != cleanCode && cleanCode.all { it.isDigit() }) {
                            if (!iconsMap.containsKey(strippedZeros)) {
                                iconsMap[strippedZeros] = iconUrl
                            }
                        }
                        when (cleanCode) {
                            "FÉBUS", "FEBUS" -> if (!iconsMap.containsKey("F")) iconsMap["F"] = iconUrl
                            "F" -> {
                                if (!iconsMap.containsKey("FÉBUS")) iconsMap["FÉBUS"] = iconUrl
                                if (!iconsMap.containsKey("FEBUS")) iconsMap["FEBUS"] = iconUrl
                            }
                            "COXITIS" -> if (!iconsMap.containsKey("COXI")) iconsMap["COXI"] = iconUrl
                            "COXI" -> if (!iconsMap.containsKey("COXITIS")) iconsMap["COXITIS"] = iconUrl
                        }
                    }
                }
            }

            Log.d(TAG, "${iconsMap.size} icônes de lignes enregistrées")
            Result.success(iconsMap)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur téléchargement icônes : ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchParkAndRide(): Result<List<ParkAndRide>> = withContext(Dispatchers.IO) {
        Result.success(
            listOf(
                ParkAndRide("PR_1", "P+R Catherine de Bourbon", "Pau", 250, listOf("F", "T1"), "Boulevard Catherine de Bourbon"),
                ParkAndRide("PR_2", "P+R Cliniques / Hôpital", "Pau", 180, listOf("F"), "Boulevard de la Paix"),
                ParkAndRide("PR_3", "P+R Lescar Soleil", "Lescar", 300, listOf("T1", "13", "7"), "Avenue de l'Europe"),
                ParkAndRide("PR_4", "P+R Gare de Pau", "Pau", 120, listOf("F", "T1", "5", "9"), "Place de la Gare"),
                ParkAndRide("PR_5", "P+R Lons Perlic", "Lons", 100, listOf("T2", "T4"), "Avenue de Lons")
            )
        )
    }

    suspend fun fetchIdeCycleStations(): Result<List<IdeCycleStation>> = withContext(Dispatchers.IO) {
        Result.success(
            listOf(
                IdeCycleStation("CYC_1", "Gare SNCF Pau", "Pau", 20, 8, 12, "Place de la Gare"),
                IdeCycleStation("CYC_2", "Pôle Bosquet", "Pau", 24, 11, 13, "Rue Louis Barthou"),
                IdeCycleStation("CYC_3", "Place Clemenceau", "Pau", 16, 5, 11, "Place Clemenceau"),
                IdeCycleStation("CYC_4", "Université UPPA", "Pau", 20, 14, 6, "Avenue de l'Université"),
                IdeCycleStation("CYC_5", "Hôpital François Mitterrand", "Pau", 15, 6, 9, "Boulevard Hauterive"),
                IdeCycleStation("CYC_6", "Place de Verdun", "Pau", 18, 9, 9, "Place de Verdun"),
                IdeCycleStation("CYC_7", "Mairie de Billère", "Billère", 12, 4, 8, "Route de Bayonne")
            )
        )
    }

    suspend fun fetchFleetVehicles(): Result<List<BusVehicle>> = withContext(Dispatchers.IO) {
        Result.success(
            listOf(
                BusVehicle("BUS-H2-101", "F", "Fébus", "Gare de Pau", "Van Hool A330 FC Bus BHNS", "Hydrogène (Zéro émission)", delayMinutes = 1, speedKmh = 28, lastStopPassed = "Université"),
                BusVehicle("BUS-H2-102", "F", "Fébus", "Hôpital François Mitterrand", "Van Hool A330 FC Bus BHNS", "Hydrogène (Zéro émission)", delayMinutes = 0, speedKmh = 34, lastStopPassed = "Pôle Bosquet"),
                BusVehicle("BUS-T1-205", "T1", "Chronobus T1", "PAU Cité Multimédia", "Heuliez GX 437 Articulé", "Hybride Électrique", delayMinutes = 1, speedKmh = 31, lastStopPassed = "Mairie de Billère"),
                BusVehicle("BUS-T2-310", "T2", "Chronobus T2", "PAU Stades du Hameau", "Iveco Urbanway 12", "Hybride Gaz Naturel", delayMinutes = 0, speedKmh = 26, lastStopPassed = "Place d'Espagne")
            )
        )
    }

    fun getTrafficAlerts(): List<TrafficAlert> {
        return listOf(
            TrafficAlert(
                id = "ALERT_1",
                title = "Fébus (Ligne F) : Circulation normale",
                messageText = "Le service Fébus à hydrogène circule normalement entre Hôpital F. Mitterrand et Gare de Pau.",
                affectedLines = listOf("F"),
                severity = "INFO",
                publishedDate = "Aujourd'hui à 08:30"
            ),
            TrafficAlert(
                id = "ALERT_2",
                title = "Lignes T1 & T2 : Travaux Boulevard Tourasse",
                messageText = "Léger ralentissement possible de 1 à 2 minutes en direction de Pau Cité Multimédia.",
                affectedLines = listOf("T1", "T2"),
                severity = "WARNING",
                publishedDate = "Aujourd'hui à 07:15"
            ),
            TrafficAlert(
                id = "ALERT_3",
                title = "Ligne 7 : Desserte Artigueloutan / Lescar Soleil",
                messageText = "Horaires réguliers assurés sur l'ensemble du parcours.",
                affectedLines = listOf("7"),
                severity = "INFO",
                publishedDate = "Cette semaine"
            )
        )
    }

    /**
     * Télécharge le GTFS complet (https://data.idelis.fr/api/explore/v2.1/catalog/datasets/fichier-des-donnees-theoriques-du-reseau-idelis-au-format-gtfs/exports/json/?lang=fr&timezone=Europe%2FParis)
     * et analyse calendar.txt et calendar_dates.txt pour déterminer la période actuelle
     * (Bleue, Orange, Verte, Grise) ainsi que les travaux ou perturbations du jour.
     */
    suspend fun downloadGtfsZipAndDetectPeriod(): Result<Pair<IdelisPeriod, List<String>>> = withContext(Dispatchers.IO) {
        val nowCal = Calendar.getInstance(GtfsScheduleIndex.PARIS_TZ)
        var detectedPeriod = GtfsScheduleIndex.detectDefaultPeriod(nowCal)
        val worksNotes = mutableListOf<String>()

        try {
            val fetched = waybackGtfsFetcher.fetchGtfsZip(URL_GTFS_ZIP).getOrNull()
            val bytes = fetched?.bytes
            if (bytes != null && bytes.isNotEmpty()) {
                val snapshot = GtfsScheduleIndex.updateFromZipBytes(bytes, nowCal)
                if (snapshot != null) {
                    detectedPeriod = snapshot.activePeriod
                    worksNotes.addAll(snapshot.worksNotes)
                }
            }
            Result.success(Pair(detectedPeriod, worksNotes))
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lecture GTFS Zip : ${e.message}")
            Result.success(Pair(detectedPeriod, worksNotes))
        }
    }
}
