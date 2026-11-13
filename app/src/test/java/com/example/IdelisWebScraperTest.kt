package com.example

import com.example.data.remote.IdelisWebScraperService
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdelisWebScraperTest {

    private val service = IdelisWebScraperService()

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
}
