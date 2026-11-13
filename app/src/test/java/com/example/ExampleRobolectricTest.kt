package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.SiriXmlParser
import org.junit.Assert.assertEquals
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
}


