package com.example.data.remote

import android.util.Log
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
class IdelisExploreService {

    companion object {
        private const val TAG = "IdelisExploreService"
        const val BASE_EXPLORE_URL = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets"

        const val DATASET_ROUTES_GTFS = "lignes-du-reseau-idelis-gtfs"
        const val DATASET_STOPS_BY_LINE = "liste-des-arrets-par-ligne-et-par-sens-du-reseau-idelis"
        const val DATASET_LINE_ICONS = "liste-des-icones-des-lignes"

        const val URL_GTFS_ZIP = "https://opendata.idelis.fr/?file=GTFS_MultiRoutes/Data.GTFS.zip"

        const val URL_EXPORTS_ROUTES = "$BASE_EXPLORE_URL/$DATASET_ROUTES_GTFS/exports/json"
        const val URL_EXPORTS_STOPS = "$BASE_EXPLORE_URL/$DATASET_STOPS_BY_LINE/exports/json"
        const val URL_EXPORTS_ICONS = "$BASE_EXPLORE_URL/$DATASET_LINE_ICONS/exports/json"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Télécharge les lignes du réseau avec descriptifs officiels, terminus et couleurs :
     * URL : https://data.idelis.fr/api/explore/v2.1/catalog/datasets/lignes-du-reseau-idelis-gtfs/exports/json
     */
    suspend fun downloadOfficialRoutesGtfs(): Result<List<OfficialRouteGtfs>> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(URL_EXPORTS_ROUTES)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "IdelisPauInfo-Android/1.0")
            .build()

        try {
            Log.d(TAG, "Téléchargement lignes GTFS : $URL_EXPORTS_ROUTES")
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val jsonString = response.body?.string().orEmpty()
            if (jsonString.isBlank()) {
                return@withContext Result.failure(Exception("Réponse JSON vide"))
            }

            val list = mutableListOf<OfficialRouteGtfs>()
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val lineCode = obj.optString("ndeg_de_ligne", "").trim()
                if (lineCode.isNotBlank()) {
                    list.add(
                        OfficialRouteGtfs(
                            lineCode = lineCode,
                            descriptif = obj.optString("descriptif", "Ligne $lineCode"),
                            descriptifParSens = obj.optString("descriptif_par_sens", null),
                            terminus1 = obj.optString("terminus_1", null),
                            terminus2 = obj.optString("terminus_2", null),
                            colorHex = obj.optString("couleur_hexa", "#E50914"),
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
            .addHeader("User-Agent", "IdelisPauInfo-Android/1.0")
            .build()

        try {
            Log.d(TAG, "Téléchargement arrêts et sens : $URL_EXPORTS_STOPS")
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val jsonString = response.body?.string().orEmpty()
            if (jsonString.isBlank()) {
                return@withContext Result.failure(Exception("Réponse JSON vide"))
            }

            val list = mutableListOf<StopServing>()
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val stopName = obj.optString("libelle", "").trim()
                val lineRef = obj.optString("ligne", "").trim()
                val direction = obj.optString("direction", "").trim()

                if (stopName.isNotBlank() && lineRef.isNotBlank()) {
                    list.add(
                        StopServing(
                            timeo = obj.optInt("timeo", i + 1000),
                            stopName = stopName,
                            pole = obj.optString("pole", null)?.trim(),
                            lineRef = lineRef,
                            direction = if (direction.isNotBlank()) direction else "Destination Idelis",
                            sens = obj.optInt("sens", 1),
                            commune = obj.optString("communes", null)?.trim(),
                            lat = obj.optDouble("latitude", 43.3000),
                            lon = obj.optDouble("longitude", -0.3700)
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
     * URL : https://data.idelis.fr/api/explore/v2.1/catalog/datasets/liste-des-icones-des-lignes/exports/json
     */
    suspend fun downloadLineIcons(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(URL_EXPORTS_ICONS)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "IdelisPauInfo-Android/1.0")
            .build()

        try {
            Log.d(TAG, "Téléchargement icônes : $URL_EXPORTS_ICONS")
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val jsonString = response.body?.string().orEmpty()
            if (jsonString.isBlank()) {
                return@withContext Result.failure(Exception("Réponse JSON vide"))
            }

            val iconsMap = mutableMapOf<String, String>()
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val lineCode = obj.optString("ligne", "").trim()
                val fileObj = obj.optJSONObject("fichier_png")
                val iconUrl = fileObj?.optString("url", null)
                if (lineCode.isNotBlank() && !iconUrl.isNullOrBlank()) {
                    iconsMap[lineCode.uppercase()] = iconUrl
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
     * Télécharge le GTFS complet (https://opendata.idelis.fr/?file=GTFS_MultiRoutes/Data.GTFS.zip)
     * et analyse calendar.txt et calendar_dates.txt pour déterminer la période actuelle
     * (Bleue, Orange, Verte, Grise) ainsi que les travaux ou perturbations du jour.
     */
    suspend fun downloadGtfsZipAndDetectPeriod(): Result<Pair<IdelisPeriod, List<String>>> = withContext(Dispatchers.IO) {
        val nowCal = Calendar.getInstance()
        val dayOfWeek = nowCal.get(Calendar.DAY_OF_WEEK)
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.FRANCE).format(nowCal.time)

        // Période de base selon les règles Idelis :
        // - Orange : Lundi au vendredi (scolaire)
        // - Bleue : Lundi au vendredi (non scolaire / vacances)
        // - Grise : Samedi
        // - Verte : Dimanche et jours fériés
        var detectedPeriod = when (dayOfWeek) {
            Calendar.SATURDAY -> IdelisPeriod.GRISE
            Calendar.SUNDAY -> IdelisPeriod.VERTE
            else -> IdelisPeriod.ORANGE // Semaine normale scolaire par défaut
        }

        val worksNotes = mutableListOf<String>()

        try {
            val request = Request.Builder()
                .url(URL_GTFS_ZIP)
                .addHeader("User-Agent", "IdelisPauInfo-Android/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val zipStream = ZipInputStream(ByteArrayInputStream(bytes))
                    var entry = zipStream.nextEntry
                    var calendarContent: String? = null
                    var calendarDatesContent: String? = null

                    while (entry != null) {
                        if (entry.name == "calendar.txt") {
                            calendarContent = zipStream.bufferedReader(Charsets.UTF_8).readText()
                        } else if (entry.name == "calendar_dates.txt") {
                            calendarDatesContent = zipStream.bufferedReader(Charsets.UTF_8).readText()
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                    zipStream.close()

                    // Analyse de calendar.txt pour les vacances scolaires (PVS = Petites Vacances Scolaires -> Période Bleue non scolaire)
                    if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY && calendarContent != null) {
                        for (line in calendarContent.lineSequence()) {
                            val parts = line.split(",")
                            if (parts.size >= 10) {
                                val serviceId = parts[0]
                                val startDate = parts[8]
                                val endDate = parts[9]
                                if (serviceId.contains("PVS", ignoreCase = true) && todayStr >= startDate && todayStr <= endDate) {
                                    detectedPeriod = IdelisPeriod.BLEUE
                                    break
                                }
                            }
                        }
                    }

                    // Analyse de calendar_dates.txt pour les travaux et suppressions de service
                    if (calendarDatesContent != null) {
                        for (line in calendarDatesContent.lineSequence()) {
                            val parts = line.split(",")
                            if (parts.size >= 3) {
                                val serviceId = parts[0]
                                val date = parts[1]
                                val exceptionType = parts[2].trim()
                                if (date == todayStr && exceptionType == "2") {
                                    worksNotes.add("Alerte travaux / déviation : service $serviceId adapté")
                                }
                            }
                        }
                    }
                }
            }
            Result.success(Pair(detectedPeriod, worksNotes))
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lecture GTFS Zip : ${e.message}")
            Result.success(Pair(detectedPeriod, worksNotes))
        }
    }
}
