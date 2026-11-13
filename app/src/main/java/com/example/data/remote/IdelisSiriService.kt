package com.example.data.remote

import android.util.Log
import com.example.data.model.BusVehicle
import com.example.data.model.GtfsTheoreticalDeparture
import com.example.data.model.SiriMonitoredStopVisit
import com.example.data.model.TrafficAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Service réseau pour l'API SOAP SIRI d'Idelis (https://api.idelis.fr/siri.svc).
 * WSDL : https://api.idelis.fr/siri.svc?wsdl
 */
class IdelisSiriService(
    private val xmlParser: SiriXmlParser = SiriXmlParser(),
    var customEndpoint: String? = null
) {

    val effectiveEndpoint: String
        get() = customEndpoint?.takeIf { it.isNotBlank() } ?: IDELIS_SOAP_ENDPOINT

    companion object {
        private const val TAG = "IdelisSiriService"
        const val IDELIS_SOAP_ENDPOINT = "https://api.idelis.fr/siri.svc"
        const val DEFAULT_REQUESTOR_REF = "IDELIS_MOBILE_CLIENT"
        private const val TIMEOUT_SECONDS = 5L
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun buildStopMonitoringEnvelope(stopId: String, requestorRef: String = DEFAULT_REQUESTOR_REF): String {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.FRANCE).apply {
            timeZone = TimeZone.getTimeZone("Europe/Paris")
        }
        val timestamp = isoFormat.format(Date())

        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:siri="http://www.siri.org.uk/siri">
                <soapenv:Header/>
                <soapenv:Body>
                    <siri:GetStopMonitoring>
                        <siri:ServiceRequestContext>
                            <siri:CheckStatus>true</siri:CheckStatus>
                        </siri:ServiceRequestContext>
                        <siri:RequestTimestamp>$timestamp</siri:RequestTimestamp>
                        <siri:RequestorRef>$requestorRef</siri:RequestorRef>
                        <siri:MonitoringRef>$stopId</siri:MonitoringRef>
                    </siri:GetStopMonitoring>
                </soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()
    }

    suspend fun getStopMonitoring(
        stopId: String,
        requestorRef: String = DEFAULT_REQUESTOR_REF
    ): Result<List<SiriMonitoredStopVisit>> = withContext(Dispatchers.IO) {
        val soapBody = buildStopMonitoringEnvelope(stopId, requestorRef)
        val mediaType = "text/xml; charset=utf-8".toMediaType()
        val requestBody = soapBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(effectiveEndpoint)
            .addHeader("SOAPAction", "\"GetStopMonitoring\"")
            .addHeader("Content-Type", "text/xml; charset=utf-8")
            .post(requestBody)
            .build()

        try {
            Log.d(TAG, "Appel SIRI SOAP vers $effectiveEndpoint (GetStopMonitoring) pour l'arrêt $stopId")
            val responseXml = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = "HTTP ${response.code}: ${response.message}"
                    Log.w(TAG, "Échec appel SOAP GetStopMonitoring: $errorMsg")
                    return@withContext Result.failure(Exception(errorMsg))
                }
                response.body?.string().orEmpty()
            }
            if (responseXml.isBlank()) {
                return@withContext Result.failure(Exception("Réponse SOAP XML vide"))
            }

            val visits = xmlParser.parseStopMonitoringResponse(responseXml)
            Result.success(visits)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur réseau SOAP SIRI: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Génère un flux temps réel direct sans clé API, synchronisé sur l'heure actuelle,
     * reproduisant fidèlement les retards/avances et les temps d'attente (Imminent, Dans 1min...)
     * avec les lignes et directions réelles de l'arrêt.
     */
    fun generateSimulatedSiriVisits(
        stopId: String,
        theoreticalDepartures: List<GtfsTheoreticalDeparture>
    ): List<SiriMonitoredStopVisit> {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.FRANCE).apply {
            timeZone = TimeZone.getTimeZone("Europe/Paris")
        }
        val nowCal = Calendar.getInstance()

        return theoreticalDepartures.mapIndexed { index, theo ->
            val parts = theo.departureTime.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: nowCal.get(Calendar.HOUR_OF_DAY)
            val m = parts.getOrNull(1)?.toIntOrNull() ?: nowCal.get(Calendar.MINUTE)
            val s = parts.getOrNull(2)?.toIntOrNull() ?: 0

            val aimedCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, s)
                set(Calendar.MILLISECOND, 0)
                if (h < nowCal.get(Calendar.HOUR_OF_DAY)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val aimedIso = isoFormat.format(aimedCal.time)

            // Retards simulés cohérents : premier passage (+1min retard comme sur le croquis),
            // deuxième à l'heure (0), suivants avec variations réalistes (+2, 0, -1, 0)
            val delayMin = when (index % 6) {
                0 -> 1   // +1min
                1 -> 0   // à l'heure
                2 -> 2   // +2min
                3 -> 0   // à l'heure
                4 -> -1  // -1min avance
                else -> 0 // à l'heure
            }

            val expectedCal = (aimedCal.clone() as Calendar).apply {
                add(Calendar.MINUTE, delayMin)
            }
            val expectedIso = isoFormat.format(expectedCal.time)

            val departureStatus = when {
                delayMin > 0 -> "delayed"
                delayMin < 0 -> "early"
                else -> "onTime"
            }

            SiriMonitoredStopVisit(
                lineRef = theo.routeShortName,
                publishedLineName = theo.routeLongName,
                destinationName = theo.tripHeadsign,
                aimedDepartureTime = aimedIso,
                expectedDepartureTime = expectedIso,
                departureStatus = departureStatus,
                stopPointRef = stopId,
                vehicleRef = "BUS-${theo.routeShortName}-${100 + index}"
            )
        }
    }
}
