package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Résultat du téléchargement brut de l'archive GTFS ZIP (direct ou via Wayback Machine).
 */
data class GtfsZipDownloadResult(
    val bytes: ByteArray,
    val sourceUrl: String,
    val usedWaybackMachine: Boolean,
    val archiveTimestamp: String? = null
)

/**
 * Téléchargeur résilient pour l'archive GTFS ZIP :
 * 1. Tente d'abord le téléchargement direct depuis l'URL principale.
 * 2. En cas d'échec (erreur HTTP, timeout, réseau, fichier HTML/corrompu),
 *    bascule automatiquement sur la Wayback Machine (Internet Archive) pour récupérer
 *    **la dernière archive en date** disponible :
 *    - API CDX (`web.archive.org/cdx/search/cdx?...&limit=-5`) pour obtenir les captures HTTP 200 les plus récentes
 *    - API Availability (`archive.org/wayback/available`) pour l'instantané le plus proche
 *    - Redirection dynamique vers le dernier instantané (`web.archive.org/web/2id_/...`)
 */
class WaybackGtfsFetcher(
    private val okHttpClient: OkHttpClient = defaultClient(),
    private val waybackAvailabilityUrl: String = "https://archive.org/wayback/available",
    private val waybackCdxUrl: String = "https://web.archive.org/cdx/search/cdx",
    private val waybackWebBaseUrl: String = "https://web.archive.org/web"
) {

    companion object {
        private const val TAG = "WaybackGtfsFetcher"
        const val LEGACY_ALT_GTFS_EXPORT_URL =
            "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/fichier-des-donnees-theoriques-du-reseau-idelis-au-format-gtfs/alternative_exports/https_opendata_idelis_fr_file_gtfs_multiroutes_data_gtfs_zip/"
        const val LEGACY_IDELIS_GTFS_URL_ENCODED =
            "https://opendata.idelis.fr/?file=GTFS_MultiRoutes%2FData.GTFS.zip"
        const val LEGACY_IDELIS_GTFS_URL =
            "https://opendata.idelis.fr/?file=GTFS_MultiRoutes/Data.GTFS.zip"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        /**
         * Extrait l'URL directe de téléchargement du fichier `gtfs.zip` depuis le JSON Explore v2.1
         * (`fichier-des-donnees-theoriques-du-reseau-idelis-au-format-gtfs/exports/json`).
         */
        fun extractGtfsZipUrlFromJson(jsonText: String?): String? {
            val trimmed = jsonText?.trim()?.removePrefix("\uFEFF")?.trim() ?: return null
            if (trimmed.isEmpty()) return null

            fun resolveUrl(raw: String?): String? {
                val clean = raw?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    ?: return null
                return when {
                    clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) -> clean
                    clean.startsWith("//") -> "https:$clean"
                    clean.startsWith("/") -> "https://data.idelis.fr$clean"
                    else -> null
                }
            }

            fun extractFromRecord(obj: JSONObject?): String? {
                if (obj == null) return null
                val fichier = obj.optJSONObject("fichier")
                    ?: obj.optJSONObject("file")
                    ?: obj.optJSONObject("fields")?.optJSONObject("fichier")
                return resolveUrl(fichier?.optString("url"))
            }

            return try {
                when {
                    trimmed.startsWith("[") -> {
                        val array = JSONArray(trimmed)
                        var fallbackUrl: String? = null
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            val url = extractFromRecord(obj) ?: continue
                            if (fallbackUrl == null) fallbackUrl = url
                            val id = obj.optString("id", "").trim()
                            val fichier = obj.optJSONObject("fichier")
                            val format = fichier?.optString("format", "").orEmpty().trim()
                            val filename = fichier?.optString("filename", "").orEmpty().trim()
                            if (id.equals("GTFS", ignoreCase = true) ||
                                format.equals("zip", ignoreCase = true) ||
                                filename.endsWith(".zip", ignoreCase = true)
                            ) {
                                return url
                            }
                        }
                        fallbackUrl
                    }
                    trimmed.startsWith("{") -> {
                        val root = JSONObject(trimmed)
                        val results = root.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            extractGtfsZipUrlFromJson(results.toString())
                        } else {
                            extractFromRecord(root)
                        }
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur extraction URL GTFS depuis JSON : ${e.message}")
                null
            }
        }

        /**
         * Transforme une URL d'instantané Wayback Machine en URL brute `id_` (sans réécriture HTML/toolbar)
         * et force HTTPS.
         */
        fun buildRawWaybackUrl(
            snapshotUrl: String?,
            timestamp: String?,
            originalUrl: String,
            webBaseUrl: String = "https://web.archive.org/web"
        ): String {
            if (!snapshotUrl.isNullOrBlank()) {
                val httpsUrl = snapshotUrl.trim()
                    .replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
                val regex = Regex("(/web/\\d{1,14}(?:id_)?)(/.*)")
                val match = regex.find(httpsUrl)
                if (match != null) {
                    val prefix = match.groupValues[1]
                    val suffix = match.groupValues[2]
                    return if (prefix.endsWith("id_")) {
                        httpsUrl
                    } else {
                        httpsUrl.replaceFirst(regex, "${prefix}id_${suffix}")
                    }
                }
            }
            val cleanTs = timestamp?.trim()?.takeIf { it.isNotBlank() } ?: "2"
            val base = webBaseUrl.trimEnd('/')
            return "$base/${cleanTs}id_/$originalUrl"
        }

        /**
         * Vérifie que le tableau d'octets correspond bien à une archive ZIP GTFS valide
         * (signature PK + au moins un fichier `.txt` lisible).
         */
        fun isValidGtfsZip(bytes: ByteArray?): Boolean {
            if (bytes == null || bytes.size < 4) return false
            if (bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) return false

            return try {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.endsWith(".txt", ignoreCase = true)) {
                            return true
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                false
            } catch (_: Exception) {
                false
            }
        }
    }

    private data class ResolvedZipDownload(
        val bytes: ByteArray,
        val resolvedSourceUrl: String
    )

    fun fetchGtfsZip(
        primaryUrl: String,
        isFirstSync: Boolean = false
    ): Result<GtfsZipDownloadResult> {
        val cleanPrimaryUrl = primaryUrl.trim()
        val discoveredZipUrls = mutableListOf<String>()

        // 1. Tentative directe sur l'URL principale (ZIP direct ou JSON Explore v2.1 contenant fichier.url)
        val directAttempt = downloadValidZipOrCatalog(cleanPrimaryUrl, discoveredZipUrls)
        if (directAttempt.isSuccess) {
            val downloaded = directAttempt.getOrThrow()
            return Result.success(
                GtfsZipDownloadResult(
                    bytes = downloaded.bytes,
                    sourceUrl = downloaded.resolvedSourceUrl,
                    usedWaybackMachine = false
                )
            )
        }
        val primaryError = directAttempt.exceptionOrNull()?.message ?: "échec inconnu"
        Log.w(
            TAG,
            "Échec du téléchargement GTFS direct ($primaryError) [premièreSync=$isFirstSync]. " +
                "Basculement vers la Wayback Machine (dernier instantané en date)..."
        )

        // 2. Candidats à rechercher dans la Wayback Machine
        val candidates = buildList {
            addAll(discoveredZipUrls)
            if (cleanPrimaryUrl.isNotBlank()) add(cleanPrimaryUrl)
            add(LEGACY_ALT_GTFS_EXPORT_URL)
            add(LEGACY_IDELIS_GTFS_URL_ENCODED)
            add(LEGACY_IDELIS_GTFS_URL)
        }.distinct()

        var lastWaybackError: String? = null
        val base = waybackWebBaseUrl.trimEnd('/')

        for (candidate in candidates) {
            // 2a. API CDX : récupère les captures HTTP 200 les plus récentes (triées de la plus récente à la plus ancienne)
            val cdxRes = tryWaybackCdx(candidate)
            if (cdxRes.isSuccess) {
                return cdxRes
            } else {
                lastWaybackError = cdxRes.exceptionOrNull()?.message ?: lastWaybackError
            }

            // 2b. API Availability : instantané le plus récent / le plus proche
            val availabilityRes = tryWaybackAvailability(candidate)
            if (availabilityRes.isSuccess) {
                return availabilityRes
            } else {
                lastWaybackError = availabilityRes.exceptionOrNull()?.message ?: lastWaybackError
            }

            // 2c. Redirection dynamique Wayback vers la dernière capture en date (/web/2id_/... puis /web/2/...)
            val latestRedirectUrls = listOf(
                "$base/2id_/$candidate",
                "$base/2/$candidate"
            )
            for (latestUrl in latestRedirectUrls) {
                val latestRes = downloadValidZip(latestUrl)
                if (latestRes.isSuccess) {
                    Log.i(TAG, "GTFS téléchargé via dernier instantané Wayback Machine : $latestUrl")
                    return Result.success(
                        GtfsZipDownloadResult(
                            bytes = latestRes.getOrThrow(),
                            sourceUrl = latestUrl,
                            usedWaybackMachine = true
                        )
                    )
                } else {
                    lastWaybackError = latestRes.exceptionOrNull()?.message ?: lastWaybackError
                }
            }
        }

        val combinedMsg = buildString {
            append("Échec téléchargement direct ($primaryError)")
            if (!lastWaybackError.isNullOrBlank()) {
                append(" et Wayback Machine ($lastWaybackError)")
            } else {
                append(" et aucun instantané valide trouvé sur la Wayback Machine")
            }
        }
        return Result.failure(IllegalStateException(combinedMsg))
    }

    private fun tryWaybackAvailability(targetUrl: String): Result<GtfsZipDownloadResult> {
        return try {
            val encoded = URLEncoder.encode(targetUrl, "UTF-8")
            val apiUrl = "$waybackAvailabilityUrl?url=$encoded"
            val req = Request.Builder()
                .url(apiUrl)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", userAgent())
                .build()

            val jsonStr = okHttpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    return Result.failure(IllegalStateException("Availability HTTP ${res.code}"))
                }
                res.body?.string().orEmpty()
            }
            if (jsonStr.isBlank()) {
                return Result.failure(IllegalStateException("Réponse Availability vide"))
            }

            val root = JSONObject(jsonStr)
            val snapshots = root.optJSONObject("archived_snapshots")
            val closest = snapshots?.optJSONObject("closest")
                ?: return Result.failure(IllegalStateException("Aucun instantané Availability pour $targetUrl"))

            val available = closest.optBoolean("available", false)
            val snapshotUrl = closest.optString("url", "")
            val timestamp = closest.optString("timestamp", "")

            if (!available && snapshotUrl.isBlank()) {
                return Result.failure(IllegalStateException("Instantané indisponible pour $targetUrl"))
            }

            val rawUrl = buildRawWaybackUrl(snapshotUrl, timestamp, targetUrl, waybackWebBaseUrl)
            val urlsToTry = listOfNotNull(
                rawUrl,
                snapshotUrl.takeIf { it.isNotBlank() }
                    ?.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
            ).distinct()

            var lastErr: Throwable? = null
            for (url in urlsToTry) {
                val zipRes = downloadValidZip(url)
                if (zipRes.isSuccess) {
                    Log.i(TAG, "GTFS téléchargé via Wayback Availability ($timestamp) : $url")
                    return Result.success(
                        GtfsZipDownloadResult(
                            bytes = zipRes.getOrThrow(),
                            sourceUrl = url,
                            usedWaybackMachine = true,
                            archiveTimestamp = timestamp.takeIf { it.isNotBlank() }
                        )
                    )
                } else {
                    lastErr = zipRes.exceptionOrNull()
                }
            }
            Result.failure(lastErr ?: IllegalStateException("Instantané Availability invalide"))
        } catch (e: Exception) {
            Log.w(TAG, "Erreur Wayback Availability pour $targetUrl : ${e.message}")
            Result.failure(e)
        }
    }

    private fun tryWaybackCdx(targetUrl: String): Result<GtfsZipDownloadResult> {
        return try {
            val encoded = URLEncoder.encode(targetUrl, "UTF-8")
            val apiUrl = "$waybackCdxUrl?url=$encoded&output=json&fl=timestamp,original&filter=statuscode:200&limit=-5"
            val req = Request.Builder()
                .url(apiUrl)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", userAgent())
                .build()

            val jsonStr = okHttpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    return Result.failure(IllegalStateException("CDX HTTP ${res.code}"))
                }
                res.body?.string().orEmpty()
            }
            if (jsonStr.isBlank()) {
                return Result.failure(IllegalStateException("Réponse CDX vide"))
            }

            val array = JSONArray(jsonStr)
            if (array.length() <= 1) {
                return Result.failure(IllegalStateException("Aucun instantané CDX 200 OK"))
            }

            var lastErr: Throwable? = null
            val base = waybackWebBaseUrl.trimEnd('/')
            // Parcourir du plus récent (fin) au plus ancien (index 1, car index 0 = en-têtes)
            for (i in array.length() - 1 downTo 1) {
                val row = array.optJSONArray(i) ?: continue
                val ts = row.optString(0, "").trim()
                val orig = row.optString(1, targetUrl).trim().ifBlank { targetUrl }
                if (ts.isBlank()) continue

                val urlsToTry = listOf("$base/${ts}id_/$orig", "$base/$ts/$orig")
                for (candidateUrl in urlsToTry) {
                    val zipRes = downloadValidZip(candidateUrl)
                    if (zipRes.isSuccess) {
                        Log.i(TAG, "GTFS téléchargé via Wayback CDX ($ts) : $candidateUrl")
                        return Result.success(
                            GtfsZipDownloadResult(
                                bytes = zipRes.getOrThrow(),
                                sourceUrl = candidateUrl,
                                usedWaybackMachine = true,
                                archiveTimestamp = ts
                            )
                        )
                    } else {
                        lastErr = zipRes.exceptionOrNull()
                    }
                }
            }

            Result.failure(lastErr ?: IllegalStateException("Aucun instantané CDX ZIP valide"))
        } catch (e: Exception) {
            Log.w(TAG, "Erreur Wayback CDX pour $targetUrl : ${e.message}")
            Result.failure(e)
        }
    }

    private fun downloadValidZip(url: String): Result<ByteArray> {
        return downloadValidZipOrCatalog(url, mutableListOf()).map { it.bytes }
    }

    private fun downloadValidZipOrCatalog(
        url: String,
        discoveredZipUrls: MutableList<String>
    ): Result<ResolvedZipDownload> {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgent())
                .build()

            val bytes = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                response.body?.bytes()
            }

            if (bytes == null || bytes.isEmpty()) {
                return Result.failure(IllegalStateException("Archive GTFS reçue vide"))
            }
            if (isValidGtfsZip(bytes)) {
                return Result.success(ResolvedZipDownload(bytes = bytes, resolvedSourceUrl = url))
            }

            val text = bytes.toString(Charsets.UTF_8).trim().removePrefix("\uFEFF").trim()
            if (text.startsWith("[") || text.startsWith("{")) {
                val extractedZipUrl = extractGtfsZipUrlFromJson(text)
                if (!extractedZipUrl.isNullOrBlank() && extractedZipUrl != url) {
                    Log.d(TAG, "URL gtfs.zip extraite du JSON OpenDataSoft : $extractedZipUrl")
                    if (!discoveredZipUrls.contains(extractedZipUrl)) {
                        discoveredZipUrls.add(extractedZipUrl)
                    }
                    val zipReq = Request.Builder()
                        .url(extractedZipUrl)
                        .addHeader("User-Agent", userAgent())
                        .build()

                    val zipBytes = okHttpClient.newCall(zipReq).execute().use { zipRes ->
                        if (!zipRes.isSuccessful) {
                            return Result.failure(IllegalStateException("HTTP ${zipRes.code} sur $extractedZipUrl"))
                        }
                        zipRes.body?.bytes()
                    }
                    if (zipBytes == null || zipBytes.isEmpty()) {
                        return Result.failure(IllegalStateException("Archive gtfs.zip reçue vide"))
                    }
                    if (!isValidGtfsZip(zipBytes)) {
                        return Result.failure(IllegalStateException("Fichier téléchargé depuis $extractedZipUrl invalide (pas un ZIP GTFS)"))
                    }
                    return Result.success(ResolvedZipDownload(bytes = zipBytes, resolvedSourceUrl = extractedZipUrl))
                }
            }

            Result.failure(IllegalStateException("Contenu reçu invalide (pas une archive GTFS ZIP)"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun userAgent(): String = "IdelisPauInfo-Android/${BuildConfig.VERSION_NAME}"
}
