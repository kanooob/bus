package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long = 0L,
    val isNewerThanCurrent: Boolean
)

sealed class AppUpdateState {
    data object Idle : AppUpdateState()
    data object Checking : AppUpdateState()
    data class UpToDate(val currentVersion: String) : AppUpdateState()
    data class UpdateAvailableWaitingWifi(val release: GitHubReleaseInfo) : AppUpdateState()
    data class Downloading(
        val release: GitHubReleaseInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateState()
    data class ReadyToInstall(
        val release: GitHubReleaseInfo,
        val apkFilePath: String
    ) : AppUpdateState()
    data class Error(
        val message: String,
        val release: GitHubReleaseInfo? = null
    ) : AppUpdateState()
}

/**
 * Nettoie le Markdown d'une description de release GitHub pour un affichage lisible dans l'UI et les notifications.
 */
fun formatReleaseNotesForDisplay(rawNotes: String?): String {
    if (rawNotes.isNullOrBlank()) return ""
    return rawNotes
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .lineSequence()
        .map { line ->
            val trimmed = line.trimEnd()
            val withoutHeaders = trimmed.replace(Regex("^#{1,6}\\s*"), "")
            val withoutBold = withoutHeaders
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("__(.+?)__"), "$1")
                .replace(Regex("`([^`]+)`"), "$1")
            if (withoutBold.startsWith("- ") || withoutBold.startsWith("* ")) {
                "• " + withoutBold.substring(2).trimStart()
            } else {
                withoutBold
            }
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

/**
 * Service de vérification, téléchargement (sous Wi-Fi) et installation d'APK depuis GitHub Releases
 * (par défaut https://github.com/kanooob/bus).
 */
class GitHubUpdaterService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "GitHubUpdaterService"
        const val DEFAULT_REPO_URL = "https://github.com/kanooob/bus"
    }

    /**
     * Extrait (owner, repo) depuis une URL GitHub ou un slug "owner/repo".
     */
    fun parseOwnerAndRepo(repoUrl: String): Pair<String, String> {
        val cleaned = repoUrl.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = cleaned.split("/").filter { it.isNotBlank() }
        val owner = parts.getOrNull(0) ?: "kanooob"
        val repo = parts.getOrNull(1) ?: "bus"
        return owner to repo
    }

    /**
     * Compare deux versions sémantiques (ex: "v1.2.0" vs "1.0").
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val remoteParts = normalizeVersionParts(remoteTag)
        val currentParts = normalizeVersionParts(currentVersion)
        if (remoteParts.isEmpty()) return false
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun normalizeVersionParts(version: String): List<Int> {
        val trimmed = version.trim()
        val firstDigitIdx = trimmed.indexOfFirst { it.isDigit() }
        if (firstDigitIdx < 0) return emptyList()
        val cleaned = trimmed.substring(firstDigitIdx)
            .substringBefore("-")
            .substringBefore("+")
        return cleaned.split(".")
            .mapNotNull { part -> part.filter { it.isDigit() }.toIntOrNull() }
    }

    /**
     * Vérifie si l'appareil est actuellement connecté en Wi-Fi.
     */
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de vérifier l'état Wi-Fi: ${e.message}")
            false
        }
    }

    /**
     * Interroge l'API GitHub Releases pour récupérer la dernière version (ou un tag spécifique) et son éventuel APK.
     */
    suspend fun checkLatestRelease(
        repoUrl: String = DEFAULT_REPO_URL,
        currentVersion: String,
        targetTag: String = "latest"
    ): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val (owner, repo) = parseOwnerAndRepo(repoUrl)
            val cleanTag = targetTag.trim()
            val primaryUrl = if (cleanTag.isNotBlank() && !cleanTag.equals("latest", ignoreCase = true)) {
                "https://api.github.com/repos/$owner/$repo/releases/tags/$cleanTag"
            } else {
                "https://api.github.com/repos/$owner/$repo/releases/latest"
            }

            val latestReq = Request.Builder()
                .url(primaryUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "GalaxieBus-Android/$currentVersion")
                .build()

            val releaseJson: JSONObject? = client.newCall(latestReq).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body?.string().orEmpty()
                        if (body.isNotBlank()) JSONObject(body) else null
                    }
                    response.code == 404 && (cleanTag.isBlank() || cleanTag.equals("latest", ignoreCase = true)) -> {
                        // Repli vers la liste complète des releases (si seule une pre-release existe)
                        val listReq = Request.Builder()
                            .url("https://api.github.com/repos/$owner/$repo/releases?per_page=1")
                            .header("Accept", "application/vnd.github+json")
                            .header("User-Agent", "GalaxieBus-Android/$currentVersion")
                            .build()
                        client.newCall(listReq).execute().use { listResp ->
                            if (!listResp.isSuccessful) {
                                return@withContext Result.failure(
                                    Exception("Aucune release publiée trouvée sur GitHub ($owner/$repo, HTTP ${listResp.code})")
                                )
                            }
                            val arrStr = listResp.body?.string().orEmpty()
                            val arr = JSONArray(arrStr)
                            if (arr.length() > 0) arr.getJSONObject(0) else null
                        }
                    }
                    else -> {
                        return@withContext Result.failure(
                            Exception("Erreur API GitHub HTTP ${response.code}: ${response.message}")
                        )
                    }
                }
            }

            if (releaseJson == null) {
                return@withContext Result.failure(
                    Exception("Aucune release disponible sur $owner/$repo")
                )
            }

            val tagName = releaseJson.optString("tag_name", "").trim()
            val versionName = tagName.removePrefix("v").removePrefix("V").ifBlank { tagName }
            val releaseName = releaseJson.optString("name", tagName).ifBlank { tagName }
            val releaseNotes = releaseJson.optString("body", "").let {
                if (it == "null") "" else it.trim()
            }
            val htmlUrl = releaseJson.optString("html_url", "https://github.com/$owner/$repo/releases")

            var apkDownloadUrl: String? = null
            var apkFileName: String? = null
            var apkSizeBytes = 0L

            val assets = releaseJson.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() && it != "null" }
                        apkFileName = name
                        apkSizeBytes = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val isNewer = isNewerVersion(tagName, currentVersion)
            Result.success(
                GitHubReleaseInfo(
                    tagName = tagName,
                    versionName = versionName,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    htmlUrl = htmlUrl,
                    apkDownloadUrl = apkDownloadUrl,
                    apkFileName = apkFileName,
                    apkSizeBytes = apkSizeBytes,
                    isNewerThanCurrent = isNewer
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur vérification mise à jour GitHub: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Télécharge l'APK depuis GitHub vers le dossier cache/updates avec suivi de progression.
     */
    suspend fun downloadApk(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val url = release.apkDownloadUrl
            ?: return@withContext Result.failure(IllegalStateException("Aucun fichier APK attaché à la release ${release.tagName}"))

        val updatesDir = File(context.cacheDir, "updates").apply {
            if (!exists()) mkdirs()
        }
        val safeName = (release.apkFileName ?: "galaxie-bus-${release.versionName}.apk")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val finalFile = File(updatesDir, safeName)
        val tempFile = File(updatesDir, "$safeName.part")

        try {
            // Si déjà téléchargé avec la bonne taille, réutiliser directement
            if (finalFile.exists() && release.apkSizeBytes > 0L && finalFile.length() == release.apkSizeBytes) {
                onProgress(100, finalFile.length(), finalFile.length())
                return@withContext Result.success(finalFile)
            }

            // Nettoyer les anciens APK
            updatesDir.listFiles()?.forEach { file ->
                if (file.name != finalFile.name) {
                    file.delete()
                }
            }

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "GalaxieBus-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec téléchargement APK HTTP ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Corps APK vide"))
                val totalBytes = body.contentLength().let { len ->
                    if (len > 0) len else release.apkSizeBytes
                }

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastReportedPercent = -1
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            val percent = if (totalBytes > 0) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent, downloaded, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(100, finalFile.length(), finalFile.length())
            Result.success(finalFile)
        } catch (e: Exception) {
            tempFile.delete()
            Log.e(TAG, "Erreur téléchargement APK: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Vérifie si l'application a l'autorisation d'installer des applications inconnues (Android 8+).
     */
    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Crée l'Intent vers les paramètres Android pour autoriser l'installation d'applications inconnues.
     */
    fun buildInstallUnknownAppsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    /**
     * Crée l'Intent système pour lancer l'installation de l'APK via FileProvider.
     */
    fun buildInstallApkIntent(context: Context, apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
