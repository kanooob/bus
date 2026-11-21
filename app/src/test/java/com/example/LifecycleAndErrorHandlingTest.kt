package com.example

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IdelisDatabase
import com.example.data.local.OpenDataCacheManager
import com.example.data.local.PreferencesManager
import com.example.data.model.AppUpdateCheckFrequency
import com.example.data.model.TrafficRefreshFrequency
import com.example.data.remote.GtfsDownloadService
import com.example.data.remote.GtfsScheduleIndex
import com.example.data.remote.HttpStatusException
import com.example.data.remote.IdelisExploreService
import com.example.data.remote.IdelisSiriService
import com.example.data.remote.IdelisWebScraperService
import com.example.data.remote.InvalidServerResponseException
import com.example.data.remote.NetworkErrorHelper
import com.example.data.repository.IdelisRepository
import com.example.ui.viewmodel.TransitBoardViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LifecycleAndErrorHandlingTest {

    private fun fastSiriFailureService(): IdelisSiriService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                throw UnknownHostException("offline-test")
            }
            .build()
        return IdelisSiriService(okHttpClient = client)
    }

    private fun fastMockExploreClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody(null))
                    .build()
            }
            .build()
    }

    private fun sampleStopMonitoringHtml(minutes: Int = 3): String = """
        <div class="ligne-group">
            <div class="row"><div class="col-12"><img class="picto-ligne" src="/fileadmin/user_upload/pictos/F.png" alt="Ligne F" /></div></div>
            <div class="row"><div class="col-12"><span class="text-black-50">Direction: PAU Gare de Pau - Arrêt: Pôle Bosquet</span></div></div>
            <div class="row"><div class="col-3 pr-1"><div class="p-1 text-center border rounded text-white bg-primary">$minutes min</div></div></div>
        </div>
    """.trimIndent()

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
    fun `NetworkErrorHelper formats network HTTP and server errors in clear French`() {
        assertTrue(
            NetworkErrorHelper.formatError(UnknownHostException("www.idelis.fr"))
                .contains("connexion Internet", ignoreCase = true)
        )
        assertTrue(
            NetworkErrorHelper.formatError(SocketTimeoutException("timeout"))
                .contains("Délai d'attente", ignoreCase = true)
        )
        assertTrue(
            NetworkErrorHelper.formatError(ConnectException("refused"))
                .contains("Impossible de joindre", ignoreCase = true)
        )
        assertTrue(
            NetworkErrorHelper.formatError(EOFException("unexpected end of stream"))
                .contains("Connexion interrompue", ignoreCase = true)
        )
        assertTrue(
            NetworkErrorHelper.formatError(HttpStatusException(503, "Service Unavailable"))
                .contains("HTTP 503")
        )
        assertTrue(
            NetworkErrorHelper.formatError(HttpStatusException(404, "Not Found"))
                .contains("HTTP 404")
        )
        assertEquals(
            "Réponse incomplète.",
            NetworkErrorHelper.formatError(InvalidServerResponseException("Réponse incomplète."))
        )
    }

    @Test
    fun `IdelisWebScraperService fails cleanly on ajax only blank or full HTML pages while keeping valid empty alerts`() = runBlocking {
        var currentBody = "ajax only"
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(currentBody.toResponseBody(null))
                    .build()
            }
            .build()

        val scraper = IdelisWebScraperService(client = mockClient)

        // 1. "ajax only" -> failure(InvalidServerResponseException)
        val ajaxOnlyResult = scraper.getStopMonitoring("Bosquet")
        assertTrue(ajaxOnlyResult.isFailure)
        assertTrue(ajaxOnlyResult.exceptionOrNull() is InvalidServerResponseException)

        // 2. Corps vide -> failure(InvalidServerResponseException)
        currentBody = "   "
        val blankResult = scraper.getStopMonitoring("Bosquet")
        assertTrue(blankResult.isFailure)
        assertTrue(blankResult.exceptionOrNull() is InvalidServerResponseException)

        // 3. Page HTML complète (redirection/maintenance/erreur) -> failure(InvalidServerResponseException)
        currentBody = "<!doctype html><html><head><title>Maintenance</title></head><body><h1>Maintenance</h1></body></html>"
        val fullHtmlResult = scraper.getStopMonitoring("Bosquet")
        assertTrue(fullHtmlResult.isFailure)
        assertTrue(fullHtmlResult.exceptionOrNull() is InvalidServerResponseException)

        // 4. Autocomplétion recevant "ajax only" ou page HTML complète -> failure(InvalidServerResponseException)
        currentBody = "ajax only"
        val autoAjaxResult = scraper.autocompleteStops("Bosquet")
        assertTrue(autoAjaxResult.isFailure)
        assertTrue(autoAjaxResult.exceptionOrNull() is InvalidServerResponseException)

        currentBody = "<!doctype html><html><body>Erreur</body></html>"
        val autoHtmlResult = scraper.autocompleteStops("Bosquet")
        assertTrue(autoHtmlResult.isFailure)
        assertTrue(autoHtmlResult.exceptionOrNull() is InvalidServerResponseException)

        // 5. Fragment HTML valide avec .alert ("Aucun passage") -> success avec 0 départ (et pas d'erreur)
        currentBody = """<div class="alert alert-info">Aucun passage prévu pour le moment</div>"""
        val validEmptyResult = scraper.getStopMonitoring("Bosquet")
        assertTrue(validEmptyResult.isSuccess)
        assertTrue(validEmptyResult.getOrNull()?.departures.isNullOrEmpty())
        assertFalse(validEmptyResult.getOrNull()?.alertMessage.isNullOrBlank())
    }

    @Test
    fun `initial failure with no cached departures surfaces error state and stops loading`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = PreferencesManager(context).apply {
            setUsageStatsEnabled(false)
            saveAppUpdateCheckFrequency(AppUpdateCheckFrequency.NEVER)
            saveTrafficRefreshFrequency(TrafficRefreshFrequency.NEVER)
            saveLastGtfsSyncTimestamp(System.currentTimeMillis())
        }
        val db = IdelisDatabase.getInstance(context)
        val cacheManager = OpenDataCacheManager(context)

        val prevIcons = IdelisRepository.globalLineIconsMap
        val prevRoutes = IdelisRepository.globalOfficialRoutesMap
        val prevStops = IdelisRepository.globalDownloadedStopsCache
        val prevServings = IdelisRepository.globalServingsByStopName

        var viewModel: TransitBoardViewModel? = null
        try {
            val failingWebClient = OkHttpClient.Builder()
                .addInterceptor { _ ->
                    throw UnknownHostException("www.idelis.fr")
                }
                .build()

            val mockExploreClient = fastMockExploreClient()
            val repo = IdelisRepository(
                database = db,
                appContext = context,
                preferencesManager = prefs,
                siriService = fastSiriFailureService(),
                exploreService = IdelisExploreService(mockExploreClient),
                gtfsDownloadService = GtfsDownloadService(db, mockExploreClient),
                webScraperService = IdelisWebScraperService(client = failingWebClient)
            )

            viewModel = TransitBoardViewModel(repo, prefs)
            pumpLooperUntil {
                !viewModel.uiState.value.isLoading && viewModel.uiState.value.departuresErrorMessage != null
            }

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.departures.isEmpty())
            assertNotNull("Un message d'erreur doit être affiché", state.departuresErrorMessage)
            assertTrue(
                "Le message d'erreur doit expliquer le problème réseau",
                state.departuresErrorMessage!!.contains("connexion", ignoreCase = true)
            )
        } finally {
            viewModel?.onClearedForTesting()
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
    fun `backgrounding and returning to foreground refreshes departures and preserves visible buses during transient errors`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = PreferencesManager(context).apply {
            setUsageStatsEnabled(false)
            saveAppUpdateCheckFrequency(AppUpdateCheckFrequency.NEVER)
            saveTrafficRefreshFrequency(TrafficRefreshFrequency.NEVER)
            saveLastGtfsSyncTimestamp(System.currentTimeMillis())
        }
        val db = IdelisDatabase.getInstance(context)
        val cacheManager = OpenDataCacheManager(context)

        val prevIcons = IdelisRepository.globalLineIconsMap
        val prevRoutes = IdelisRepository.globalOfficialRoutesMap
        val prevStops = IdelisRepository.globalDownloadedStopsCache
        val prevServings = IdelisRepository.globalServingsByStopName

        var viewModel: TransitBoardViewModel? = null
        try {
            // Modes: 0 = 200 OK (3 min), 1 = HTTP 503, 2 = 200 OK ("ajax only"), 3 = 200 OK (7 min)
            val responseMode = AtomicInteger(0)
            val stopMonitoringCalls = AtomicInteger(0)

            val mockWebClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val body = chain.request().body as? FormBody
                    val requete = (0 until (body?.size ?: 0)).firstOrNull { body?.name(it) == "requete" }
                        ?.let { body?.value(it) }
                    if (requete == "stop_monitoring") {
                        stopMonitoringCalls.incrementAndGet()
                        when (responseMode.get()) {
                            1 -> return@addInterceptor Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(503)
                                .message("Service Unavailable")
                                .body("Service Unavailable".toResponseBody(null))
                                .build()
                            2 -> return@addInterceptor Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body("ajax only".toResponseBody(null))
                                .build()
                        }
                    }
                    val html = when (requete) {
                        "stop_monitoring" -> if (responseMode.get() == 3) {
                            sampleStopMonitoringHtml(minutes = 7)
                        } else {
                            sampleStopMonitoringHtml(minutes = 3)
                        }
                        else -> ""
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(html.toResponseBody(null))
                        .build()
                }
                .build()

            val mockExploreClient = fastMockExploreClient()
            val repo = IdelisRepository(
                database = db,
                appContext = context,
                preferencesManager = prefs,
                siriService = fastSiriFailureService(),
                exploreService = IdelisExploreService(mockExploreClient),
                gtfsDownloadService = GtfsDownloadService(db, mockExploreClient),
                webScraperService = IdelisWebScraperService(client = mockWebClient)
            )

            viewModel = TransitBoardViewModel(repo, prefs)

            // 1. Chargement initial réussi -> des départs sont visibles sans erreur
            pumpLooperUntil {
                viewModel.uiState.value.departures.isNotEmpty() && !viewModel.uiState.value.isLoading
            }
            assertEquals(1, viewModel.uiState.value.departures.size)
            assertNull(viewModel.uiState.value.departuresErrorMessage)
            assertEquals(3L, viewModel.uiState.value.departures.first().minutesUntilDeparture)

            // 2. Passage en arrière-plan puis erreur HTTP 503 au rafraîchissement manuel ->
            //    les bus déjà affichés doivent rester visibles et une bannière d'erreur doit apparaître
            responseMode.set(1)
            val beforeManualFailCalls = stopMonitoringCalls.get()
            viewModel.refreshDepartures(isManual = true, forceRefresh = true)
            pumpLooperUntil {
                stopMonitoringCalls.get() > beforeManualFailCalls &&
                    !viewModel.uiState.value.isManualRefreshing &&
                    viewModel.uiState.value.departuresErrorMessage != null
            }
            assertTrue(
                "Les prochains bus déjà visibles ne doivent jamais disparaître lors d'une erreur 503",
                viewModel.uiState.value.departures.isNotEmpty()
            )
            assertTrue(viewModel.uiState.value.departuresErrorMessage!!.contains("HTTP 503"))

            // 3. Deuxième échec avec "ajax only" -> toujours pas d'effacement des bus visibles
            responseMode.set(2)
            val beforeAjaxFailCalls = stopMonitoringCalls.get()
            viewModel.refreshDepartures(isManual = true, forceRefresh = true)
            pumpLooperUntil {
                stopMonitoringCalls.get() > beforeAjaxFailCalls &&
                    !viewModel.uiState.value.isManualRefreshing &&
                    viewModel.uiState.value.departuresErrorMessage?.contains("incomplète", ignoreCase = true) == true
            }
            assertTrue(
                "Les prochains bus doivent rester visibles si le serveur répond 'ajax only'",
                viewModel.uiState.value.departures.isNotEmpty()
            )

            // 4. L'utilisateur quitte l'app (onStop -> onAppBackgrounded) puis revient (onStart -> onAppForegrounded)
            //    et le réseau remarche (7 min) -> rafraîchissement automatique au retour et effacement de l'erreur
            viewModel.onAppBackgrounded()
            responseMode.set(3)
            val beforeForegroundCalls = stopMonitoringCalls.get()
            viewModel.onAppForegrounded()

            pumpLooperUntil {
                stopMonitoringCalls.get() > beforeForegroundCalls &&
                    viewModel.uiState.value.departuresErrorMessage == null &&
                    viewModel.uiState.value.departures.firstOrNull()?.minutesUntilDeparture == 7L
            }

            assertNull(viewModel.uiState.value.departuresErrorMessage)
            assertEquals(7L, viewModel.uiState.value.departures.first().minutesUntilDeparture)
        } finally {
            viewModel?.onClearedForTesting()
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
    fun `web stop search failure sets webSearchErrorMessage and clears cleanly on reset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = PreferencesManager(context).apply {
            setUsageStatsEnabled(false)
            saveAppUpdateCheckFrequency(AppUpdateCheckFrequency.NEVER)
            saveTrafficRefreshFrequency(TrafficRefreshFrequency.NEVER)
            saveLastGtfsSyncTimestamp(System.currentTimeMillis())
        }
        val db = IdelisDatabase.getInstance(context)
        val cacheManager = OpenDataCacheManager(context)

        val prevIcons = IdelisRepository.globalLineIconsMap
        val prevRoutes = IdelisRepository.globalOfficialRoutesMap
        val prevStops = IdelisRepository.globalDownloadedStopsCache
        val prevServings = IdelisRepository.globalServingsByStopName

        var viewModel: TransitBoardViewModel? = null
        try {
            val mockWebClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val body = chain.request().body as? FormBody
                    val requete = (0 until (body?.size ?: 0)).firstOrNull { body?.name(it) == "requete" }
                        ?.let { body?.value(it) }
                    if (requete == "autocomplete") {
                        return@addInterceptor Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(502)
                            .message("Bad Gateway")
                            .body("Bad Gateway".toResponseBody(null))
                            .build()
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(sampleStopMonitoringHtml(4).toResponseBody(null))
                        .build()
                }
                .build()

            val mockExploreClient = fastMockExploreClient()
            val repo = IdelisRepository(
                database = db,
                appContext = context,
                preferencesManager = prefs,
                siriService = fastSiriFailureService(),
                exploreService = IdelisExploreService(mockExploreClient),
                gtfsDownloadService = GtfsDownloadService(db, mockExploreClient),
                webScraperService = IdelisWebScraperService(client = mockWebClient)
            )

            viewModel = TransitBoardViewModel(repo, prefs)
            pumpLooperUntil { viewModel.uiState.value.departures.isNotEmpty() }

            viewModel.searchWebAutocomplete("Bosquet")
            pumpLooperUntil {
                !viewModel.uiState.value.isWebSearching && viewModel.uiState.value.webSearchErrorMessage != null
            }

            assertFalse(viewModel.uiState.value.isWebSearching)
            assertNotNull(viewModel.uiState.value.webSearchErrorMessage)
            assertTrue(viewModel.uiState.value.webSearchErrorMessage!!.contains("HTTP 502"))

            viewModel.clearWebSuggestions()
            assertNull(viewModel.uiState.value.webSearchErrorMessage)
            assertTrue(viewModel.uiState.value.webSuggestions.isEmpty())
        } finally {
            viewModel?.onClearedForTesting()
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
}
