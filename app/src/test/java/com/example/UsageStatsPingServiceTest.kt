package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.data.remote.UsageStatsPingService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsageStatsPingServiceTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = PreferencesManager(context)
    }

    @Test
    fun `usage stats is enabled by default and builds expected ping url`() {
        assertTrue(prefs.isUsageStatsEnabled())
        val url = UsageStatsPingService.buildPingUrl(appVersion = "2.1.1", androidVersion = "14")
        assertEquals("https://stats.galaxie.workers.dev/ping?app_v=2.1.1&os_v=14", url)
    }

    @Test
    fun `ping sends once per day when enabled and skips subsequent launches same day`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrls.add(chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody(null))
                    .build()
            }
            .build()

        val service = UsageStatsPingService(mockClient)

        service.pingIfDue(
            preferencesManager = prefs,
            delayMs = 0L,
            todayProvider = { "2026-11-14" },
            appVersion = "2.1.1",
            androidVersion = "14"
        )
        assertEquals(1, requestedUrls.size)
        assertEquals("https://stats.galaxie.workers.dev/ping?app_v=2.1.1&os_v=14", requestedUrls[0])

        // Deuxième ouverture le même jour -> pas de requête
        service.pingIfDue(
            preferencesManager = prefs,
            delayMs = 0L,
            todayProvider = { "2026-11-14" },
            appVersion = "2.1.1",
            androidVersion = "14"
        )
        assertEquals(1, requestedUrls.size)

        // Jour suivant -> 1 nouvelle requête
        service.pingIfDue(
            preferencesManager = prefs,
            delayMs = 0L,
            todayProvider = { "2026-11-15" },
            appVersion = "2.1.1",
            androidVersion = "14"
        )
        assertEquals(2, requestedUrls.size)
    }

    @Test
    fun `ping does not send when disabled in preferences`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrls.add(chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody(null))
                    .build()
            }
            .build()

        prefs.setUsageStatsEnabled(false)
        assertFalse(prefs.isUsageStatsEnabled())

        val service = UsageStatsPingService(mockClient)
        service.pingIfDue(
            preferencesManager = prefs,
            delayMs = 0L,
            todayProvider = { "2026-11-14" },
            appVersion = "2.1.1",
            androidVersion = "14"
        )

        assertTrue(requestedUrls.isEmpty())
    }

    @Test
    fun `ping fails silently on network or http errors and still counts once per day`() = runBlocking {
        var attempts = 0
        val failingClient = OkHttpClient.Builder()
            .addInterceptor {
                attempts++
                throw IOException("Network unreachable")
            }
            .build()

        val service = UsageStatsPingService(failingClient)

        service.pingIfDue(
            preferencesManager = prefs,
            delayMs = 0L,
            todayProvider = { "2026-11-14" },
            appVersion = "2.1.1",
            androidVersion = "14"
        )
        service.pingIfDue(
            preferencesManager = prefs,
            delayMs = 0L,
            todayProvider = { "2026-11-14" },
            appVersion = "2.1.1",
            androidVersion = "14"
        )

        assertEquals(1, attempts)
    }
}
