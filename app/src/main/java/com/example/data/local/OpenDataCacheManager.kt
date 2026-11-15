package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.data.remote.OfficialRouteGtfs
import com.example.data.remote.StopServing
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Cache disque léger pour les données de référence Idelis (icônes des lignes, fiches lignes,
 * correspondances d'arrêts et archive GTFS ZIP).
 * Permet un démarrage instantané hors réseau et évite de re-télécharger ces référentiels à chaque ouverture.
 */
class OpenDataCacheManager(context: Context) {

    data class CachedEntry<T>(
        val value: T,
        val savedAtMs: Long
    ) {
        fun isFresh(ttlMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
            return savedAtMs > 0L && (nowMs - savedAtMs) in 0..ttlMs
        }
    }

    private val cacheDir = File(context.applicationContext.cacheDir, "idelis_opendata_cache").apply {
        if (!exists()) mkdirs()
    }

    private val iconsFile = File(cacheDir, "line_icons_cache.json")
    private val routesFile = File(cacheDir, "official_routes_cache.json")
    private val stopsFile = File(cacheDir, "stops_by_line_cache.json")
    private val gtfsZipFile = File(cacheDir, "gtfs_latest.zip")
    private val gtfsMetaFile = File(cacheDir, "gtfs_latest_meta.json")

    companion object {
        private const val TAG = "OpenDataCacheManager"
        const val DEFAULT_METADATA_TTL_MS = 24L * 60L * 60L * 1000L // 24 heures
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        val raw = optString(key, "").trim()
        return raw.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private fun writeTextAtomically(target: File, content: String) {
        synchronized(this) {
            try {
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val tmp = File(cacheDir, "${target.name}.tmp")
                tmp.writeText(content, Charsets.UTF_8)
                if (!tmp.renameTo(target)) {
                    target.writeText(content, Charsets.UTF_8)
                    tmp.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur écriture cache ${target.name} : ${e.message}")
            }
        }
    }

    fun loadLineIcons(): CachedEntry<Map<String, String>>? {
        return try {
            if (!iconsFile.exists()) return null
            val root = JSONObject(iconsFile.readText(Charsets.UTF_8))
            val ts = root.optLong("timestamp", 0L)
            val iconsObj = root.optJSONObject("icons") ?: return null
            val map = HashMap<String, String>(iconsObj.length())
            val keys = iconsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = iconsObj.optString(k, "").trim()
                if (k.isNotBlank() && v.isNotBlank()) {
                    map[k] = v
                }
            }
            if (map.isEmpty()) null else CachedEntry(map, ts)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lecture cache icônes : ${e.message}")
            null
        }
    }

    fun saveLineIcons(icons: Map<String, String>, savedAtMs: Long = System.currentTimeMillis()) {
        if (icons.isEmpty()) return
        val iconsObj = JSONObject()
        for ((k, v) in icons) {
            iconsObj.put(k, v)
        }
        val root = JSONObject().apply {
            put("timestamp", savedAtMs)
            put("icons", iconsObj)
        }
        writeTextAtomically(iconsFile, root.toString())
    }

    fun loadOfficialRoutes(): CachedEntry<List<OfficialRouteGtfs>>? {
        return try {
            if (!routesFile.exists()) return null
            val root = JSONObject(routesFile.readText(Charsets.UTF_8))
            val ts = root.optLong("timestamp", 0L)
            val arr = root.optJSONArray("routes") ?: return null
            val list = ArrayList<OfficialRouteGtfs>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val lineCode = obj.optString("lineCode", "").trim()
                if (lineCode.isNotBlank()) {
                    list.add(
                        OfficialRouteGtfs(
                            lineCode = lineCode,
                            descriptif = obj.optString("descriptif", "Ligne $lineCode"),
                            descriptifParSens = obj.optNullableString("descriptifParSens"),
                            terminus1 = obj.optNullableString("terminus1"),
                            terminus2 = obj.optNullableString("terminus2"),
                            colorHex = obj.optString("colorHex", "#E50914"),
                            sens = obj.optInt("sens", 1)
                        )
                    )
                }
            }
            if (list.isEmpty()) null else CachedEntry(list, ts)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lecture cache lignes : ${e.message}")
            null
        }
    }

    fun saveOfficialRoutes(routes: List<OfficialRouteGtfs>, savedAtMs: Long = System.currentTimeMillis()) {
        if (routes.isEmpty()) return
        val arr = JSONArray()
        for (r in routes) {
            arr.put(
                JSONObject().apply {
                    put("lineCode", r.lineCode)
                    put("descriptif", r.descriptif)
                    put("descriptifParSens", r.descriptifParSens ?: JSONObject.NULL)
                    put("terminus1", r.terminus1 ?: JSONObject.NULL)
                    put("terminus2", r.terminus2 ?: JSONObject.NULL)
                    put("colorHex", r.colorHex)
                    put("sens", r.sens)
                }
            )
        }
        val root = JSONObject().apply {
            put("timestamp", savedAtMs)
            put("routes", arr)
        }
        writeTextAtomically(routesFile, root.toString())
    }

    fun loadStopsByLine(): CachedEntry<List<StopServing>>? {
        return try {
            if (!stopsFile.exists()) return null
            val root = JSONObject(stopsFile.readText(Charsets.UTF_8))
            val ts = root.optLong("timestamp", 0L)
            val arr = root.optJSONArray("stops") ?: return null
            val list = ArrayList<StopServing>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val stopName = obj.optString("stopName", "").trim()
                val lineRef = obj.optString("lineRef", "").trim()
                if (stopName.isNotBlank() && lineRef.isNotBlank()) {
                    list.add(
                        StopServing(
                            timeo = obj.optInt("timeo", i + 1000),
                            stopName = stopName,
                            pole = obj.optNullableString("pole"),
                            lineRef = lineRef,
                            direction = obj.optString("direction", "Destination Idelis"),
                            sens = obj.optInt("sens", 1),
                            commune = obj.optNullableString("commune"),
                            lat = obj.optDouble("lat", 43.3000),
                            lon = obj.optDouble("lon", -0.3700)
                        )
                    )
                }
            }
            if (list.isEmpty()) null else CachedEntry(list, ts)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lecture cache arrêts : ${e.message}")
            null
        }
    }

    fun saveStopsByLine(stops: List<StopServing>, savedAtMs: Long = System.currentTimeMillis()) {
        if (stops.isEmpty()) return
        val arr = JSONArray()
        for (s in stops) {
            arr.put(
                JSONObject().apply {
                    put("timeo", s.timeo)
                    put("stopName", s.stopName)
                    put("pole", s.pole ?: JSONObject.NULL)
                    put("lineRef", s.lineRef)
                    put("direction", s.direction)
                    put("sens", s.sens)
                    put("commune", s.commune ?: JSONObject.NULL)
                    put("lat", s.lat)
                    put("lon", s.lon)
                }
            )
        }
        val root = JSONObject().apply {
            put("timestamp", savedAtMs)
            put("stops", arr)
        }
        writeTextAtomically(stopsFile, root.toString())
    }

    fun loadGtfsZipBytes(): CachedEntry<ByteArray>? {
        return try {
            if (!gtfsZipFile.exists() || gtfsZipFile.length() <= 0L) return null
            val bytes = gtfsZipFile.readBytes()
            if (bytes.isEmpty()) return null
            val ts = if (gtfsMetaFile.exists()) {
                JSONObject(gtfsMetaFile.readText(Charsets.UTF_8)).optLong("timestamp", gtfsZipFile.lastModified())
            } else {
                gtfsZipFile.lastModified()
            }
            CachedEntry(bytes, ts)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lecture cache GTFS ZIP : ${e.message}")
            null
        }
    }

    fun saveGtfsZipBytes(bytes: ByteArray, savedAtMs: Long = System.currentTimeMillis()) {
        if (bytes.isEmpty()) return
        synchronized(this) {
            try {
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val tmp = File(cacheDir, "${gtfsZipFile.name}.tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(gtfsZipFile)) {
                    gtfsZipFile.writeBytes(bytes)
                    tmp.delete()
                }
                val metaJson = JSONObject().apply { put("timestamp", savedAtMs) }.toString()
                gtfsMetaFile.writeText(metaJson, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "Erreur sauvegarde cache GTFS ZIP : ${e.message}")
            }
        }
    }

    fun clearAll() {
        synchronized(this) {
            listOf(iconsFile, routesFile, stopsFile, gtfsZipFile, gtfsMetaFile).forEach { file ->
                try {
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
        }
    }
}
