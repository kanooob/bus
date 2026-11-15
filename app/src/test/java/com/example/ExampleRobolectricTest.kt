package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.SiriXmlParser
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Galaxie Bus", appName)
    }

    @Test
    fun `parse SIRI GetStopMonitoring XML response successfully`() {
        val sampleXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:siri="http://www.siri.org.uk/siri">
                <soapenv:Body>
                    <siri:GetStopMonitoringResponse>
                        <siri:Answer>
                            <siri:StopMonitoringDelivery>
                                <siri:MonitoredStopVisit>
                                    <siri:MonitoredVehicleJourney>
                                        <siri:LineRef>F</siri:LineRef>
                                        <siri:PublishedLineName>Fébus</siri:PublishedLineName>
                                        <siri:DestinationName>Gare de Pau</siri:DestinationName>
                                        <siri:MonitoredCall>
                                            <siri:StopPointRef>ID_BOSQUET</siri:StopPointRef>
                                            <siri:AimedDepartureTime>2026-11-06T17:16:00+01:00</siri:AimedDepartureTime>
                                            <siri:ExpectedDepartureTime>2026-11-06T17:17:00+01:00</siri:ExpectedDepartureTime>
                                            <siri:DepartureStatus>delayed</siri:DepartureStatus>
                                        </siri:MonitoredCall>
                                    </siri:MonitoredVehicleJourney>
                                </siri:MonitoredStopVisit>
                            </siri:StopMonitoringDelivery>
                        </siri:Answer>
                    </siri:GetStopMonitoringResponse>
                </soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()

        val parser = SiriXmlParser()
        val visits = parser.parseStopMonitoringResponse(sampleXml)

        assertEquals(1, visits.size)
        val visit = visits[0]
        assertEquals("F", visit.lineRef)
        assertEquals("Fébus", visit.publishedLineName)
        assertEquals("Gare de Pau", visit.destinationName)
        assertEquals("2026-11-06T17:16:00+01:00", visit.aimedDepartureTime)
        assertEquals("2026-11-06T17:17:00+01:00", visit.expectedDepartureTime)
        assertEquals("delayed", visit.departureStatus)
    }

    @Test
    fun `stop selection changes bus lines and official directions according to stop servings`() {
        val stop218 = com.example.data.local.GtfsDatabaseSeeder.INITIAL_STOPS.first { it.stopId == "ID_218_RI" }
        assertEquals("218ème RI", stop218.stopName)

        val servings218 = com.example.data.local.GtfsDatabaseSeeder.INITIAL_SERVINGS.filter { it.stopId == "ID_218_RI" }
        // Seule la ligne 7 avec ses directions officielles doit desservir cet arrêt
        assertTrue(servings218.all { it.lineRef == "7" })
        val directions = servings218.map { it.direction }.toSet()
        assertTrue(directions.contains("ARTIGUELOUTAN Salle des Fêtes"))
        assertTrue(directions.contains("LESCAR Soleil"))

        val stopBremond = com.example.data.local.GtfsDatabaseSeeder.INITIAL_STOPS.first { it.stopId == "ID_ABBE_BREMOND" }
        val servingsBremond = com.example.data.local.GtfsDatabaseSeeder.INITIAL_SERVINGS.filter { it.stopId == "ID_ABBE_BREMOND" }
        val bremondLines = servingsBremond.map { it.lineRef }.toSet()
        assertEquals(setOf("6", "T4"), bremondLines)
    }

    @Test
    fun `platforms are canonicalized into single hub stops`() {
        assertEquals("Pôle Bosquet", com.example.data.repository.IdelisRepository.getCanonicalStopName("PAU Bosquet Quai C"))
        assertEquals("Pôle Bosquet", com.example.data.repository.IdelisRepository.getCanonicalStopName("PAU Bosquet Quai H"))
        assertEquals("Pôle Bosquet", com.example.data.repository.IdelisRepository.getCanonicalStopName("Pl. d'Espagne - Bosquet Quai A"))
        assertEquals("Gare de Pau", com.example.data.repository.IdelisRepository.getCanonicalStopName("PAU Gare de Pau Quai B"))
        assertEquals("Place de Verdun", com.example.data.repository.IdelisRepository.getCanonicalStopName("Verdun Camou Quai 1"))
    }

    @Test
    fun `gtfs zip report model holds correct structure`() {
        val report = com.example.data.model.GtfsSyncReport(
            stopsCount = 415,
            routesCount = 22,
            tripsCount = 1200,
            stopTimesCount = 4500,
            activePeriod = com.example.data.model.IdelisPeriod.VERTE,
            detectedWorks = listOf("Travaux Boulevard Tourasse"),
            downloadedFiles = listOf("stops.txt", "routes.txt", "trips.txt", "stop_times.txt", "calendar.txt"),
            success = true
        )

        assertEquals(415, report.stopsCount)
        assertEquals(22, report.routesCount)
        assertEquals(com.example.data.model.IdelisPeriod.VERTE, report.activePeriod)
        assertEquals(1, report.detectedWorks.size)
        assertTrue(report.downloadedFiles.contains("stops.txt"))
        assertTrue(report.success)
    }

    @Test
    fun `idelis periods match exact user specification`() {
        assertEquals("Orange", com.example.data.model.IdelisPeriod.ORANGE.colorName)
        assertEquals("Lundi au vendredi (scolaire)", com.example.data.model.IdelisPeriod.ORANGE.description)

        assertEquals("Bleue", com.example.data.model.IdelisPeriod.BLEUE.colorName)
        assertEquals("Lundi au vendredi (non scolaire)", com.example.data.model.IdelisPeriod.BLEUE.description)

        assertEquals("Grise", com.example.data.model.IdelisPeriod.GRISE.colorName)
        assertEquals("Samedi", com.example.data.model.IdelisPeriod.GRISE.description)

        assertEquals("Verte", com.example.data.model.IdelisPeriod.VERTE.colorName)
        assertEquals("Dimanche et jours fériés", com.example.data.model.IdelisPeriod.VERTE.description)
    }

    @Test
    fun `multiple widgets store independent bus stops`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val widget1Id = 101
        val widget2Id = 102
        val widget3Id = 103

        // Widget 1 configure Pôle Bosquet
        com.example.ui.widget.IdelisWidgetPreferences.saveWidgetStop(
            context = context,
            appWidgetId = widget1Id,
            stopId = "ID_BOSQUET",
            stopName = "Pôle Bosquet"
        )

        // Widget 2 configure Gare de Pau
        com.example.ui.widget.IdelisWidgetPreferences.saveWidgetStop(
            context = context,
            appWidgetId = widget2Id,
            stopId = "ID_GARE",
            stopName = "Gare de Pau"
        )

        // Widget 3 configure Université
        com.example.ui.widget.IdelisWidgetPreferences.saveWidgetStop(
            context = context,
            appWidgetId = widget3Id,
            stopId = "ID_UNIV",
            stopName = "Université"
        )

        // Vérifier l'isolation totale entre widgets
        assertEquals("ID_BOSQUET", com.example.ui.widget.IdelisWidgetPreferences.getWidgetStopId(context, widget1Id))
        assertEquals("Pôle Bosquet", com.example.ui.widget.IdelisWidgetPreferences.getWidgetStopName(context, widget1Id))

        assertEquals("ID_GARE", com.example.ui.widget.IdelisWidgetPreferences.getWidgetStopId(context, widget2Id))
        assertEquals("Gare de Pau", com.example.ui.widget.IdelisWidgetPreferences.getWidgetStopName(context, widget2Id))

        assertEquals("ID_UNIV", com.example.ui.widget.IdelisWidgetPreferences.getWidgetStopId(context, widget3Id))
        assertEquals("Université", com.example.ui.widget.IdelisWidgetPreferences.getWidgetStopName(context, widget3Id))

        // Suppression d'un widget
        com.example.ui.widget.IdelisWidgetPreferences.deleteWidgetPrefs(context, intArrayOf(widget2Id))
        assertEquals("ID_BOSQUET", com.example.ui.widget.IdelisWidgetPreferences.getWidgetStopId(context, widget2Id)) // revient au fallback
        assertEquals("ID_UNIV", com.example.ui.widget.IdelisWidgetPreferences.getWidgetStopId(context, widget3Id)) // non affecté
    }

    @Test
    fun `widget helper renders badge bitmap and formats waiting times`() {
        val badge = com.example.ui.widget.IdelisWidgetHelper.createLineBadgeBitmap("F", "F18700", "121212")
        org.junit.Assert.assertNotNull(badge)
        assertEquals(96, badge.width)
        assertEquals(60, badge.height)

        val depApproaching = com.example.data.model.DepartureComparison(
            id = "test_1",
            lineRef = "F",
            lineName = "Fébus",
            destinationName = "Gare de Pau",
            routeColorHex = "F18700",
            routeTextColorHex = "121212",
            aimedDepartureTimeFormatted = "14:00",
            expectedDepartureTimeFormatted = "14:00",
            delayMinutes = 0,
            status = com.example.data.model.DepartureStatus.ON_TIME,
            isRealTime = true,
            minutesUntilDeparture = 0L,
            comparisonLabel = "14:00"
        )
        assertEquals("À l'approche", com.example.ui.widget.IdelisWidgetHelper.formatWaitingTime(depApproaching))

        val dep5Min = depApproaching.copy(minutesUntilDeparture = 5L)
        assertEquals("Dans 5 min", com.example.ui.widget.IdelisWidgetHelper.formatWaitingTime(dep5Min))
    }

    @Test
    fun `widget RemoteViews inflates cleanly without InflateException`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val views = android.widget.RemoteViews(context.packageName, R.layout.widget_idelis_departures)
        val inflated = views.apply(context, null)
        org.junit.Assert.assertNotNull(inflated)
    }

    @Test
    fun `bus line detail shows all stops in order with terminus at top and debut at bottom`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.example.data.repository.RouteStopsRepository(context)

        val departure = com.example.data.model.DepartureComparison(
            id = "test_f",
            lineRef = "F",
            lineName = "Fébus",
            destinationName = "PAU Hôpital F. Mitterrand",
            routeColorHex = "F18700",
            routeTextColorHex = "121212",
            aimedDepartureTimeFormatted = "14:30",
            expectedDepartureTimeFormatted = "14:30",
            delayMinutes = 0,
            status = com.example.data.model.DepartureStatus.ON_TIME,
            isRealTime = true,
            minutesUntilDeparture = 5L,
            comparisonLabel = "14:30"
        )

        // 1. Ordre par défaut selon la demande : Terminus en haut, Début en bas
        val detail = repo.getRouteDetail(
            departure = departure,
            currentStopName = "Pôle Bosquet",
            isTerminusAtTop = true
        )

        org.junit.Assert.assertNotNull(detail)
        assertTrue(detail.stops.isNotEmpty())
        assertEquals(true, detail.isTerminusAtTop)

        // En haut (premier élément de la liste) : le Terminus !
        val topStop = detail.stops.first()
        assertTrue("Le premier arrêt en haut doit être le terminus", topStop.isTerminus)
        assertTrue(topStop.stopName.contains("Hôpital", ignoreCase = true))

        // En bas (dernier élément de la liste) : le Début !
        val bottomStop = detail.stops.last()
        assertTrue("Le dernier arrêt en bas doit être le début", bottomStop.isStart)
        assertTrue(bottomStop.stopName.contains("Gare de Pau", ignoreCase = true))

        // Vérification de la présence de l'arrêt actuel
        val currentStopFound = detail.stops.any { it.isCurrentStop && it.stopName.contains("Bosquet", ignoreCase = true) }
        assertTrue("L'arrêt actuel de l'utilisateur doit être détecté", currentStopFound)

        // 2. Vérification de l'inversion d'ordre : Début en haut, Terminus en bas
        val invertedDetail = repo.getRouteDetail(
            departure = departure,
            currentStopName = "Pôle Bosquet",
            isTerminusAtTop = false
        )
        val invTop = invertedDetail.stops.first()
        val invBottom = invertedDetail.stops.last()
        assertTrue("Quand inversé, le début est en haut", invTop.isStart)
        assertTrue("Quand inversé, le terminus est en bas", invBottom.isTerminus)
    }

    @Test
    fun `web departures compare with theoretical to calculate exact delays and advances`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.IdelisDatabase.getInstance(context)
        val repo = com.example.data.repository.IdelisRepository(db, context)

        val parisTz = java.util.TimeZone.getTimeZone("Europe/Paris")
        val nowCal = java.util.Calendar.getInstance(parisTz)
        val h = nowCal.get(java.util.Calendar.HOUR_OF_DAY)
        val m = nowCal.get(java.util.Calendar.MINUTE)

        // Horaire théorique exactement à l'heure actuelle
        val theoTimeStr = String.format(java.util.Locale.FRANCE, "%02d:%02d:00", h, m)
        val theoretical = listOf(
            com.example.data.model.GtfsTheoreticalDeparture(
                stopId = "ID_BOSQUET",
                stopName = "Pôle Bosquet",
                routeId = "F",
                routeShortName = "F",
                routeLongName = "Fébus",
                routeColor = "FFC107",
                routeTextColor = "121212",
                tripHeadsign = "PAU Gare de Pau",
                departureTime = theoTimeStr
            )
        )

        // 1. Bus en retard de 2 minutes (arrive dans 2 min alors qu'il était prévu maintenant)
        val webDelayed = listOf(
            com.example.data.model.IdelisWebDeparture(
                lineRef = "F",
                lineName = "Fébus",
                linePictoUrl = null,
                direction = "PAU Gare de Pau",
                stopQuai = "Quai A",
                timeText = "2 min",
                isRealTime = true,
                waitMinutes = 2
            )
        )
        val compDelayed = repo.compareWebDeparturesWithTheoretical(webDelayed, theoretical)
        assertEquals(1, compDelayed.size)
        assertEquals(2, compDelayed[0].delayMinutes)
        assertEquals(com.example.data.model.DepartureStatus.DELAYED, compDelayed[0].status)
        assertTrue(compDelayed[0].comparisonLabel.contains("+2 min"))

        // 2. Bus à l'heure (0 min)
        val webOnTime = listOf(
            com.example.data.model.IdelisWebDeparture(
                lineRef = "F",
                lineName = "Fébus",
                linePictoUrl = null,
                direction = "PAU Gare de Pau",
                stopQuai = "Quai A",
                timeText = "Imminent",
                isRealTime = true,
                waitMinutes = 0
            )
        )
        val compOnTime = repo.compareWebDeparturesWithTheoretical(webOnTime, theoretical)
        assertEquals(1, compOnTime.size)
        assertEquals(0, compOnTime[0].delayMinutes)
        assertEquals(com.example.data.model.DepartureStatus.ON_TIME, compOnTime[0].status)
        assertTrue(compOnTime[0].comparisonLabel.contains("À l'heure"))

        // 3. Filtrage du théorique : les passages avec isRealTime = false (horaires futurs théoriques) ne doivent PAS être affichés
        val webMixed = listOf(
            com.example.data.model.IdelisWebDeparture(
                lineRef = "F",
                lineName = "Fébus",
                linePictoUrl = null,
                direction = "PAU Gare de Pau",
                stopQuai = "Quai A",
                timeText = "3 min",
                isRealTime = true,
                waitMinutes = 3
            ),
            com.example.data.model.IdelisWebDeparture(
                lineRef = "F",
                lineName = "Fébus",
                linePictoUrl = null,
                direction = "PAU Gare de Pau",
                stopQuai = "Quai A",
                timeText = "16:45",
                isRealTime = false, // théorique issu de la grille
                waitMinutes = 45
            )
        )
        val compMixed = repo.compareWebDeparturesWithTheoretical(webMixed, theoretical)
        // Seul le bus temps réel doit subsister
        assertEquals(1, compMixed.size)
        assertTrue(compMixed[0].isRealTime)
        assertEquals("3 min", compMixed[0].id.substringAfterLast("_"))
    }

    @Test
    fun `requested web stops can be saved and favorited`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.IdelisDatabase.getInstance(context)
        val repo = com.example.data.repository.IdelisRepository(db, context)
        val prefs = com.example.data.local.PreferencesManager(context)

        val webStop = com.example.data.model.IdelisWebStop(
            pole = "Gare de Pau",
            name = "Gare de Pau (Quai A)"
        )

        // Sauvegarder l'arrêt reçu par requête
        val saved = repo.saveWebStop(webStop)
        assertEquals("WEB_Gare_de_Pau", saved.stopId)
        assertEquals("Gare de Pau (Quai A)", saved.stopName)
        assertEquals("Gare de Pau", saved.parentStation)

        // Vérifier l'ajout en favoris
        val initialFavs = prefs.getFavoriteStopIds().toMutableSet()
        initialFavs.add(saved.stopId)
        prefs.saveFavoriteStopIds(initialFavs)
        assertTrue(prefs.getFavoriteStopIds().contains("WEB_Gare_de_Pau"))

        // Vérifier la purge des arrêts théoriques (conserve les favoris et arrêts WEB_)
        repo.purgeTheoreticalStops(prefs.getFavoriteStopIds())
        val allStops = db.stopDao().getAllStopsList()
        assertTrue(allStops.any { it.stopId == "WEB_Gare_de_Pau" })
        assertTrue("Aucun arrêt théorique ID_ ou GRP_ ne doit subsister sans être favori",
            allStops.all { it.stopId.startsWith("WEB_") })
    }

    @Test
    fun `settings hierarchy defaults and json backup export import round-trip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Nettoyer les prefs pour tester les valeurs par défaut
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = com.example.data.local.PreferencesManager(context)

        // Vérifier toutes les valeurs par défaut demandées
        assertEquals(com.example.data.model.ThemeMode.SYSTEM, prefs.getThemeMode())
        assertEquals(com.example.data.model.PrimaryColorMode.APP_DEFAULT, prefs.getPrimaryColorMode())
        assertEquals(com.example.data.model.InAppRefreshFrequency.EVERY_1_MIN, prefs.getInAppRefreshFrequency())
        assertEquals(com.example.data.model.WidgetRefreshFrequency.EVERY_15_MIN, prefs.getWidgetRefreshFrequency())
        assertEquals(com.example.data.model.WidgetLocationFrequency.EVERY_15_MIN, prefs.getWidgetLocationFrequency())
        assertEquals(com.example.data.model.GtfsSyncFrequency.DAILY_ON_OPEN, prefs.getGtfsSyncFrequency())
        assertEquals(com.example.data.model.TrafficRefreshFrequency.EVERY_2_HOURS, prefs.getTrafficRefreshFrequency())
        assertFalse(prefs.isTrafficNotificationsEnabled())
        assertEquals(com.example.data.model.AppUpdateCheckFrequency.DAILY_ON_OPEN, prefs.getAppUpdateCheckFrequency())
        assertEquals(com.example.data.model.UpdateNetworkMode.WIFI_AND_CELLULAR, prefs.getUpdateNetworkMode())

        // Modifier tous les paramètres et favoris
        prefs.saveFavoriteStopIds(setOf("WEB_Bosquet", "WEB_Universite"))
        prefs.saveThemeMode(com.example.data.model.ThemeMode.DARK)
        prefs.savePrimaryColorMode(com.example.data.model.PrimaryColorMode.DYNAMIC)
        prefs.saveInAppRefreshFrequency(com.example.data.model.InAppRefreshFrequency.EVERY_2_MIN)
        prefs.saveWidgetRefreshFrequency(com.example.data.model.WidgetRefreshFrequency.MANUAL_ONLY)
        prefs.saveWidgetLocationFrequency(com.example.data.model.WidgetLocationFrequency.EVERY_10_MIN)
        prefs.saveGtfsSyncFrequency(com.example.data.model.GtfsSyncFrequency.TWICE_A_DAY)
        prefs.saveTrafficRefreshFrequency(com.example.data.model.TrafficRefreshFrequency.EVERY_30_MIN)
        prefs.setTrafficNotificationsEnabled(true)
        prefs.saveAppUpdateCheckFrequency(com.example.data.model.AppUpdateCheckFrequency.WEEKLY)
        prefs.saveUpdateNetworkMode(com.example.data.model.UpdateNetworkMode.WIFI_ONLY)

        // Exporter en JSON
        val json = prefs.exportBackupJson()

        // Réinitialiser les prefs
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(com.example.data.model.ThemeMode.SYSTEM, prefs.getThemeMode())

        // Réimporter depuis JSON
        val importRes = prefs.importBackupJson(json)
        assertTrue(importRes.isSuccess)
        assertEquals(2, importRes.getOrNull())
        assertEquals(setOf("WEB_Bosquet", "WEB_Universite"), prefs.getFavoriteStopIds())
        assertEquals(com.example.data.model.ThemeMode.DARK, prefs.getThemeMode())
        assertEquals(com.example.data.model.PrimaryColorMode.DYNAMIC, prefs.getPrimaryColorMode())
        assertEquals(com.example.data.model.InAppRefreshFrequency.EVERY_2_MIN, prefs.getInAppRefreshFrequency())
        assertEquals(com.example.data.model.WidgetRefreshFrequency.MANUAL_ONLY, prefs.getWidgetRefreshFrequency())
        assertEquals(com.example.data.model.WidgetLocationFrequency.EVERY_10_MIN, prefs.getWidgetLocationFrequency())
        assertEquals(com.example.data.model.GtfsSyncFrequency.TWICE_A_DAY, prefs.getGtfsSyncFrequency())
        assertEquals(com.example.data.model.TrafficRefreshFrequency.EVERY_30_MIN, prefs.getTrafficRefreshFrequency())
        assertTrue(prefs.isTrafficNotificationsEnabled())
        assertEquals(com.example.data.model.AppUpdateCheckFrequency.WEEKLY, prefs.getAppUpdateCheckFrequency())
        assertEquals(com.example.data.model.UpdateNetworkMode.WIFI_ONLY, prefs.getUpdateNetworkMode())
    }

    @Test
    fun `in app 45 second refresh frequency persists and restores via json backup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = com.example.data.local.PreferencesManager(context)

        assertEquals(45, com.example.data.model.InAppRefreshFrequency.EVERY_45_SEC.intervalSeconds)
        assertEquals("Toutes les 45 secondes", com.example.data.model.InAppRefreshFrequency.EVERY_45_SEC.label)

        prefs.saveInAppRefreshFrequency(com.example.data.model.InAppRefreshFrequency.EVERY_45_SEC)
        assertEquals(com.example.data.model.InAppRefreshFrequency.EVERY_45_SEC, prefs.getInAppRefreshFrequency())

        val json = prefs.exportBackupJson()
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val importRes = prefs.importBackupJson(json)
        assertTrue(importRes.isSuccess)
        assertEquals(com.example.data.model.InAppRefreshFrequency.EVERY_45_SEC, prefs.getInAppRefreshFrequency())
    }

    @Test
    fun `app update notifier posts notification with release description`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(context)
        val shadowNotificationManager = org.robolectric.Shadows.shadowOf(
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        )
        val release = com.example.data.remote.GitHubReleaseInfo(
            tagName = "v2.2.0",
            versionName = "2.2.0",
            releaseName = "Galaxie Bus v2.2.0",
            releaseNotes = "## Nouveautés\n- **Actualisation discrète** toutes les 45s\n- Corrections de bugs",
            htmlUrl = "https://github.com/kanooob/bus/releases/tag/v2.2.0",
            apkDownloadUrl = "https://github.com/kanooob/bus/releases/download/v2.2.0/app-release.apk",
            apkFileName = "app-release.apk",
            apkSizeBytes = 1024L,
            isNewerThanCurrent = true
        )

        // Sans permission sur Android 13+ (SDK 34), la notification est ignorée proprement
        assertFalse(com.example.data.remote.AppUpdateNotifier.notifyUpdateAvailable(context, release))

        // Avec la permission POST_NOTIFICATIONS accordée, la notification est envoyée avec les notes de release
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val posted = com.example.data.remote.AppUpdateNotifier.notifyUpdateAvailable(context, release)
        assertTrue(posted)
        assertEquals(1, shadowNotificationManager.allNotifications.size)
        val notification = shadowNotificationManager.allNotifications.first()
        val extras = notification.extras
        assertTrue(extras.getString(android.app.Notification.EXTRA_TITLE).orEmpty().contains("v2.2.0"))
        assertTrue(extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT).toString().contains("Actualisation discrète"))
    }

    @Test
    fun `discreet auto refresh does not trigger manual refreshing indicator`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.IdelisDatabase.getInstance(context)
        val prefs = com.example.data.local.PreferencesManager(context)
        val repo = com.example.data.repository.IdelisRepository(db, context, prefs)
        val viewModel = com.example.ui.viewmodel.TransitBoardViewModel(repo, prefs)

        viewModel.setInAppRefreshFrequency(com.example.data.model.InAppRefreshFrequency.EVERY_45_SEC)
        assertEquals(45, viewModel.uiState.value.secondsUntilRefresh)
        assertEquals(false, viewModel.uiState.value.isManualRefreshing)

        viewModel.refreshDepartures(isManual = false)
        assertEquals(false, viewModel.uiState.value.isManualRefreshing)
    }

    @Test
    fun `traffic alert notifier detects new alerts and respects notification preference`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = com.example.data.local.PreferencesManager(context)
        val shadowApp = org.robolectric.Shadows.shadowOf(context)
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val shadowNotificationManager = org.robolectric.Shadows.shadowOf(
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        )

        val alert1 = com.example.data.model.TrafficAlert(
            id = "a1",
            title = "Travaux rue Carnot",
            messageText = "Déviation ligne T1",
            affectedLines = listOf("T1"),
            severity = "WARNING",
            publishedDate = "Aujourd'hui"
        )
        val alert2 = com.example.data.model.TrafficAlert(
            id = "a2",
            title = "Manifestation centre-ville",
            messageText = "Arrêt Bosquet non desservi",
            affectedLines = listOf("F", "A"),
            severity = "WARNING",
            publishedDate = "Aujourd'hui"
        )

        prefs.setTrafficNotificationsEnabled(true)

        // 1. Première vérification : établit la baseline sans notifier
        val firstNew = com.example.data.remote.TrafficAlertNotifier.processAlerts(
            context = context,
            prefs = prefs,
            alerts = listOf(alert1),
            allowNotification = true
        )
        assertTrue(firstNew.isEmpty())
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        // 2. Même alerte : aucune notification
        val sameNew = com.example.data.remote.TrafficAlertNotifier.processAlerts(
            context = context,
            prefs = prefs,
            alerts = listOf(alert1),
            allowNotification = true
        )
        assertTrue(sameNew.isEmpty())
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        // 3. Nouvelle alerte ajoutée : notification postée
        val secondNew = com.example.data.remote.TrafficAlertNotifier.processAlerts(
            context = context,
            prefs = prefs,
            alerts = listOf(alert1, alert2),
            allowNotification = true
        )
        assertEquals(1, secondNew.size)
        assertEquals("Manifestation centre-ville", secondNew.first().title)
        assertEquals(1, shadowNotificationManager.allNotifications.size)
        val notifTitle = shadowNotificationManager.allNotifications.first().extras
            .getString(android.app.Notification.EXTRA_TITLE).orEmpty()
        assertTrue(notifTitle.contains("Manifestation centre-ville"))
    }

    private fun buildSampleGtfsZipBytes(): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            val files = mapOf(
                "agency.txt" to "agency_id,agency_name,agency_url,agency_timezone\nIDELIS,Idelis,https://www.idelis.fr,Europe/Paris\n",
                "calendar_dates.txt" to "service_id,date,exception_type\nSEM,20261114,1\n",
                "calendar.txt" to "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\nSEM,1,1,1,1,1,0,0,20260101,20261231\n",
                "fare_attributes.txt" to "fare_id,price,currency_type,payment_method,transfers\nT1,1.30,EUR,0,\n",
                "fare_rules.txt" to "fare_id,route_id\nT1,F\n",
                "feed_info.txt" to "feed_publisher_name,feed_publisher_url,feed_lang\nIdelis,https://data.idelis.fr,fr\n",
                "routes.txt" to "route_id,route_short_name,route_long_name,route_color,route_text_color\nF,F,Fébus,F18700,121212\n",
                "shapes.txt" to "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\nSH1,43.2995,-0.3668,1\n",
                // stop_times.txt intentionnellement avant stops.txt et trips.txt avec un stop_id absent de stopDao
                "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\nTRIP_ORPHAN,12:00:00,12:00:00,STOP_GTFS_EXT_1,1\n",
                "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\nSTOP_GTFS_EXT_1,Pôle Bosquet,43.2995,-0.3668\n",
                "transfers.txt" to "from_stop_id,to_stop_id,transfer_type\nSTOP_GTFS_EXT_1,STOP_GTFS_EXT_1,2\n",
                "trips.txt" to "trip_id,route_id,service_id,trip_headsign\nTRIP_1,F,SEM,Gare de Pau\n"
            )
            for ((name, content) in files) {
                zos.putNextEntry(java.util.zip.ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun `gtfs download falls back to wayback machine and imports full zip without foreign key error`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.IdelisDatabase.getInstance(context)
        val sampleZip = buildSampleGtfsZipBytes()
        val requestedUrls = mutableListOf<String>()

        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                requestedUrls.add(url)
                val builder = okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)

                when {
                    url.startsWith("https://data.idelis.fr/") -> {
                        builder.code(503).message("Service Unavailable")
                            .body(okhttp3.ResponseBody.create(null, "Down"))
                            .build()
                    }
                    url.startsWith("https://web.archive.org/cdx/search/cdx") -> {
                        val cdxJson = """
                            [
                              ["timestamp","original"],
                              ["20261110101500","https://opendata.idelis.fr/?file=GTFS_MultiRoutes%2FData.GTFS.zip"]
                            ]
                        """.trimIndent()
                        builder.code(200).message("OK")
                            .body(okhttp3.ResponseBody.create(null, cdxJson))
                            .build()
                    }
                    url.startsWith("https://web.archive.org/web/20261110101500id_/") -> {
                        builder.code(200).message("OK")
                            .body(okhttp3.ResponseBody.create(null, sampleZip))
                            .build()
                    }
                    else -> {
                        builder.code(404).message("Not Found")
                            .body(okhttp3.ResponseBody.create(null, ""))
                            .build()
                    }
                }
            }
            .build()

        val service = com.example.data.remote.GtfsDownloadService(db, mockClient)
        val report = service.downloadAndProcessGtfsZip(isFirstSync = true)

        assertTrue("La synchro GTFS doit réussir sans erreur FOREIGN KEY: ${report.errorMessage}", report.success)
        assertTrue(report.usedWaybackFallback)
        assertEquals("Wayback Machine", report.sourceUsed)
        assertEquals(1, report.routesCount)
        assertEquals(1, report.tripsCount)
        assertEquals(1, report.stopTimesCount)
        assertEquals(12, report.downloadedFiles.size)
        assertTrue(report.downloadedFiles.containsAll(listOf("routes.txt", "stops.txt", "trips.txt", "stop_times.txt", "calendar.txt", "calendar_dates.txt")))
        assertTrue(requestedUrls.any { it.startsWith("https://web.archive.org/web/20261110101500id_/") })
    }

    @Test
    fun `wayback fetcher falls back to legacy idelis snapshot via cdx picking latest date`() {
        val sampleZip = buildSampleGtfsZipBytes()
        val legacyUrl = com.example.data.remote.WaybackGtfsFetcher.LEGACY_IDELIS_GTFS_URL_ENCODED

        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val builder = okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)

                when {
                    // L'URL principale renvoie du HTML invalide au lieu d'un ZIP
                    url.startsWith("https://data.idelis.fr/") -> {
                        builder.code(200).message("OK")
                            .body(okhttp3.ResponseBody.create(null, "<html>Maintenance</html>"))
                            .build()
                    }
                    // Availability vide
                    url.startsWith("https://archive.org/wayback/available") -> {
                        builder.code(200).message("OK")
                            .body(okhttp3.ResponseBody.create(null, """{"archived_snapshots":{}}"""))
                            .build()
                    }
                    // CDX renvoie plusieurs instantanés pour l'URL Idelis -> doit prendre le dernier en date
                    url.startsWith("https://web.archive.org/cdx/search/cdx") && url.contains("opendata.idelis.fr") -> {
                        val cdxJson = """
                            [
                              ["timestamp","original"],
                              ["20260408120650","$legacyUrl"],
                              ["20261112090000","$legacyUrl"]
                            ]
                        """.trimIndent()
                        builder.code(200).message("OK")
                            .body(okhttp3.ResponseBody.create(null, cdxJson))
                            .build()
                    }
                    url.startsWith("https://web.archive.org/cdx/search/cdx") -> {
                        builder.code(200).message("OK")
                            .body(okhttp3.ResponseBody.create(null, """[]"""))
                            .build()
                    }
                    url == "https://web.archive.org/web/20261112090000id_/$legacyUrl" -> {
                        builder.code(200).message("OK")
                            .body(okhttp3.ResponseBody.create(null, sampleZip))
                            .build()
                    }
                    else -> {
                        builder.code(404).message("Not Found")
                            .body(okhttp3.ResponseBody.create(null, ""))
                            .build()
                    }
                }
            }
            .build()

        val fetcher = com.example.data.remote.WaybackGtfsFetcher(mockClient)
        val result = fetcher.fetchGtfsZip(
            primaryUrl = com.example.data.remote.GtfsDownloadService.DEFAULT_GTFS_ZIP_URL,
            isFirstSync = true
        )

        assertTrue(result.isSuccess)
        val fetched = result.getOrThrow()
        assertTrue(fetched.usedWaybackMachine)
        assertEquals("20261112090000", fetched.archiveTimestamp)
        assertEquals("https://web.archive.org/web/20261112090000id_/$legacyUrl", fetched.sourceUrl)
    }

    @Test
    fun `wayback fetcher falls back to dynamic latest wayback redirect when apis return nothing`() {
        val sampleZip = buildSampleGtfsZipBytes()
        val latestUrl = "https://web.archive.org/web/2id_/${com.example.data.remote.WaybackGtfsFetcher.LEGACY_IDELIS_GTFS_URL_ENCODED}"

        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val builder = okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)

                when (url) {
                    latestUrl -> {
                        builder.code(200).message("OK")
                            .body(okhttp3.ResponseBody.create(null, sampleZip))
                            .build()
                    }
                    else -> {
                        builder.code(503).message("Unavailable")
                            .body(okhttp3.ResponseBody.create(null, "Down"))
                            .build()
                    }
                }
            }
            .build()

        val fetcher = com.example.data.remote.WaybackGtfsFetcher(mockClient)
        val result = fetcher.fetchGtfsZip(
            primaryUrl = com.example.data.remote.GtfsDownloadService.DEFAULT_GTFS_ZIP_URL,
            isFirstSync = true
        )

        assertTrue(result.isSuccess)
        val fetched = result.getOrThrow()
        assertTrue(fetched.usedWaybackMachine)
        assertEquals(latestUrl, fetched.sourceUrl)
    }

    @Test
    fun `nearest stop searches autocomplete with few-word fallback and fetches matching pole instead of Bosquet`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.IdelisDatabase.getInstance(context)
        db.stopDao().clearAllStops()

        val recordedAutocompleteQueries = mutableListOf<String>()
        val recordedMonitoringPoles = mutableListOf<String>()

        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val body = req.body as? okhttp3.FormBody
                val params = mutableMapOf<String, String>()
                if (body != null) {
                    for (i in 0 until body.size) {
                        params[body.name(i)] = body.value(i)
                    }
                }
                val requete = params["requete"]
                val builder = okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")

                when (requete) {
                    "autocomplete" -> {
                        val search = params["requete_val[search]"].orEmpty()
                        recordedAutocompleteQueries.add(search)
                        val html = if (search.equals("Mitterrand", ignoreCase = true) ||
                            search.equals("Hôpital Mitterrand", ignoreCase = true)
                        ) {
                            """
                            <li class="ui-menu-item"><a data-pole="Lycée Mitterrand">Lycée Mitterrand</a></li>
                            <li class="ui-menu-item"><a data-pole="Hôpital François Mitterrand">Hôpital François Mitterrand</a></li>
                            """.trimIndent()
                        } else {
                            ""
                        }
                        builder.body(okhttp3.ResponseBody.create(null, html)).build()
                    }
                    "stop_monitoring" -> {
                        val pole = params["requete_val[pole]"].orEmpty()
                        recordedMonitoringPoles.add(pole)
                        val html = if (pole == "Hôpital François Mitterrand") {
                            """
                            <div class="ligne-group">
                                <div class="row"><div class="col-12"><img class="picto-ligne" src="/fileadmin/user_upload/pictos/F.png" alt="Ligne F" /></div></div>
                                <div class="row"><div class="col-12"><span class="text-black-50">Direction: PAU Gare de Pau - Arrêt: Hôpital François Mitterrand</span></div></div>
                                <div class="row"><div class="col-3 pr-1"><div class="p-1 text-center border rounded text-white bg-primary">4 min</div></div></div>
                            </div>
                            """.trimIndent()
                        } else {
                            ""
                        }
                        builder.body(okhttp3.ResponseBody.create(null, html)).build()
                    }
                    else -> builder.body(okhttp3.ResponseBody.create(null, "")).build()
                }
            }
            .build()

        val webService = com.example.data.remote.IdelisWebScraperService(client = mockClient)
        val repo = com.example.data.repository.IdelisRepository(
            database = db,
            appContext = context,
            webScraperService = webService
        )

        // Simuler un arrêt OpenDataSoft proche de l'utilisateur dont le libellé exact diffère du site web
        com.example.data.repository.IdelisRepository.globalDownloadedStopsCache = listOf(
            com.example.data.remote.StopServing(
                timeo = 9001,
                stopName = "PAU Hôpital F. Mitterrand Quai A",
                pole = null,
                lineRef = "F",
                direction = "PAU Gare de Pau",
                sens = 1,
                commune = "Pau",
                lat = 43.3256,
                lon = -0.3475
            )
        )

        val resolution = repo.resolveAndSaveNearestStop(userLat = 43.3257, userLon = -0.3474)
        org.junit.Assert.assertNotNull(resolution)
        assertTrue(resolution!!.matchedViaWebSearch)
        assertEquals("Hôpital François Mitterrand", resolution.stop.stopName)
        assertEquals("Hôpital François Mitterrand", resolution.stop.parentStation)
        assertTrue(
            "La recherche doit d'abord essayer le nom complet puis se replier sur quelques mots",
            recordedAutocompleteQueries.first() == "PAU Hôpital F. Mitterrand Quai A" &&
                recordedAutocompleteQueries.size >= 2
        )

        // Vérifier que l'arrêt est bien enregistré en base et utilisé pour getDeparturesForStop (pas Bosquet !)
        val persisted = db.stopDao().getStopById(resolution.stop.stopId)
        org.junit.Assert.assertNotNull(persisted)

        val departuresResult = repo.getDeparturesForStop(resolution.stop.stopId)
        assertEquals(listOf("Hôpital François Mitterrand"), recordedMonitoringPoles)
        assertEquals(1, departuresResult.departures.size)
        assertFalse(departuresResult.departures[0].destinationName.contains("Bosquet", ignoreCase = true))
        assertTrue(departuresResult.departures[0].destinationName.contains("PAU Gare de Pau"))
    }

    @Test
    fun `notification channels register all three categories with silent widget channel`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        com.example.data.remote.NotificationChannels.ensureAllChannels(context)

        val updatesChannel = manager.getNotificationChannel(com.example.data.remote.NotificationChannels.CHANNEL_APP_UPDATES)
        val trafficChannel = manager.getNotificationChannel(com.example.data.remote.NotificationChannels.CHANNEL_TRAFFIC_ALERTS)
        val widgetChannel = manager.getNotificationChannel(com.example.data.remote.NotificationChannels.CHANNEL_WIDGET_UPDATES)

        org.junit.Assert.assertNotNull(updatesChannel)
        org.junit.Assert.assertNotNull(trafficChannel)
        org.junit.Assert.assertNotNull(widgetChannel)

        assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, updatesChannel!!.importance)
        assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, trafficChannel!!.importance)
        assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, widgetChannel!!.importance)
        assertEquals(null, widgetChannel.sound)
        assertFalse(widgetChannel.shouldVibrate())
        assertFalse(widgetChannel.canShowBadge())
    }

    @Test
    fun `widget update notifier shows silent notification only during refresh and respects preference`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = com.example.data.local.PreferencesManager(context)
        val shadowApp = org.robolectric.Shadows.shadowOf(context)
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val shadowNotificationManager = org.robolectric.Shadows.shadowOf(
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        )

        assertTrue(prefs.isWidgetUpdateNotificationsEnabled())

        val started1 = com.example.data.remote.WidgetUpdateNotifier.showWidgetRefreshing(
            context = context,
            stopId = "WEB_Bosquet",
            stopName = "📍 Pôle Bosquet",
            prefs = prefs
        )
        assertTrue(started1)
        assertEquals(1, shadowNotificationManager.allNotifications.size)
        val notif = shadowNotificationManager.allNotifications.first()
        assertEquals(com.example.data.remote.NotificationChannels.CHANNEL_WIDGET_UPDATES, notif.channelId)
        assertTrue((notif.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
        assertTrue(notif.extras.getString(android.app.Notification.EXTRA_TEXT).orEmpty().contains("Pôle Bosquet"))

        // Deuxième widget en cours d'actualisation simultanée : la notification reste visible jusqu'à la fin des 2
        val started2 = com.example.data.remote.WidgetUpdateNotifier.showWidgetRefreshing(
            context = context,
            stopId = "WEB_Universite",
            stopName = "Université",
            prefs = prefs
        )
        assertTrue(started2)
        assertEquals(1, shadowNotificationManager.allNotifications.size)

        com.example.data.remote.WidgetUpdateNotifier.finishWidgetRefreshing(context)
        assertEquals(1, shadowNotificationManager.allNotifications.size)

        com.example.data.remote.WidgetUpdateNotifier.finishWidgetRefreshing(context)
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        // Désactivation via préférence + restauration JSON
        prefs.setWidgetUpdateNotificationsEnabled(false)
        assertFalse(
            com.example.data.remote.WidgetUpdateNotifier.showWidgetRefreshing(
                context = context,
                stopId = "WEB_Bosquet",
                stopName = "Pôle Bosquet",
                prefs = prefs
            )
        )
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        val backup = prefs.exportBackupJson()
        prefs.setWidgetUpdateNotificationsEnabled(true)
        assertTrue(prefs.importBackupJson(backup).isSuccess)
        assertFalse(prefs.isWidgetUpdateNotificationsEnabled())
    }

    @Test
    fun `gtfs download resolves json catalog export to fichier url and imports zip`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.IdelisDatabase.getInstance(context)
        val sampleZip = buildSampleGtfsZipBytes()
        val catalogUrl = com.example.data.remote.GtfsDownloadService.DEFAULT_GTFS_ZIP_URL
        val zipFileUrl = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/fichier-des-donnees-theoriques-du-reseau-idelis-au-format-gtfs/files/400d7da94eaacb5e52c612f8ac28e420"
        val catalogJson = """
            [{"id": "GTFS", "date_de_debut_de_validite": "2026-10-26", "date_de_fin_de_validite": "2027-07-04", "fichier": {"thumbnail": false, "filename": "gtfs.zip", "format": "zip", "width": 300, "id": "400d7da94eaacb5e52c612f8ac28e420", "height": 300, "url": "$zipFileUrl"}}]
        """.trimIndent()
        val requestedUrls = mutableListOf<String>()

        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                requestedUrls.add(url)
                val builder = okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                when (url) {
                    catalogUrl -> builder.code(200).message("OK")
                        .body(okhttp3.ResponseBody.create(null, catalogJson))
                        .build()
                    zipFileUrl -> builder.code(200).message("OK")
                        .body(okhttp3.ResponseBody.create(null, sampleZip))
                        .build()
                    else -> builder.code(404).message("Not Found")
                        .body(okhttp3.ResponseBody.create(null, ""))
                        .build()
                }
            }
            .build()

        val service = com.example.data.remote.GtfsDownloadService(db, mockClient)
        val report = service.downloadAndProcessGtfsZip(isFirstSync = true)

        assertTrue("La synchro GTFS via JSON doit réussir : ${report.errorMessage}", report.success)
        assertFalse(report.usedWaybackFallback)
        assertEquals("Direct", report.sourceUsed)
        assertEquals(listOf(catalogUrl, zipFileUrl), requestedUrls)
        assertEquals(1, report.routesCount)
        assertEquals(1, report.tripsCount)
        assertEquals(1, report.stopTimesCount)
    }

    @Test
    fun `line icons json export replaces legacy web pictos across departures and traffic alerts`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.IdelisDatabase.getInstance(context)

        val t4IconUrl = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/liste-des-icones-des-lignes/files/825f6179a80cb12fa46b337cc45beaf7"
        val fIconUrl = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/liste-des-icones-des-lignes/files/febus123456"
        val iconsJson = """
            [
              {"ligne": "T4", "dimensions_png": "470x486", "format": "PNG", "fichier_png": {"thumbnail": true, "filename": "t4.png", "format": "PNG", "width": 470, "id": "825f6179a80cb12fa46b337cc45beaf7", "height": 486, "color_summary": [], "url": "$t4IconUrl"}, "format0": "SVG", "fichier_svg": {"thumbnail": true, "filename": "t4.svg", "format": "svg", "width": 300, "id": "955ca3eec8c920b55bb6f1132a156fcb", "height": 300, "url": "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/liste-des-icones-des-lignes/files/955ca3eec8c920b55bb6f1132a156fcb"}},
              {"ligne": "F", "dimensions_png": "470x486", "format": "PNG", "fichier_png": {"url": "$fIconUrl"}}
            ]
        """.trimIndent()

        val mockExploreClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val builder = okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                when (url) {
                    com.example.data.remote.IdelisExploreService.URL_EXPORTS_ICONS -> {
                        builder.code(200).message("OK")
                            .body(okhttp3.ResponseBody.create(null, iconsJson))
                            .build()
                    }
                    else -> builder.code(404).message("Not Found")
                        .body(okhttp3.ResponseBody.create(null, ""))
                        .build()
                }
            }
            .build()

        val mockWebClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body = chain.request().body as? okhttp3.FormBody
                val requete = (0 until (body?.size ?: 0)).firstOrNull { body?.name(it) == "requete" }
                    ?.let { body?.value(it) }
                val html = if (requete == "infostrafic") {
                    """
                    <div class="alert-item">
                        <div class="alert-pictos">
                            <img class="picto-ligne" src="/fileadmin/user_upload/pictos/T4.png" alt="Ligne T4" />
                        </div>
                        <h4 class="alert-title">Déviation T4</h4>
                        <p class="alert-description">Travaux avenue de Lons.</p>
                    </div>
                    """.trimIndent()
                } else ""
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, html))
                    .build()
            }
            .build()

        com.example.data.repository.IdelisRepository.globalLineIconsMap = emptyMap()
        val exploreService = com.example.data.remote.IdelisExploreService(mockExploreClient)
        val webService = com.example.data.remote.IdelisWebScraperService(client = mockWebClient)
        val repo = com.example.data.repository.IdelisRepository(
            database = db,
            appContext = context,
            exploreService = exploreService,
            webScraperService = webService
        )

        repo.ensureLineIconsLoaded()
        assertEquals(t4IconUrl, repo.resolveLineIconUrl("T4"))
        assertEquals(fIconUrl, repo.resolveLineIconUrl("Fébus"))

        val comparisons = repo.compareWebDeparturesWithTheoretical(
            webDepartures = listOf(
                com.example.data.model.IdelisWebDeparture(
                    lineRef = "T4",
                    lineName = "Ligne T4",
                    linePictoUrl = "https://www.idelis.fr/fileadmin/user_upload/pictos/T4.png",
                    direction = "PAU CST Jean Feger",
                    stopQuai = null,
                    timeText = "5 min",
                    isRealTime = true,
                    waitMinutes = 5
                )
            ),
            theoreticalDepartures = emptyList()
        )
        assertEquals(1, comparisons.size)
        assertEquals(t4IconUrl, comparisons[0].iconUrl)

        val alerts = repo.fetchLiveTrafficAlerts().getOrThrow()
        assertEquals(1, alerts.size)
        assertEquals(listOf(t4IconUrl), alerts[0].affectedLinePictos)
    }

    @Test
    fun `preferences manager migrates legacy gtfs alternative_exports url to new json export url`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sp = context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE)
        sp.edit()
            .putString(
                "url_gtfs_zip",
                "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/fichier-des-donnees-theoriques-du-reseau-idelis-au-format-gtfs/alternative_exports/https_opendata_idelis_fr_file_gtfs_multiroutes_data_gtfs_zip/"
            )
            .commit()

        val prefs = com.example.data.local.PreferencesManager(context)
        assertEquals(com.example.data.local.PreferencesManager.DEFAULT_GTFS_ZIP_URL, prefs.getGtfsZipUrl())
    }

    @Test
    fun `gtfs schedule index selects weekday saturday sunday and calendar date exceptions accurately`() {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            val files = mapOf(
                "calendar.txt" to """
                    service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
                    SEM_SCO,1,1,1,1,1,0,0,20260101,20261231
                    SAMEDI,0,0,0,0,0,1,0,20260101,20261231
                    DIMANCHE,0,0,0,0,0,0,1,20260101,20261231
                """.trimIndent() + "\n",
                "calendar_dates.txt" to """
                    service_id,date,exception_type
                    SEM_SCO,20261111,2
                    DIMANCHE,20261111,1
                """.trimIndent() + "\n",
                "routes.txt" to "route_id,route_short_name,route_long_name,route_color,route_text_color\nF,F,Fébus,F18700,121212\n",
                "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\nBOS_A,PAU Bosquet Quai A,43.2995,-0.3668\n",
                // stop_times.txt volontairement avant trips.txt
                "stop_times.txt" to """
                    trip_id,arrival_time,departure_time,stop_id,stop_sequence
                    T_SEM,08:05:00,08:05:00,BOS_A,1
                    T_SAM,09:20:00,09:20:00,BOS_A,1
                    T_DIM,10:45:00,10:45:00,BOS_A,1
                """.trimIndent() + "\n",
                "trips.txt" to """
                    trip_id,route_id,service_id,trip_headsign
                    T_SEM,F,SEM_SCO,PAU Gare de Pau
                    T_SAM,F,SAMEDI,PAU Gare de Pau
                    T_DIM,F,DIMANCHE,PAU Gare de Pau
                """.trimIndent() + "\n"
            )
            for ((name, content) in files) {
                zos.putNextEntry(java.util.zip.ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        val zipBytes = baos.toByteArray()

        fun parisCal(year: Int, month: Int, day: Int): java.util.Calendar {
            return java.util.Calendar.getInstance(com.example.data.remote.GtfsScheduleIndex.PARIS_TZ).apply {
                set(year, month, day, 12, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
        }

        // Vendredi 13 nov 2026 -> Semaine (Orange), 08:05:00
        val friSnap = com.example.data.remote.GtfsScheduleIndex.buildSnapshotFromZipBytes(zipBytes, parisCal(2026, java.util.Calendar.NOVEMBER, 13))!!
        assertEquals(com.example.data.model.IdelisPeriod.ORANGE, friSnap.activePeriod)
        assertEquals(listOf("08:05:00"), friSnap.departuresByCanonicalStop["pôle bosquet"]?.map { it.departureTime })

        // Samedi 14 nov 2026 -> Samedi (Grise), 09:20:00
        val satSnap = com.example.data.remote.GtfsScheduleIndex.buildSnapshotFromZipBytes(zipBytes, parisCal(2026, java.util.Calendar.NOVEMBER, 14))!!
        assertEquals(com.example.data.model.IdelisPeriod.GRISE, satSnap.activePeriod)
        assertEquals(listOf("09:20:00"), satSnap.departuresByCanonicalStop["pôle bosquet"]?.map { it.departureTime })

        // Dimanche 15 nov 2026 -> Dimanche (Verte), 10:45:00
        val sunSnap = com.example.data.remote.GtfsScheduleIndex.buildSnapshotFromZipBytes(zipBytes, parisCal(2026, java.util.Calendar.NOVEMBER, 15))!!
        assertEquals(com.example.data.model.IdelisPeriod.VERTE, sunSnap.activePeriod)
        assertEquals(listOf("10:45:00"), sunSnap.departuresByCanonicalStop["pôle bosquet"]?.map { it.departureTime })

        // Mercredi 11 nov 2026 (jour férié via calendar_dates exception 2 sur SEM_SCO + 1 sur DIMANCHE) -> Verte, 10:45:00
        val holidaySnap = com.example.data.remote.GtfsScheduleIndex.buildSnapshotFromZipBytes(zipBytes, parisCal(2026, java.util.Calendar.NOVEMBER, 11))!!
        assertEquals(com.example.data.model.IdelisPeriod.VERTE, holidaySnap.activePeriod)
        assertEquals(listOf("10:45:00"), holidaySnap.departuresByCanonicalStop["pôle bosquet"]?.map { it.departureTime })

        // Archive expirée (2027) -> fallback sur le même jour de semaine (Samedi -> 09:20:00)
        val expiredArchiveSnap = com.example.data.remote.GtfsScheduleIndex.buildSnapshotFromZipBytes(zipBytes, parisCal(2027, java.util.Calendar.MAY, 15))!!
        assertEquals(com.example.data.model.IdelisPeriod.GRISE, expiredArchiveSnap.activePeriod)
        assertEquals(listOf("09:20:00"), expiredArchiveSnap.departuresByCanonicalStop["pôle bosquet"]?.map { it.departureTime })
    }

    @Test
    fun `delay comparison caps implausible 500min offsets and computes realistic delays`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.IdelisDatabase.getInstance(context)
        val repo = com.example.data.repository.IdelisRepository(database = db, appContext = context)

        val parisTz = com.example.data.remote.GtfsScheduleIndex.PARIS_TZ
        val nowCal = java.util.Calendar.getInstance(parisTz)
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.FRANCE).apply { timeZone = parisTz }

        // Cas 1 : écart énorme (~500 min) entre passage réel (dans 5 min) et un horaire théorique éloigné (+505 min) -> ramené à 0 (À l'heure)
        val farCal = (nowCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.MINUTE, 505) }
        val implausibleTheo = listOf(
            com.example.data.model.GtfsTheoreticalDeparture(
                stopId = "BOS_1",
                stopName = "Pôle Bosquet",
                routeId = "F",
                routeShortName = "F",
                routeLongName = "Fébus",
                routeColor = "F18700",
                routeTextColor = "121212",
                tripHeadsign = "PAU Gare de Pau",
                departureTime = fmt.format(farCal.time)
            )
        )
        val webDep = listOf(
            com.example.data.model.IdelisWebDeparture(
                lineRef = "F",
                lineName = "Fébus",
                linePictoUrl = null,
                direction = "PAU Gare de Pau",
                stopQuai = null,
                timeText = "5 min",
                isRealTime = true,
                waitMinutes = 5
            )
        )
        val guarded = repo.compareWebDeparturesWithTheoretical(webDep, implausibleTheo)
        assertEquals(1, guarded.size)
        assertEquals(0, guarded[0].delayMinutes)
        assertEquals(com.example.data.model.DepartureStatus.ON_TIME, guarded[0].status)

        // Cas 2 : retard réaliste de +4 min par rapport à un horaire GTFS concordant
        val nearCal = (nowCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.MINUTE, 1) }
        val plausibleTheo = listOf(
            implausibleTheo.first().copy(departureTime = fmt.format(nearCal.time))
        )
        val delayed = repo.compareWebDeparturesWithTheoretical(webDep, plausibleTheo)
        assertEquals(1, delayed.size)
        assertEquals(4, delayed[0].delayMinutes)
        assertEquals(com.example.data.model.DepartureStatus.DELAYED, delayed[0].status)
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun `widget roundCornerSquareBitmap center crops 470x486 icon to 96x96 with 30 percent rounded corners`() {
        val raw = android.graphics.Bitmap.createBitmap(470, 486, android.graphics.Bitmap.Config.ARGB_8888)
        raw.eraseColor(android.graphics.Color.rgb(241, 135, 0))

        val rounded = com.example.ui.widget.IdelisWidgetHelper.roundCornerSquareBitmap(raw, targetSize = 96, cornerPercent = 0.30f)
        assertEquals(96, rounded.width)
        assertEquals(96, rounded.height)

        // Les angles extrêmes (0,0) et (95,95) doivent être transparents suite à l'arrondi 30%
        assertEquals(0, android.graphics.Color.alpha(rounded.getPixel(0, 0)))
        assertEquals(0, android.graphics.Color.alpha(rounded.getPixel(95, 95)))
        // Le centre de l'icône reste totalement opaque
        assertEquals(255, android.graphics.Color.alpha(rounded.getPixel(48, 48)))
        raw.recycle()
        rounded.recycle()

        // Cas 2 : PNG 470x486 avec une marge transparente de 40px autour d'un carré orange
        val padded = android.graphics.Bitmap.createBitmap(470, 486, android.graphics.Bitmap.Config.ARGB_8888)
        padded.eraseColor(android.graphics.Color.TRANSPARENT)
        val c = android.graphics.Canvas(padded)
        val p = android.graphics.Paint().apply { color = android.graphics.Color.rgb(241, 135, 0) }
        c.drawRect(40f, 45f, 430f, 435f, p)

        val trimmedRounded = com.example.ui.widget.IdelisWidgetHelper.roundCornerSquareBitmap(padded, targetSize = 96, cornerPercent = 0.30f)
        // Après suppression de la marge transparente, les bords milieu (48, 2) et (2, 48) doivent être colorés et opaques
        assertEquals(255, android.graphics.Color.alpha(trimmedRounded.getPixel(48, 2)))
        assertEquals(255, android.graphics.Color.alpha(trimmedRounded.getPixel(2, 48)))
        // Et les angles extrêmes (0,0) et (10,10) à l'extérieur de l'arc 30% doivent être transparents
        assertEquals(0, android.graphics.Color.alpha(trimmedRounded.getPixel(0, 0)))
        assertEquals(0, android.graphics.Color.alpha(trimmedRounded.getPixel(4, 4)))
        padded.recycle()
        trimmedRounded.recycle()
    }

    @Test
    fun `getDeparturesForStop prioritizes stop_monitoring without blocking on heavy opendata and coalesces calls`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.IdelisDatabase.getInstance(context)
        val cacheManager = com.example.data.local.OpenDataCacheManager(context)
        cacheManager.clearAll()

        val prevIcons = com.example.data.repository.IdelisRepository.globalLineIconsMap
        val prevRoutes = com.example.data.repository.IdelisRepository.globalOfficialRoutesMap
        val prevStops = com.example.data.repository.IdelisRepository.globalDownloadedStopsCache
        val prevServings = com.example.data.repository.IdelisRepository.globalServingsByStopName

        com.example.data.repository.IdelisRepository.globalLineIconsMap = emptyMap()
        com.example.data.repository.IdelisRepository.globalLineIconsUpdatedAtMs = 0L
        com.example.data.repository.IdelisRepository.globalOfficialRoutesMap = emptyMap()
        com.example.data.repository.IdelisRepository.globalOfficialRoutesUpdatedAtMs = 0L
        com.example.data.repository.IdelisRepository.globalDownloadedStopsCache = emptyList()
        com.example.data.repository.IdelisRepository.globalDownloadedStopsUpdatedAtMs = 0L
        com.example.data.repository.IdelisRepository.globalServingsByStopName = emptyMap()

        try {
            val iconCalls = java.util.concurrent.atomic.AtomicInteger(0)
            val stopsJsonCalls = java.util.concurrent.atomic.AtomicInteger(0)
            val routesJsonCalls = java.util.concurrent.atomic.AtomicInteger(0)
            val stopMonitoringCalls = java.util.concurrent.atomic.AtomicInteger(0)

            val fIconUrl = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/liste-des-icones-des-lignes/files/febus_cached"
            val iconsJson = """[{"ligne": "F", "fichier_png": {"url": "$fIconUrl"}}]"""

            val mockExploreClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.toString()
                    val builder = okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                    when (url) {
                        com.example.data.remote.IdelisExploreService.URL_EXPORTS_ICONS -> {
                            iconCalls.incrementAndGet()
                            builder.code(200).message("OK")
                                .body(okhttp3.ResponseBody.create(null, iconsJson))
                                .build()
                        }
                        com.example.data.remote.IdelisExploreService.URL_EXPORTS_STOPS -> {
                            stopsJsonCalls.incrementAndGet()
                            builder.code(500).message("Should not block departures")
                                .body(okhttp3.ResponseBody.create(null, ""))
                                .build()
                        }
                        com.example.data.remote.IdelisExploreService.URL_EXPORTS_ROUTES -> {
                            routesJsonCalls.incrementAndGet()
                            builder.code(500).message("Should not block departures")
                                .body(okhttp3.ResponseBody.create(null, ""))
                                .build()
                        }
                        else -> builder.code(404).message("Not Found")
                            .body(okhttp3.ResponseBody.create(null, ""))
                            .build()
                    }
                }
                .build()

            val mockWebClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val body = chain.request().body as? okhttp3.FormBody
                    val requete = (0 until (body?.size ?: 0)).firstOrNull { body?.name(it) == "requete" }
                        ?.let { body?.value(it) }
                    if (requete == "stop_monitoring") {
                        stopMonitoringCalls.incrementAndGet()
                        Thread.sleep(25)
                    }
                    val html = """
                        <div class="ligne-group">
                            <div class="row"><div class="col-12"><img class="picto-ligne" src="/fileadmin/user_upload/pictos/F.png" alt="Ligne F" /></div></div>
                            <div class="row"><div class="col-12"><span class="text-black-50">Direction: PAU Gare de Pau - Arrêt: Bosquet</span></div></div>
                            <div class="row"><div class="col-3 pr-1"><div class="p-1 text-center border rounded text-white bg-primary">3 min</div></div></div>
                        </div>
                    """.trimIndent()
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(okhttp3.ResponseBody.create(null, html))
                        .build()
                }
                .build()

            val repo = com.example.data.repository.IdelisRepository(
                database = db,
                appContext = context,
                exploreService = com.example.data.remote.IdelisExploreService(mockExploreClient),
                webScraperService = com.example.data.remote.IdelisWebScraperService(client = mockWebClient)
            )

            // 1. Deux appels concurrents + un appel immédiat dans la foulée -> 1 seule requête stop_monitoring et 1 seul appel icônes
            val (r1, r2) = kotlinx.coroutines.coroutineScope {
                val r1Async = async { repo.getDeparturesForStop("WEB_Bosquet") }
                val r2Async = async { repo.getDeparturesForStop("WEB_Bosquet") }
                r1Async.await() to r2Async.await()
            }
            val r3 = repo.getDeparturesForStop("WEB_Bosquet")

            assertEquals(1, r1.departures.size)
            assertEquals(1, r2.departures.size)
            assertEquals(1, r3.departures.size)
            assertEquals(fIconUrl, r1.departures.first().iconUrl)
            assertEquals("stop_monitoring ne doit pas être spammé lors d'appels rapprochés", 1, stopMonitoringCalls.get())
            assertEquals("downloadLineIcons ne doit être appelé qu'une fois", 1, iconCalls.get())
            assertEquals("stops_by_line ne doit jamais bloquer getDeparturesForStop", 0, stopsJsonCalls.get())
            assertEquals("official_routes ne doit jamais bloquer getDeparturesForStop", 0, routesJsonCalls.get())

            // 2. Simuler un redémarrage à froid (RAM vidée) : les icônes sont restaurées depuis le disque sans appel réseau
            com.example.data.repository.IdelisRepository.globalLineIconsMap = emptyMap()
            com.example.data.repository.IdelisRepository.globalLineIconsUpdatedAtMs = 0L
            val coldRepo = com.example.data.repository.IdelisRepository(
                database = db,
                appContext = context,
                exploreService = com.example.data.remote.IdelisExploreService(mockExploreClient),
                webScraperService = com.example.data.remote.IdelisWebScraperService(client = mockWebClient)
            )
            coldRepo.ensureLineIconsLoaded()
            assertEquals(fIconUrl, coldRepo.resolveLineIconUrl("F"))
            assertEquals("Aucune requête réseau si le cache disque des icônes est frais", 1, iconCalls.get())
        } finally {
            cacheManager.clearAll()
            com.example.data.repository.IdelisRepository.globalLineIconsMap = prevIcons
            com.example.data.repository.IdelisRepository.globalLineIconsUpdatedAtMs = 0L
            com.example.data.repository.IdelisRepository.globalOfficialRoutesMap = prevRoutes
            com.example.data.repository.IdelisRepository.globalOfficialRoutesUpdatedAtMs = 0L
            com.example.data.repository.IdelisRepository.globalDownloadedStopsCache = prevStops
            com.example.data.repository.IdelisRepository.globalDownloadedStopsUpdatedAtMs = 0L
            com.example.data.repository.IdelisRepository.globalServingsByStopName = prevServings
        }
    }

    @Test
    fun `startup viewmodel skips double gtfs download and avoids spamming stop_monitoring`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = com.example.data.local.PreferencesManager(context)
        val db = com.example.data.local.IdelisDatabase.getInstance(context)
        db.stopDao().clearAllStops()
        db.stopDao().insertStops(listOf(com.example.data.repository.IdelisRepository.DEFAULT_INITIAL_STOP))

        val cacheManager = com.example.data.local.OpenDataCacheManager(context)
        cacheManager.clearAll()

        val prevIcons = com.example.data.repository.IdelisRepository.globalLineIconsMap
        val prevRoutes = com.example.data.repository.IdelisRepository.globalOfficialRoutesMap
        val prevStops = com.example.data.repository.IdelisRepository.globalDownloadedStopsCache
        val prevServings = com.example.data.repository.IdelisRepository.globalServingsByStopName

        com.example.data.repository.IdelisRepository.globalLineIconsMap = emptyMap()
        com.example.data.repository.IdelisRepository.globalLineIconsUpdatedAtMs = 0L
        com.example.data.repository.IdelisRepository.globalOfficialRoutesMap = emptyMap()
        com.example.data.repository.IdelisRepository.globalOfficialRoutesUpdatedAtMs = 0L
        com.example.data.repository.IdelisRepository.globalDownloadedStopsCache = emptyList()
        com.example.data.repository.IdelisRepository.globalDownloadedStopsUpdatedAtMs = 0L
        com.example.data.repository.IdelisRepository.globalServingsByStopName = emptyMap()

        try {
            val sampleZip = buildSampleGtfsZipBytes()
            val gtfsCatalogUrl = com.example.data.remote.GtfsDownloadService.DEFAULT_GTFS_ZIP_URL
            val gtfsZipFileUrl = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/fichier-des-donnees-theoriques-du-reseau-idelis-au-format-gtfs/files/400d7da94eaacb5e52c612f8ac28e420"
            val gtfsCatalogJson = """
                [{"id": "GTFS", "fichier": {"url": "$gtfsZipFileUrl"}}]
            """.trimIndent()
            val iconsJson = """[{"ligne": "F", "fichier_png": {"url": "https://data.idelis.fr/files/f.png"}}]"""
            val routesJson = """[{"ndeg_de_ligne": "F", "descriptif": "Fébus", "couleur_de_ligne": "#F18700", "sens": 1}]"""
            val stopsJson = """[{"timeo": 1, "nom": "Bosquet", "pole": "Bosquet", "ligne": "F", "direction": "Gare de Pau", "sens": 1, "code_commune": "Pau", "geo_point_2d": {"lat": 43.2995, "lon": -0.3668}}]"""

            val stopMonitoringCount = java.util.concurrent.atomic.AtomicInteger(0)
            val gtfsZipDownloadCount = java.util.concurrent.atomic.AtomicInteger(0)

            val mockExploreClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.toString()
                    val builder = okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                    when (url) {
                        com.example.data.remote.IdelisExploreService.URL_EXPORTS_ICONS ->
                            builder.code(200).message("OK").body(okhttp3.ResponseBody.create(null, iconsJson)).build()
                        com.example.data.remote.IdelisExploreService.URL_EXPORTS_ROUTES ->
                            builder.code(200).message("OK").body(okhttp3.ResponseBody.create(null, routesJson)).build()
                        com.example.data.remote.IdelisExploreService.URL_EXPORTS_STOPS ->
                            builder.code(200).message("OK").body(okhttp3.ResponseBody.create(null, stopsJson)).build()
                        gtfsCatalogUrl -> {
                            gtfsZipDownloadCount.incrementAndGet()
                            builder.code(200).message("OK").body(okhttp3.ResponseBody.create(null, gtfsCatalogJson)).build()
                        }
                        gtfsZipFileUrl ->
                            builder.code(200).message("OK").body(okhttp3.ResponseBody.create(null, sampleZip)).build()
                        else ->
                            builder.code(404).message("Not Found").body(okhttp3.ResponseBody.create(null, "")).build()
                    }
                }
                .build()

            val mockWebClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val body = chain.request().body as? okhttp3.FormBody
                    val requete = (0 until (body?.size ?: 0)).firstOrNull { body?.name(it) == "requete" }
                        ?.let { body?.value(it) }
                    if (requete == "stop_monitoring") {
                        stopMonitoringCount.incrementAndGet()
                    }
                    val html = when (requete) {
                        "stop_monitoring" -> """
                            <div class="ligne-group">
                                <div class="row"><div class="col-12"><img class="picto-ligne" src="/fileadmin/user_upload/pictos/F.png" alt="Ligne F" /></div></div>
                                <div class="row"><div class="col-12"><span class="text-black-50">Direction: PAU Gare de Pau - Arrêt: Bosquet</span></div></div>
                                <div class="row"><div class="col-3 pr-1"><div class="p-1 text-center border rounded text-white bg-primary">2 min</div></div></div>
                            </div>
                        """.trimIndent()
                        else -> ""
                    }
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(okhttp3.ResponseBody.create(null, html))
                        .build()
                }
                .build()

            val repo = com.example.data.repository.IdelisRepository(
                database = db,
                appContext = context,
                preferencesManager = prefs,
                exploreService = com.example.data.remote.IdelisExploreService(mockExploreClient),
                gtfsDownloadService = com.example.data.remote.GtfsDownloadService(db, mockExploreClient),
                webScraperService = com.example.data.remote.IdelisWebScraperService(client = mockWebClient)
            )
            // Désactiver le ping/update GitHub réseau pour ce test isolé
            prefs.setUsageStatsEnabled(false)
            prefs.saveAppUpdateCheckFrequency(com.example.data.model.AppUpdateCheckFrequency.NEVER)
            prefs.saveTrafficRefreshFrequency(com.example.data.model.TrafficRefreshFrequency.NEVER)

            val viewModel = com.example.ui.viewmodel.TransitBoardViewModel(repo, prefs)

            // Pomper le Looper principal Robolectric + attendre les coroutines IO de démarrage
            val shadowMainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            val deadline = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < deadline &&
                (viewModel.uiState.value.departures.isEmpty() ||
                    viewModel.uiState.value.isDownloadingData ||
                    viewModel.uiState.value.isGtfsSyncing ||
                    prefs.getLastGtfsSyncTimestamp() == 0L)
            ) {
                shadowMainLooper.idleFor(java.time.Duration.ofMillis(100))
                Thread.sleep(25L)
            }
            shadowMainLooper.idle()

            assertTrue(
                "Les prochains bus doivent être chargés au démarrage (departures=${viewModel.uiState.value.departures.size}, status=${viewModel.uiState.value.statusMessage})",
                viewModel.uiState.value.departures.isNotEmpty()
            )
            assertEquals(
                "stop_monitoring ne doit être appelé qu'une seule fois au démarrage",
                1,
                stopMonitoringCount.get()
            )
            assertEquals(
                "gtfs.zip ne doit être téléchargé qu'une seule fois au démarrage",
                1,
                gtfsZipDownloadCount.get()
            )
        } finally {
            cacheManager.clearAll()
            com.example.data.repository.IdelisRepository.globalLineIconsMap = prevIcons
            com.example.data.repository.IdelisRepository.globalLineIconsUpdatedAtMs = 0L
            com.example.data.repository.IdelisRepository.globalOfficialRoutesMap = prevRoutes
            com.example.data.repository.IdelisRepository.globalOfficialRoutesUpdatedAtMs = 0L
            com.example.data.repository.IdelisRepository.globalDownloadedStopsCache = prevStops
            com.example.data.repository.IdelisRepository.globalDownloadedStopsUpdatedAtMs = 0L
            com.example.data.repository.IdelisRepository.globalServingsByStopName = prevServings
        }
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun `line icon url is downloaded at most once across concurrent directions preloads and cold restart`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.ui.components.IdelisLineIconCache.clearForTesting(context)

        val rawPng = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(241, 135, 0))
        }
        val pngBytes = java.io.ByteArrayOutputStream().use { baos ->
            rawPng.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }
        rawPng.recycle()

        val downloadCount = java.util.concurrent.atomic.AtomicInteger(0)
        val iconUrl = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/liste-des-icones-des-lignes/files/unique_febus_png"

        val countingClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                downloadCount.incrementAndGet()
                Thread.sleep(30)
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, pngBytes))
                    .build()
            }
            .build()

        com.example.ui.components.IdelisLineIconCache.setHttpClientForTesting(countingClient)
        try {
            // 1. Deux directions d'une même ligne + préchargement avec alias en doublon demandent la même URL en parallèle
            val (img1, img2, img3) = kotlinx.coroutines.coroutineScope {
                com.example.ui.components.IdelisLineIconCache.preloadIcons(context, listOf(iconUrl, " $iconUrl "))
                val d1 = async { com.example.ui.components.IdelisLineIconCache.getOrLoadIcon(context, iconUrl) }
                val d2 = async { com.example.ui.components.IdelisLineIconCache.getOrLoadIcon(context, iconUrl) }
                val d3 = async { com.example.ui.components.IdelisLineIconCache.getOrLoadIcon(context, " $iconUrl ") }
                Triple(d1.await(), d2.await(), d3.await())
            }

            assertTrue(img1 != null)
            assertTrue(img2 != null)
            assertTrue(img3 != null)
            assertTrue(com.example.ui.components.IdelisLineIconCache.getCachedIconSync(iconUrl) != null)
            assertEquals("Une même icône ne doit être téléchargée qu'une seule fois malgré les 2 directions", 1, downloadCount.get())

            // 2. Simuler un redémarrage à froid (RAM vidée, fichier PNG conservé sur disque)
            com.example.ui.components.IdelisLineIconCache.clearForTesting(context = null)
            val fromDisk = com.example.ui.components.IdelisLineIconCache.getOrLoadIcon(context, iconUrl)
            assertTrue(fromDisk != null)
            assertEquals("Le redémarrage à froid doit relire le PNG sur disque sans re-télécharger", 1, downloadCount.get())
        } finally {
            com.example.ui.components.IdelisLineIconCache.setHttpClientForTesting(null)
            com.example.ui.components.IdelisLineIconCache.clearForTesting(context)
        }
    }

    @Test
    fun `switching stops skips siri fallback on empty http 200 and shows instant cache when revisited`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("idelis_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = com.example.data.local.PreferencesManager(context)
        prefs.setUsageStatsEnabled(false)
        prefs.saveAppUpdateCheckFrequency(com.example.data.model.AppUpdateCheckFrequency.NEVER)
        prefs.saveTrafficRefreshFrequency(com.example.data.model.TrafficRefreshFrequency.NEVER)

        val db = com.example.data.local.IdelisDatabase.getInstance(context)
        db.stopDao().clearAllStops()
        db.stopDao().insertStops(listOf(com.example.data.repository.IdelisRepository.DEFAULT_INITIAL_STOP))

        val cacheManager = com.example.data.local.OpenDataCacheManager(context)
        cacheManager.clearAll()

        val prevIcons = com.example.data.repository.IdelisRepository.globalLineIconsMap
        val prevRoutes = com.example.data.repository.IdelisRepository.globalOfficialRoutesMap
        com.example.data.repository.IdelisRepository.globalLineIconsMap = mapOf("F" to "https://data.idelis.fr/files/f.png", "T1" to "https://data.idelis.fr/files/t1.png")
        com.example.data.repository.IdelisRepository.globalLineIconsUpdatedAtMs = System.currentTimeMillis()
        com.example.data.repository.IdelisRepository.globalOfficialRoutesMap = emptyMap()

        try {
            val siriCalls = java.util.concurrent.atomic.AtomicInteger(0)
            val mockWebClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val body = chain.request().body as? okhttp3.FormBody
                    val pole = (0 until (body?.size ?: 0)).firstOrNull { body?.name(it) == "requete_val[pole]" }
                        ?.let { body?.value(it) } ?: ""
                    val html = when {
                        pole.contains("EmptyStop", ignoreCase = true) -> """<div class="alert">Aucun passage</div>"""
                        pole.contains("Gare", ignoreCase = true) -> """
                            <div class="ligne-group">
                                <div class="row"><div class="col-12"><img class="picto-ligne" src="/fileadmin/user_upload/pictos/T1.png" alt="Ligne T1" /></div></div>
                                <div class="row"><div class="col-12"><span class="text-black-50">Direction: PAU Cité Multimédia - Arrêt: Gare</span></div></div>
                                <div class="row"><div class="col-3 pr-1"><div class="p-1 text-center border rounded text-white bg-primary">6 min</div></div></div>
                            </div>
                        """.trimIndent()
                        else -> """
                            <div class="ligne-group">
                                <div class="row"><div class="col-12"><img class="picto-ligne" src="/fileadmin/user_upload/pictos/F.png" alt="Ligne F" /></div></div>
                                <div class="row"><div class="col-12"><span class="text-black-50">Direction: PAU Hôpital - Arrêt: Bosquet</span></div></div>
                                <div class="row"><div class="col-3 pr-1"><div class="p-1 text-center border rounded text-white bg-primary">2 min</div></div></div>
                            </div>
                        """.trimIndent()
                    }
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(okhttp3.ResponseBody.create(null, html))
                        .build()
                }
                .build()

            val countingSiriClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    siriCalls.incrementAndGet()
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(503)
                        .message("Mock")
                        .body(okhttp3.ResponseBody.create(null, ""))
                        .build()
                }
                .build()
            val mockSiriService = com.example.data.remote.IdelisSiriService(okHttpClient = countingSiriClient)

            val repo = com.example.data.repository.IdelisRepository(
                database = db,
                appContext = context,
                preferencesManager = prefs,
                siriService = mockSiriService,
                webScraperService = com.example.data.remote.IdelisWebScraperService(client = mockWebClient)
            )

            // 1. Un arrêt vide en HTTP 200 ne doit JAMAIS appeler SIRI SOAP (pas d'attente de 5s)
            val emptyRes = repo.getDeparturesForStop(
                stopId = "WEB_EmptyStop",
                stopOverride = com.example.data.model.GtfsStop(
                    stopId = "WEB_EmptyStop",
                    stopName = "EmptyStop",
                    parentStation = "EmptyStop"
                )
            )
            assertTrue(emptyRes.departures.isEmpty())
            assertEquals("SIRI ne doit pas être interrogé quand stop_monitoring HTTP 200 a déjà répondu", 0, siriCalls.get())

            // 2. ViewModel : sélection d'un nouvel arrêt -> clear immédiat + isLoading = true, puis retour -> cache instantané
            val vm = com.example.ui.viewmodel.TransitBoardViewModel(repo, prefs)
            val shadowMainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            val d1 = System.currentTimeMillis() + 3_000L
            while (System.currentTimeMillis() < d1 && vm.uiState.value.departures.isEmpty()) {
                shadowMainLooper.idleFor(java.time.Duration.ofMillis(50))
                Thread.sleep(15L)
            }
            assertEquals("F", vm.uiState.value.departures.first().lineRef)

            // Changer pour Gare de Pau (non encore visité) : doit passer immédiatement en chargement et effacer l'ancien arrêt
            val gareWebStop = com.example.data.model.IdelisWebStop(pole = "Gare de Pau", name = "Gare de Pau")
            vm.selectWebStop(gareWebStop)
            assertEquals("Gare de Pau", vm.uiState.value.selectedStop?.stopName)
            assertTrue("Les anciens bus de Bosquet ne doivent pas rester figés pendant le chargement du nouvel arrêt", vm.uiState.value.departures.isEmpty())
            assertTrue("isLoading doit être actif immédiatement au changement vers un arrêt non caché", vm.uiState.value.isLoading)

            val d2 = System.currentTimeMillis() + 3_000L
            while (System.currentTimeMillis() < d2 && vm.uiState.value.departures.none { it.lineRef == "T1" }) {
                shadowMainLooper.idleFor(java.time.Duration.ofMillis(50))
                Thread.sleep(15L)
            }
            assertEquals("T1", vm.uiState.value.departures.first().lineRef)

            // Revenir sur Pôle Bosquet déjà visité -> affichage instantané sans repasser par l'écran vide
            vm.selectStop(com.example.data.repository.IdelisRepository.DEFAULT_INITIAL_STOP)
            assertEquals("F", vm.uiState.value.departures.firstOrNull()?.lineRef)
            assertFalse("Un arrêt déjà visité récemment doit s'afficher instantanément sans écran de chargement bloquant", vm.uiState.value.isLoading)
        } finally {
            cacheManager.clearAll()
            com.example.data.repository.IdelisRepository.globalLineIconsMap = prevIcons
            com.example.data.repository.IdelisRepository.globalLineIconsUpdatedAtMs = 0L
            com.example.data.repository.IdelisRepository.globalOfficialRoutesMap = prevRoutes
        }
    }
}
