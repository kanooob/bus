package com.example

import com.example.data.remote.GitHubUpdaterService
import com.example.data.remote.IdelisWebScraperService
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdelisWebScraperTest {

    private val service = IdelisWebScraperService()
    private val updater = GitHubUpdaterService()

    @Test
    fun testAutocompleteHtmlParsing() {
        val sampleHtml = """
            <li class="col-12 ui-menu-item btn-light" data-value="Bosquet" tabindex="-1">
                <a style="cursor:pointer" class="d-block m-3" data-pole="Bosquet">Bosquet</a>
            </li>
            <li class="col-12 ui-menu-item btn-light" data-value="Place Verdun Bayard" tabindex="-1">
                <a style="cursor:pointer" class="d-block m-3" data-pole="Place Verdun Bayard">Place Verdun Bayard</a>
            </li>
            <li class="col-12 ui-menu-item btn-light" data-value="Place Verdun" tabindex="-1">
                <a style="cursor:pointer" class="d-block m-3" data-pole="Place Verdun">Place Verdun</a>
            </li>
        """.trimIndent()

        val doc = Jsoup.parse(sampleHtml)
        val elements = doc.select("li[data-value], li[data-pole], a[data-pole]")
        val poles = elements.mapNotNull { it.attr("data-pole").takeIf { p -> p.isNotBlank() } ?: it.attr("data-value") }.distinct()

        assertEquals(3, poles.size)
        assertEquals("Bosquet", poles[0])
        assertEquals("Place Verdun Bayard", poles[1])
        assertEquals("Place Verdun", poles[2])
    }

    @Test
    fun testStopMonitoringHtmlParsing() {
        val sampleHtml = """
            <div class="ligne-group">
                <div class="row">
                    <div class="col-12 border-0 mt-2">
                        <img class="picto-ligne" src="/fileadmin/user_upload/pictos/F.png" alt="Ligne F" />
                    </div>
                </div>
                <div class="row">
                    <div class="col-12 mt-2">
                        <span class="text-black-50"> Direction: PAU Gare de Pau -  Arrêt: Pl. d'Espagne - Bosquet Quai A - </span>
                    </div>
                </div>
                <div class="row">
                    <div class="col-3 pr-1">
                        <div class="p-1 text-center border rounded text-white bg-primary">
                            <img alt="" class="img-realtime mr-1" src="https://www.idelis.fr/typo3conf/ext/kg_package/Resources/Public/images/realtime_white.gif">6 min
                        </div>
                    </div>
                    <div class="col-3 pr-1">
                        <div class="p-1 text-center border rounded ">33 min</div>
                    </div>
                    <div class="col-3 pr-1">
                        <div class="p-1 text-center border rounded ">15:41</div>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(sampleHtml)
        val group = doc.selectFirst(".ligne-group")
        assertNotNull(group)

        val img = group!!.selectFirst("img.picto-ligne")
        assertNotNull(img)
        assertEquals("Ligne F", img!!.attr("alt"))

        val dirSpan = group.selectFirst("span.text-black-50")
        assertNotNull(dirSpan)
        assertTrue(dirSpan!!.text().contains("Direction: PAU Gare de Pau"))
        assertTrue(dirSpan.text().contains("Arrêt: Pl. d'Espagne - Bosquet Quai A"))

        val timeBoxes = group.select(".p-1.text-center.border, .border.rounded")
        assertEquals(3, timeBoxes.size)
        assertTrue(timeBoxes[0].hasClass("bg-primary"))
        assertTrue(timeBoxes[0].text().contains("6 min"))
        assertEquals("33 min", timeBoxes[1].text().trim())
        assertEquals("15:41", timeBoxes[2].text().trim())
    }

    @Test
    fun testStopNotServedDetectionAndFiltering() {
        val mixedHtml = """
            <div class="alert alert-warning">Cet arrêt n'est pas desservi par la ligne T1 jusqu'à nouvel ordre.</div>
            <div class="ligne-group">
                <div class="row">
                    <div class="col-12"><img class="picto-ligne" src="/fileadmin/user_upload/pictos/T1.png" alt="Ligne T1" /></div>
                </div>
                <div class="row">
                    <div class="col-12"><span class="text-black-50">Direction: PAU Cité Multimédia</span></div>
                </div>
                <div class="row">
                    <div class="col-12"><div class="p-1 text-center border rounded">Cet arrêt n'est pas desservi</div></div>
                </div>
            </div>
            <div class="ligne-group">
                <div class="row">
                    <div class="col-12"><img class="picto-ligne" src="/fileadmin/user_upload/pictos/F.png" alt="Ligne F" /></div>
                </div>
                <div class="row">
                    <div class="col-12"><span class="text-black-50">Direction: PAU Gare de Pau - Arrêt: Bosquet</span></div>
                </div>
                <div class="row">
                    <div class="col-3 pr-1">
                        <div class="p-1 text-center border rounded text-white bg-primary">4 min</div>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val result = service.parseStopMonitoringHtml("Bosquet", mixedHtml)
        assertTrue(result.isStopNotServed)
        assertNotNull(result.notServedMessage)
        assertEquals(1, result.departures.size)
        assertEquals("F", result.departures[0].lineRef)
        assertEquals("4 min", result.departures[0].timeText)
        assertEquals(1, result.notServedEntries.size)
        assertEquals("T1", result.notServedEntries[0].lineRef)
        assertEquals("PAU Cité Multimédia", result.notServedEntries[0].direction)
    }

    @Test
    fun testNotServedSchedulesExtractionStripsRepeatedPhrase() {
        val html = """
            <div class="ligne-group">
                <div class="row">
                    <div class="col-12"><img class="picto-ligne" src="/fileadmin/user_upload/pictos/6.png" alt="Ligne 6" /></div>
                </div>
                <div class="row">
                    <div class="col-12"><span class="text-black-50">Direction: PAU Bosquet - Arrêt: Pau Université -</span></div>
                </div>
                <div class="row">
                    <div class="col-3 pr-1"><div class="p-1 text-center border rounded">Cet arrêt n'est pas desservi [09:34]</div></div>
                    <div class="col-3 pr-1"><div class="p-1 text-center border rounded">Cet arrêt n'est pas desservi [09:53]</div></div>
                    <div class="col-3 pr-1"><div class="p-1 text-center border rounded">Cet arrêt n'est pas desservi [10:09]</div></div>
                    <div class="col-3 pr-1"><div class="p-1 text-center border rounded">Cet arrêt n'est pas desservi [10:31]</div></div>
                </div>
            </div>
            <div class="ligne-group">
                <div class="row">
                    <div class="col-12"><img class="picto-ligne" src="/fileadmin/user_upload/pictos/F.png" alt="Ligne F" /></div>
                </div>
                <div class="row">
                    <div class="col-12"><span class="text-black-50">Direction: PAU Gare de Pau</span></div>
                </div>
                <div class="row">
                    <div class="col-3 pr-1"><div class="p-1 text-center border rounded text-white bg-primary">5 min</div></div>
                </div>
            </div>
        """.trimIndent()

        val result = service.parseStopMonitoringHtml("Pau Université", html)
        assertTrue(result.isStopNotServed)
        assertEquals(null, result.notServedMessage)
        assertEquals(1, result.departures.size)
        assertEquals("F", result.departures[0].lineRef)

        // Seule la ligne/direction ayant "Cet arrêt n'est pas desservi" apparaît dans notServedEntries
        assertEquals(1, result.notServedEntries.size)
        val entry = result.notServedEntries[0]
        assertEquals("6", entry.lineRef)
        assertEquals("Ligne 6", entry.lineName)
        assertEquals("PAU Bosquet (Pau Université)", entry.direction)
        assertEquals(listOf("09:34", "09:53", "10:09", "10:31"), entry.times)
    }

    @Test
    fun testInfosTraficHtmlParsing() {
        val sampleHtml = """
            <div class="alert-item">
                <div class="alert-pictos">
                    <img class="picto-ligne" src="/fileadmin/user_upload/pictos/F.png" alt="Ligne F" />
                    <img class="picto-ligne" src="/fileadmin/user_upload/pictos/T1.png" alt="Ligne T1" />
                </div>
                <h4 class="alert-title">Travaux Rue Barthou</h4>
                <p class="alert-description">Déviation mise en place du 12 au 20 novembre.</p>
                <a href="/?type=778&tx_deviation=42" class="btn">Voir le plan de déviation</a>
            </div>
        """.trimIndent()

        val alerts = service.parseInfosTraficHtml(sampleHtml)
        assertEquals(1, alerts.size)
        val alert = alerts[0]
        assertEquals("Travaux Rue Barthou", alert.title)
        assertEquals("Déviation mise en place du 12 au 20 novembre.", alert.description)
        assertEquals(listOf("F", "T1"), alert.affectedLines)
        assertEquals(
            listOf(
                "https://www.idelis.fr/fileadmin/user_upload/pictos/F.png",
                "https://www.idelis.fr/fileadmin/user_upload/pictos/T1.png"
            ),
            alert.affectedLinePictos
        )
        assertEquals("https://www.idelis.fr/?type=778&tx_deviation=42", alert.deviationLink)
    }

    @Test
    fun testGitHubUpdaterVersionAndRepoParsing() {
        val (owner, repo) = updater.parseOwnerAndRepo("https://github.com/kanooob/bus")
        assertEquals("kanooob", owner)
        assertEquals("bus", repo)

        assertTrue(updater.isNewerVersion("v1.0.1", "1.0"))
        assertTrue(updater.isNewerVersion("v2.0", "1.9.9"))
        assertTrue(updater.isNewerVersion("release-2.2.0", "2.1.0"))
        assertTrue(updater.isNewerVersion("v2.2.0-beta1", "2.1.0"))
        assertFalse(updater.isNewerVersion("v1.0", "1.0"))
        assertFalse(updater.isNewerVersion("0.9.5", "1.0"))
    }

    @Test
    fun testReleaseNotesFormatting() {
        val raw = "## Nouveautés\r\n- **Mode 45s** ajouté\r\n* `Fix` réseau"
        val formatted = com.example.data.remote.formatReleaseNotesForDisplay(raw)
        assertEquals("Nouveautés\n• Mode 45s ajouté\n• Fix réseau", formatted)
    }
}
