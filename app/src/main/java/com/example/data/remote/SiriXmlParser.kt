package com.example.data.remote

import android.util.Log
import android.util.Xml
import com.example.data.model.SiriMonitoredStopVisit
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Parser XML haute-résilience pour le flux SIRI SOAP GetStopMonitoring d'Idelis.
 * Décode la réponse XML sans dépendance externe lourde en utilisant XmlPullParser Android natif.
 */
class SiriXmlParser {

    companion object {
        private const val TAG = "SiriXmlParser"
    }

    /**
     * Analyse le document XML SOAP SIRI et extrait la liste des passages surveillés (MonitoredStopVisit).
     */
    fun parseStopMonitoringResponse(xmlContent: String): List<SiriMonitoredStopVisit> {
        val visits = mutableListOf<SiriMonitoredStopVisit>()
        if (xmlContent.isBlank()) return visits

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var inMonitoredStopVisit = false

            var lineRef = ""
            var publishedLineName = ""
            var destinationName = ""
            var aimedDepartureTime = ""
            var expectedDepartureTime = ""
            var departureStatus = ""
            var stopPointRef = ""
            var vehicleRef = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.substringAfterLast(':') ?: ""

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("MonitoredStopVisit", ignoreCase = true)) {
                            inMonitoredStopVisit = true
                            lineRef = ""
                            publishedLineName = ""
                            destinationName = ""
                            aimedDepartureTime = ""
                            expectedDepartureTime = ""
                            departureStatus = ""
                            stopPointRef = ""
                            vehicleRef = ""
                        } else if (inMonitoredStopVisit) {
                            when {
                                tagName.equals("LineRef", ignoreCase = true) -> {
                                    lineRef = readText(parser)
                                }
                                tagName.equals("PublishedLineName", ignoreCase = true) -> {
                                    publishedLineName = readText(parser)
                                }
                                tagName.equals("DestinationName", ignoreCase = true) -> {
                                    destinationName = readText(parser)
                                }
                                tagName.equals("AimedDepartureTime", ignoreCase = true) -> {
                                    aimedDepartureTime = readText(parser)
                                }
                                tagName.equals("ExpectedDepartureTime", ignoreCase = true) -> {
                                    expectedDepartureTime = readText(parser)
                                }
                                tagName.equals("DepartureStatus", ignoreCase = true) -> {
                                    departureStatus = readText(parser)
                                }
                                tagName.equals("StopPointRef", ignoreCase = true) ||
                                tagName.equals("MonitoringRef", ignoreCase = true) -> {
                                    stopPointRef = readText(parser)
                                }
                                tagName.equals("VehicleRef", ignoreCase = true) -> {
                                    vehicleRef = readText(parser)
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("MonitoredStopVisit", ignoreCase = true) && inMonitoredStopVisit) {
                            inMonitoredStopVisit = false
                            if (lineRef.isNotBlank() || publishedLineName.isNotBlank()) {
                                visits.add(
                                    SiriMonitoredStopVisit(
                                        lineRef = lineRef.ifBlank { publishedLineName },
                                        publishedLineName = publishedLineName.ifBlank { lineRef },
                                        destinationName = destinationName.ifBlank { "Destination inconnue" },
                                        aimedDepartureTime = aimedDepartureTime,
                                        expectedDepartureTime = expectedDepartureTime.ifBlank { aimedDepartureTime },
                                        departureStatus = departureStatus.ifBlank { "onTime" },
                                        stopPointRef = stopPointRef.ifBlank { null },
                                        vehicleRef = vehicleRef.ifBlank { null }
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du décodage XML SIRI: ${e.message}", e)
        }

        return visits
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text.orEmpty().trim()
            parser.nextTag()
        }
        return result
    }
}
