package com.example

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IdelisDatabase
import com.example.data.local.OpenDataCacheManager
import com.example.data.local.PreferencesManager
import com.example.data.model.DepartureComparison
import com.example.data.model.DepartureStatus
import com.example.data.model.GtfsStop
import com.example.data.model.IdelisPeriod
import com.example.data.model.RouteLineDetail
import com.example.data.model.TrafficAlert
import com.example.data.remote.GtfsDownloadService
import com.example.data.remote.GtfsScheduleIndex
import com.example.data.remote.HttpStatusException
import com.example.data.remote.IdelisExploreService
import com.example.data.remote.IdelisSiriService
import com.example.data.remote.IdelisWebScraperService
import com.example.data.remote.NetworkErrorHelper
import com.example.data.repository.IdelisRepository
import com.example.ui.components.RouteStopsModal
import com.example.ui.components.TrafficInfoModal
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.widget.IdelisAppWidgetProvider
import com.example.ui.widget.IdelisWidgetPreferences
import com.example.ui.widget.WidgetConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.time.Duration
import java.util.zip.ZipException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ResilienceAndErrorHandlingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun pumpLooperUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val shadowMainLooper = shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !predicate()) {
            shadowMainLooper.idleFor(Duration.ofMillis(80))
            Thread.sleep(20L)
        }
        shadowMainLooper.idle()
    }

    @Test
    fun `NetworkErrorHelper classifies ZipException SecurityException and UnknownServiceException`() {
        assertTrue(
            NetworkErrorHelper.formatError(ZipException("invalid entry"))
                .contains("Archive", ignoreCase = true)
        )
        assertTrue(
            NetworkErrorHelper.formatError(UnknownServiceException("CLEARTEXT communication not permitted"))
                .contains("sécurisée", ignoreCase = true)
        )
        assertTrue(
            NetworkErrorHelper.formatError(SecurityException("Permission denied (missing INTERNET?)"))
                .contains("permissions", ignoreCase = true)
        )
    }

    @Test
    fun `IdelisExploreService defaults missing coords to 0_0 and supports GeoJSON coordinates`() = runBlocking {
        val jsonPayload = """
            [
              {
                "nom_arret": "Nouvel Arrêt Sans Coords",
                "numero_ligne": "T5",
                "sens": "A",
                "pole": "NouvelPole",
                "commune": "Lons"
              },
              {
                "nom_arret": "Arrêt GeoJSON",
                "numero_ligne": "F",
                "sens": "R",
                "pole": "GeoPole",
                "geometry": { "type": "Point", "coordinates": [-0.368, 43.301] }
              }
            ]
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonPayload.toResponseBody(null))
                    .build()
            }
            .build()

        val service = IdelisExploreService(client)
        val result = service.downloadStopsByLineAndDirection()
        assertTrue(result.isSuccess)
        val stops = result.getOrThrow()
        assertEquals(2, stops.size)

        assertEquals(0.0, stops[0].lat, 0.0001)
        assertEquals(0.0, stops[0].lon, 0.0001)
        assertEquals("Lons", stops[0].commune)

        assertEquals(43.301, stops[1].lat, 0.0001)
        assertEquals(-0.368, stops[1].lon, 0.0001)
    }

    @Test
    fun `IdelisExploreService downloadGtfsZipAndDetectPeriod returns failure on HTTP 503`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("Maintenance".toResponseBody(null))
                    .build()
            }
            .build()

        val service = IdelisExploreService(client)
        val result = service.downloadGtfsZipAndDetectPeriod()
        assertTrue("Doit retourner Result.failure en cas d'erreur réseau", result.isFailure)
        assertTrue(result.exceptionOrNull() is HttpStatusException)
    }

    @Test
    fun `IdelisWebScraperService parses fallback selectors alternative time formats and propagates HTTP 500`() = runBlocking {
        val altHtml = """
            <article class="line-group">
                <img src="/assets/pictos/line_T10.png" alt="T10" />
                <span class="stop-direction">Direction: BILLERE Mairie - Arrêt: Bosquet Quai B</span>
                <div class="horaire-box">15h41</div>
                <div class="horaire-box bg-primary">< 1 min</div>
                <div class="horaire-box">24:10</div>
            </article>
        """.trimIndent()

        var return500 = false
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (return500) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Internal Server Error")
                        .body("Error".toResponseBody(null))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(altHtml.toResponseBody(null))
                        .build()
                }
            }
            .build()

        val scraper = IdelisWebScraperService(client)
        val okRes = scraper.getStopMonitoring("Bosquet")
        assertTrue(okRes.isSuccess)
        val deps = okRes.getOrThrow().departures
        assertEquals(3, deps.size)
        assertEquals("T10", deps[0].lineRef)
        assertEquals("BILLERE Mairie", deps[0].direction)
        assertEquals("Quai B", deps[0].stopQuai)
        val imminent = deps.first { it.timeText == "< 1 min" }
        assertTrue(imminent.isRealTime)
        assertEquals(0, imminent.waitMinutes)
        assertTrue(deps.any { it.timeText == "15h41" })
        assertTrue(deps.any { it.timeText == "24:10" })

        return500 = true
        val errRes = scraper.getStopMonitoring("Bosquet")
        assertTrue(errRes.isFailure)
        assertTrue(errRes.exceptionOrNull() is HttpStatusException)
    }

    @Test
    fun `IdelisRepository dynamically generates colors and contrasting text for future lines`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = IdelisDatabase.getInstance(context)
        val repo = IdelisRepository(
            database = db,
            appContext = context,
            preferencesManager = PreferencesManager(context)
        )
        val (bg1, fg1) = repo.getRouteColors("T5")
        val (bg2, fg2) = repo.getRouteColors("T5")
        assertEquals(bg1, bg2)
        assertEquals(fg1, fg2)
        assertTrue(bg1.length == 6)
        assertTrue(fg1 == "FFFFFF" || fg1 == "121212")

        assertEquals(bg1, IdelisRepository.deterministicColorHex("T5"))
        assertEquals("121212", IdelisRepository.computeContrastTextHex("FFF59D"))
        assertEquals("FFFFFF", IdelisRepository.computeContrastTextHex("1A237E"))
    }

    @Test
    fun `IdelisRepository auto-heals changed pole via autocomplete while preserving favorite stopId`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = IdelisDatabase.getInstance(context)
        val cacheManager = OpenDataCacheManager(context)
        val prefs = PreferencesManager(context)

        val prevIcons = IdelisRepository.globalLineIconsMap
        val prevRoutes = IdelisRepository.globalOfficialRoutesMap
        val prevStops = IdelisRepository.globalDownloadedStopsCache
        val prevServings = IdelisRepository.globalServingsByStopName

        try {
            val favoriteStop = GtfsStop(
                stopId = "FAV_BOSQUET_ID",
                stopName = "Pôle Bosquet",
                stopDesc = "Favori utilisateur",
                parentStation = "AncienPoleObsolete"
            )
            db.stopDao().insertStops(listOf(favoriteStop))

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    val reqBodyStr = req.body?.let { body ->
                        val buffer = okio.Buffer()
                        body.writeTo(buffer)
                        buffer.readUtf8()
                    } ?: ""
                    val urlStr = req.url.toString()

                    val responseBody: String
                    val code: Int
                    if (urlStr.contains("type=777") && reqBodyStr.contains("autocomplete")) {
                        code = 200
                        responseBody = """
                            <li class="ui-menu-item" data-value="Pôle Bosquet">
                                <a data-pole="NouveauPoleBosquet">Pôle Bosquet</a>
                            </li>
                        """.trimIndent()
                    } else if (urlStr.contains("type=777") && reqBodyStr.contains("AncienPoleObsolete")) {
                        code = 404
                        responseBody = "Not Found"
                    } else if (urlStr.contains("type=777") && reqBodyStr.contains("NouveauPoleBosquet")) {
                        code = 200
                        responseBody = """
                            <div class="ligne-group">
                                <img class="picto-ligne" src="/pictos/F.png" alt="Ligne F" />
                                <span class="text-black-50">Direction: PAU Gare de Pau - Arrêt: Pôle Bosquet</span>
                                <div class="p-1 text-center border rounded text-white bg-primary">5 min</div>
                            </div>
                        """.trimIndent()
                    } else {
                        code = 200
                        responseBody = "[]"
                    }

                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code == 200) "OK" else "Not Found")
                        .body(responseBody.toResponseBody(null))
                        .build()
                }
                .build()

            val failingSiri = IdelisSiriService(
                okHttpClient = OkHttpClient.Builder()
                    .addInterceptor { throw UnknownHostException("siri-offline") }
                    .build()
            )
            val repo = IdelisRepository(
                database = db,
                appContext = context,
                preferencesManager = prefs,
                siriService = failingSiri,
                exploreService = IdelisExploreService(client),
                gtfsDownloadService = GtfsDownloadService(db, client),
                webScraperService = IdelisWebScraperService(client)
            )

            val result = repo.getDeparturesForStop(
                stopId = "FAV_BOSQUET_ID",
                useSimulationMode = false,
                forceOffline = false,
                stopOverride = favoriteStop,
                forceRefresh = true
            )

            assertFalse("Auto-healing doit réussir à récupérer les départs", result.isError)
            assertEquals(1, result.departures.size)
            assertEquals("F", result.departures.first().lineRef)

            val healedStop = db.stopDao().getStopById("FAV_BOSQUET_ID")
            assertNotNull(healedStop)
            assertEquals("NouveauPoleBosquet", healedStop?.parentStation)
        } finally {
            cacheManager.clearAll()
            GtfsScheduleIndex.resetForTesting()
            IdelisRepository.globalLineIconsMap = prevIcons
            IdelisRepository.globalLineIconsUpdatedAtMs = 0L
            IdelisRepository.globalOfficialRoutesMap = prevRoutes
            IdelisRepository.globalOfficialRoutesUpdatedAtMs = 0L
            IdelisRepository.globalDownloadedStopsCache = prevStops
            IdelisRepository.globalDownloadedStopsUpdatedAtMs = 0L
            IdelisRepository.globalServingsByStopName = prevServings
        }
    }

    @Test
    fun `IdelisRepository downloadOpenDataOnLaunch returns failure when all OpenData endpoints fail`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = IdelisDatabase.getInstance(context)
        val cacheManager = OpenDataCacheManager(context)
        cacheManager.clearAll()

        val prevIcons = IdelisRepository.globalLineIconsMap
        val prevRoutes = IdelisRepository.globalOfficialRoutesMap
        val prevStops = IdelisRepository.globalDownloadedStopsCache
        val prevServings = IdelisRepository.globalServingsByStopName

        try {
            IdelisRepository.globalLineIconsMap = emptyMap()
            IdelisRepository.globalOfficialRoutesMap = emptyMap()
            IdelisRepository.globalDownloadedStopsCache = emptyList()
            IdelisRepository.globalServingsByStopName = emptyMap()

            val failingClient = OkHttpClient.Builder()
                .addInterceptor { throw UnknownHostException("offline-opendata") }
                .build()

            val repo = IdelisRepository(
                database = db,
                appContext = context,
                preferencesManager = PreferencesManager(context),
                siriService = IdelisSiriService(okHttpClient = failingClient),
                exploreService = IdelisExploreService(failingClient),
                gtfsDownloadService = GtfsDownloadService(db, failingClient),
                webScraperService = IdelisWebScraperService(failingClient)
            )

            val res = repo.downloadOpenDataOnLaunch(forceRefresh = true, skipGtfsZip = true)
            assertTrue("downloadOpenDataOnLaunch doit signaler l'échec quand OpenData est inaccessible", res.isFailure)
        } finally {
            cacheManager.clearAll()
            GtfsScheduleIndex.resetForTesting()
            IdelisRepository.globalLineIconsMap = prevIcons
            IdelisRepository.globalLineIconsUpdatedAtMs = 0L
            IdelisRepository.globalOfficialRoutesMap = prevRoutes
            IdelisRepository.globalOfficialRoutesUpdatedAtMs = 0L
            IdelisRepository.globalDownloadedStopsCache = prevStops
            IdelisRepository.globalDownloadedStopsUpdatedAtMs = 0L
            IdelisRepository.globalServingsByStopName = prevServings
        }
    }

    @Test
    fun `IdelisAppWidgetProvider preserves cached snapshot when network refresh fails`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)
        val prevWebUrl = prefs.getWebMonitoringUrl()
        val prevSiriUrl = prefs.getSiriSoapUrl()
        prefs.saveWebMonitoringUrl("http://127.0.0.1:1/fail")
        prefs.saveSiriSoapUrl("http://127.0.0.1:1/fail-siri")

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetId = shadowOf(appWidgetManager)
            .createWidgets(IdelisAppWidgetProvider::class.java, R.layout.widget_idelis_departures, 1)
            .first()
        pumpLooperUntil(timeoutMs = 500L) { false }

        val stopId = "WEB_Bosquet"
        val cachedDep = DepartureComparison(
            id = "cached_1",
            lineRef = "F",
            lineName = "Fébus",
            destinationName = "Gare de Pau",
            routeColorHex = "F18700",
            routeTextColorHex = "FFFFFF",
            aimedDepartureTimeFormatted = "10:15",
            expectedDepartureTimeFormatted = "10:15",
            delayMinutes = 0,
            status = DepartureStatus.ON_TIME,
            isRealTime = true,
            minutesUntilDeparture = 7L,
            comparisonLabel = "À l'heure"
        )

        try {
            IdelisWidgetPreferences.saveWidgetConfig(
                context,
                WidgetConfig(
                    appWidgetId = widgetId,
                    stopId = stopId,
                    stopName = "Pôle Bosquet",
                    isDynamicNearest = false
                )
            )
            IdelisWidgetPreferences.saveWidgetDeparturesSnapshot(
                context = context,
                appWidgetId = widgetId,
                stopId = stopId,
                departures = listOf(cachedDep)
            )

            // Appeler updateAppWidget avec URLs injoignables (échec réseau garanti)
            IdelisAppWidgetProvider.updateAppWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = widgetId,
                postSilentNotification = false
            )

            // Laisser la coroutine IO s'exécuter et terminer sur erreur réseau
            pumpLooperUntil(timeoutMs = 1_200L) { false }

            val preserved = IdelisWidgetPreferences.getWidgetDeparturesSnapshot(context, widgetId)
            assertEquals(stopId, preserved.stopId)
            assertEquals(
                "Le cache du widget ne doit pas être effacé après un échec réseau",
                1,
                preserved.departures.size
            )
            assertEquals("cached_1", preserved.departures.first().id)
        } finally {
            prefs.saveWebMonitoringUrl(prevWebUrl)
            prefs.saveSiriSoapUrl(prevSiriUrl)
            IdelisWidgetPreferences.deleteWidgetPrefs(context, intArrayOf(widgetId))
        }
    }

    @Test
    fun `TrafficInfoModal displays inline error banner alongside cached alerts and triggers retry`() {
        var retried = false
        val cachedAlerts = listOf(
            TrafficAlert(
                id = "a1",
                title = "Travaux Avenue Jean Mermoz",
                messageText = "Déviation en cours.",
                affectedLines = listOf("F"),
                severity = "WARNING",
                publishedDate = "Aujourd'hui"
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                TrafficInfoModal(
                    alerts = cachedAlerts,
                    activePeriod = IdelisPeriod.ORANGE,
                    isLoading = false,
                    errorMessage = "Pas de connexion Internet.",
                    onRefresh = { retried = true },
                    onDismissRequest = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("traffic_error_banner").assertExists()
        composeTestRule.onNodeWithText("Travaux Avenue Jean Mermoz").assertExists()
        composeTestRule.onNodeWithTag("retry_traffic_button").performClick()
        assertTrue(retried)
    }

    @Test
    fun `RouteStopsModal displays error state and triggers retry callback`() {
        var retried = false
        val errorDetail = RouteLineDetail(
            lineCode = "T5",
            lineLabel = "Ligne T5",
            routeLongName = "Lons - Bosquet",
            colorHex = "0085CA",
            textColorHex = "FFFFFF",
            availableDirections = listOf("Pôle Bosquet"),
            selectedDirection = "Pôle Bosquet",
            terminusName = "Pôle Bosquet",
            startName = "",
            totalStopsCount = 0,
            stops = emptyList(),
            isLoadingStops = false,
            errorMessage = "Impossible de charger les arrêts de la ligne."
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                RouteStopsModal(
                    routeDetail = errorDetail,
                    currentStopName = "Pôle Bosquet",
                    onRetryLoadStops = { retried = true },
                    onDismissRequest = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("route_stops_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retry_route_stops_button").performClick()
        assertTrue(retried)
    }
}
