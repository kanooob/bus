package com.example.data.remote

import android.net.Uri
import android.os.Build
import com.example.BuildConfig
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Service discret et silencieux envoyant un ping quotidien à l'ouverture de l'application
 * afin de comptabiliser les utilisateurs actifs (version de l'app et version d'Android).
 */
class UsageStatsPingService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val BASE_PING_URL = "https://stats.galaxie.workers.dev/ping"
        const val DEFAULT_DELAY_MS = 4_000L

        fun currentDayKey(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }

        fun resolveAndroidVersion(): String {
            val release = Build.VERSION.RELEASE.orEmpty().trim()
            return release.ifEmpty { Build.VERSION.SDK_INT.toString() }
        }

        fun buildPingUrl(
            appVersion: String = BuildConfig.VERSION_NAME,
            androidVersion: String = resolveAndroidVersion()
        ): String {
            val encodedAppVersion = Uri.encode(appVersion)
            val encodedAndroidVersion = Uri.encode(androidVersion)
            return "$BASE_PING_URL?app_v=$encodedAppVersion&os_v=$encodedAndroidVersion"
        }
    }

    /**
     * Envoie au maximum 1 requête par jour à l'ouverture de l'application, après un léger délai,
     * de manière totalement silencieuse même en cas d'échec réseau ou HTTP.
     */
    suspend fun pingIfDue(
        preferencesManager: PreferencesManager?,
        delayMs: Long = DEFAULT_DELAY_MS,
        todayProvider: () -> String = ::currentDayKey,
        appVersion: String = BuildConfig.VERSION_NAME,
        androidVersion: String = resolveAndroidVersion()
    ) {
        if (preferencesManager == null || !preferencesManager.isUsageStatsEnabled()) return

        val today = todayProvider()
        if (preferencesManager.getLastUsageStatsPingDate() == today) return

        if (delayMs > 0L) {
            delay(delayMs)
        }

        // Re-vérifier après le délai au cas où l'utilisateur aurait désactivé l'option
        if (!preferencesManager.isUsageStatsEnabled()) return
        if (preferencesManager.getLastUsageStatsPingDate() == today) return

        preferencesManager.saveLastUsageStatsPingDate(today)

        val url = buildPingUrl(appVersion = appVersion, androidVersion = androidVersion)
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                client.newCall(request).execute().use { _ ->
                    // Silencieux quel que soit le code de retour
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Silencieux même en cas d'échec
            }
        }
    }
}
