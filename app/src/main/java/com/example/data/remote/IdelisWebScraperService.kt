package com.example.data.remote

import android.util.Log
import com.example.data.model.DepartureComparison
import com.example.data.model.DepartureStatus
import com.example.data.model.IdelisWebDeparture
import com.example.data.model.IdelisWebMonitoringResult
import com.example.data.model.IdelisWebStop
import com.example.data.model.IdelisWebTrafficAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Service HTTP et parseur HTML Jsoup pour interagir directement avec l'endpoint Idelis :
 * https://www.idelis.fr/?type=777
 *
 * Supporte :
 * 1. Autocomplétion (recherche d'arrêts et extraction des data-pole / libellés)
 * 2. Stop Monitoring (prochains passages en direct, pictos de lignes, directions, quais, horaires temps réel, détection d'arrêt non desservi)
 * 3. Info Trafic (requête POST requete=infostrafic&requete_val%5Blignesfav%5D=0 et parsing des .alert-item)
 */
class IdelisWebScraperService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    var customBaseUrl: String? = null,
    var customQueryParam: String? = null
) {

    val effectiveUrl: String
        get() = customBaseUrl?.takeIf { it.isNotBlank() } ?: BASE_URL

    companion object {
        private const val TAG = "IdelisWebScraper"
        const val BASE_URL = "https://www.idelis.fr/?type=777"
        const val IDELIS_HOST = "https://www.idelis.fr"
        const val IDELIS_REFERER = "https://www.idelis.fr/"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    /**
     * Détecte la mention "Cet arrêt n'est pas desservi..." (insensible à la casse et aux apostrophes/accents).
     */
    fun containsNotServedText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val normalized = text
            .replace('’', '\'')
            .replace('ʼ', '\'')
            .lowercase(Locale.FRANCE)
        return normalized.contains("cet arrêt n'est pas desservi") ||
            normalized.contains("cet arret n'est pas desservi") ||
            normalized.contains("n'est pas desservi")
    }

    private fun resolveIdelisUrl(rawUrl: String?): String? {
        val clean = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (clean == "#" || clean.startsWith("javascript:", ignoreCase = true)) return null
        return when {
            clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> "$IDELIS_HOST$clean"
            clean.startsWith("?") -> "$IDELIS_HOST/$clean"
            else -> "$IDELIS_HOST/$clean"
        }
    }

    /**
     * 1. REQUÊTE D'AUTOCOMPLÉTION (Recherche d'arrêts/pôles)
     * Corps : requete=autocomplete&requete_val[search]=TERME_RECHERCHE
     * Renvoie la liste des arrêts trouvés avec leur data-pole et leur nom d'affichage.
     */
    suspend fun autocompleteStops(searchTerm: String): Result<List<IdelisWebStop>> = withContext(Dispatchers.IO) {
        if (searchTerm.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val formBody = FormBody.Builder()
                .add("requete", "autocomplete")
                .add("requete_val[search]", searchTerm.trim())
                .build()

            val request = Request.Builder()
                .url(effectiveUrl)
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", IDELIS_REFERER)
                .header("Accept", "text/html, */*; q=0.01")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erreur HTTP ${response.code}: ${response.message}"))
                }
                response.body?.string().orEmpty()
            }
            if (html.isBlank() || html.trim() == "ajax only") {
                return@withContext Result.success(emptyList())
            }

            // Parser le contenu HTML renvoyé avec Jsoup
            val doc = Jsoup.parse(html)
            val stopItems = mutableListOf<IdelisWebStop>()
            val seenPoles = mutableSetOf<String>()

            // Balises <li> avec class ui-menu-item ou balises <a> avec data-pole
            val elements = doc.select("li[data-value], li[data-pole], a[data-pole]")

            for (element in elements) {
                val anchor = if (element.tagName() == "a") element else element.selectFirst("a[data-pole]")
                val pole = anchor?.attr("data-pole")?.takeIf { it.isNotBlank() }
                    ?: element.attr("data-pole").takeIf { it.isNotBlank() }
                    ?: element.attr("data-value").takeIf { it.isNotBlank() }
                    ?: element.text().trim()

                val name = anchor?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: element.text().trim()

                if (pole.isNotBlank() && name.isNotBlank() && seenPoles.add(pole.lowercase(Locale.ROOT))) {
                    stopItems.add(
                        IdelisWebStop(
                            pole = pole.trim(),
                            name = name.trim()
                        )
                    )
                }
            }

            // Fallback si la structure HTML était un peu différente (ex: li simples sans attr data-pole explicite)
            if (stopItems.isEmpty() && doc.select("li").isNotEmpty()) {
                for (li in doc.select("li")) {
                    val text = li.text().trim()
                    if (text.isNotBlank() && seenPoles.add(text.lowercase(Locale.ROOT))) {
                        stopItems.add(IdelisWebStop(pole = text, name = text))
                    }
                }
            }

            Log.d(TAG, "Autocomplétion pour '$searchTerm': ${stopItems.size} arrêts trouvés")
            Result.success(stopItems)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur autocomplétion pour '$searchTerm': ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 2. REQUÊTE DES PROCHAINS PASSAGES (Stop Monitoring en direct)
     * Corps : requete=stop_monitoring&requete_val[pole]=NOM_DU_POLE
     * Extrait les lignes, directions, quais et horaires de passage en direct,
     * tout en détectant et filtrant les mentions "Cet arrêt n'est pas desservi...".
     */
    suspend fun getStopMonitoring(poleName: String): Result<IdelisWebMonitoringResult> = withContext(Dispatchers.IO) {
        if (poleName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Nom de pôle vide"))
        }

        try {
            val formBody = FormBody.Builder()
                .add("requete", "stop_monitoring")
                .add("requete_val[pole]", poleName.trim())
                .build()

            val request = Request.Builder()
                .url(effectiveUrl)
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", IDELIS_REFERER)
                .header("Accept", "text/html, */*; q=0.01")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erreur HTTP ${response.code}: ${response.message}"))
                }
                response.body?.string().orEmpty()
            }
            if (html.isBlank() || html.trim() == "ajax only") {
                return@withContext Result.success(
                    IdelisWebMonitoringResult(
                        pole = poleName,
                        departures = emptyList(),
                        alertMessage = "Réponse vide du serveur Idelis"
                    )
                )
            }

            val parsed = parseStopMonitoringHtml(poleName, html)
            Log.d(
                TAG,
                "Stop monitoring pour '$poleName': ${parsed.departures.size} passages, notServed=${parsed.isStopNotServed}"
            )
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur stop_monitoring pour '$poleName': ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Parse le HTML retourné par stop_monitoring.
     * Détecte si la réponse contient "Cet arrêt n'est pas desservi..." et exclut toute entrée concernée
     * afin de n'afficher que les prochains bus valides.
     */
    fun parseStopMonitoringHtml(poleName: String, html: String): IdelisWebMonitoringResult {
        val doc = Jsoup.parse(html)
        val alertElement = doc.selectFirst(".alert, .alert-info, .alert-warning, .alert-danger")
        val alertMessage = alertElement?.text()?.trim()?.takeIf { it.isNotBlank() }

        val fullDocText = doc.text()
        val isStopNotServed = containsNotServedText(fullDocText)
        val notServedMessage = if (isStopNotServed) {
            doc.select(".alert, .alert-info, .alert-warning, .alert-danger, p, span, div")
                .map { it.ownText().ifBlank { it.text() }.trim() }
                .firstOrNull { containsNotServedText(it) && it.length <= 220 }
                ?: alertMessage?.takeIf { containsNotServedText(it) }
                ?: "Cet arrêt n'est pas desservi actuellement."
        } else {
            null
        }

        val departures = mutableListOf<IdelisWebDeparture>()
        val ligneGroups = doc.select(".ligne-group")

        for (group in ligneGroups) {
            // 1. Extraction de la ligne et du picto
            val imgPicto = group.selectFirst("img.picto-ligne")
            val pictoSrc = resolveIdelisUrl(imgPicto?.attr("src"))
            val pictoAlt = imgPicto?.attr("alt")?.trim() ?: ""

            // Nom ou code de la ligne (ex: "F", "A", "B", "T1", "COXI")
            val lineRef = extractLineRef(pictoAlt, pictoSrc, group.text())
            val lineName = when {
                lineRef.equals("F", ignoreCase = true) -> "Fébus"
                lineRef.equals("COXI", ignoreCase = true) -> "Coxitis"
                lineRef.startsWith("Ligne", ignoreCase = true) -> lineRef
                else -> "Ligne $lineRef"
            }

            // 2. Extraction des directions et des temps de passage
            var currentDirection = ""
            var currentQuai: String? = null
            var isCurrentDirectionNotServed = false

            val rows = group.children().flatMap { if (it.hasClass("row")) listOf(it) else it.select(".row") }

            for (row in rows) {
                val directionSpan = row.selectFirst("span.text-black-50")
                if (directionSpan != null) {
                    val text = directionSpan.text()
                    isCurrentDirectionNotServed = containsNotServedText(text) || containsNotServedText(row.text())
                    val parsed = parseDirectionAndQuai(text)
                    currentDirection = parsed.first
                    currentQuai = parsed.second
                    continue
                }

                // Si la ligne entière indique que l'arrêt n'est pas desservi, ne pas extraire de passages
                if (containsNotServedText(row.text())) {
                    isCurrentDirectionNotServed = true
                    continue
                }

                if (isCurrentDirectionNotServed) {
                    continue
                }

                // Recherche des cases horaires
                val timeBoxes = row.select(".p-1.text-center.border, .border.rounded")
                if (timeBoxes.isNotEmpty() && currentDirection.isNotBlank()) {
                    for (box in timeBoxes) {
                        val boxText = box.text().trim()
                        if (containsNotServedText(boxText)) {
                            continue
                        }

                        val isRealTime = box.hasClass("bg-primary") ||
                            box.hasClass("text-white") ||
                            box.select("img.img-realtime").isNotEmpty() ||
                            box.select("img[src*='realtime']").isNotEmpty()

                        var rawText = boxText
                        // Cas particulier constaté : image realtime seule sans texte = bus imminent (0 min)
                        if (rawText.isBlank() && isRealTime) {
                            rawText = "Imminent"
                        }

                        if (rawText.isNotBlank() && !containsNotServedText(rawText)) {
                            val waitMinutes = computeWaitMinutes(rawText)
                            departures.add(
                                IdelisWebDeparture(
                                    lineRef = lineRef,
                                    lineName = lineName,
                                    linePictoUrl = pictoSrc,
                                    direction = currentDirection,
                                    stopQuai = currentQuai,
                                    timeText = rawText,
                                    isRealTime = isRealTime,
                                    waitMinutes = waitMinutes
                                )
                            )
                        }
                    }
                }
            }
        }

        // Filtrage final de sécurité : aucun bus contenant "Cet arrêt n'est pas desservi"
        departures.removeAll { dep ->
            containsNotServedText(dep.direction) ||
                containsNotServedText(dep.timeText) ||
                containsNotServedText(dep.lineName) ||
                containsNotServedText(dep.stopQuai)
        }

        // Trier par temps d'attente estimé croissant
        departures.sortBy { it.waitMinutes ?: 999 }

        return IdelisWebMonitoringResult(
            pole = poleName,
            departures = departures,
            alertMessage = alertMessage,
            isStopNotServed = isStopNotServed,
            notServedMessage = notServedMessage
        )
    }

    /**
     * 3. REQUÊTE INFO TRAFIC (Perturbations & Déviations en cours)
     * URL : https://www.idelis.fr/?type=777
     * Méthode : POST
     * Content-Type : application/x-www-form-urlencoded; charset=UTF-8
     * Body : requete=infostrafic&requete_val%5Blignesfav%5D=0
     * Headers : User-Agent, X-Requested-With: XMLHttpRequest, Referer: https://www.idelis.fr/
     */
    suspend fun getInfosTrafic(): Result<List<IdelisWebTrafficAlert>> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("requete", "infostrafic")
                .add("requete_val[lignesfav]", "0")
                .build()

            val request = Request.Builder()
                .url(effectiveUrl)
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", IDELIS_REFERER)
                .header("Accept", "text/html, */*; q=0.01")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erreur HTTP ${response.code}: ${response.message}"))
                }
                response.body?.string().orEmpty()
            }
            if (html.isBlank() || html.trim() == "ajax only") {
                return@withContext Result.success(emptyList())
            }

            val alerts = parseInfosTraficHtml(html)
            Log.d(TAG, "Info trafic idelis.fr : ${alerts.size} alerte(s) extraite(s)")
            Result.success(alerts)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur infostrafic: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Parse le HTML retourné par `requete=infostrafic` avec Jsoup.
     * Extrait les conteneurs `.alert-item` (titre, lignes concernées + pictos, description, lien de déviation `/?type=778...`).
     */
    fun parseInfosTraficHtml(html: String): List<IdelisWebTrafficAlert> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, IDELIS_HOST)

        // Priorité aux conteneurs .alert-item ; repli si la structure utilise une variante proche
        var items = doc.select(".alert-item")
        if (items.isEmpty()) {
            items = doc.select(".infotrafic-item, .perturbation-item, .trafic-item, div:has(> a[href*='type=778'])")
        }

        val alerts = mutableListOf<IdelisWebTrafficAlert>()
        items.forEachIndexed { index, item ->
            // 1. Lien vers le plan ou la fiche de déviation (ex: /?type=778...)
            val deviationAnchor = item.selectFirst(
                "a[href*='type=778'], a[href*='deviation'], a[href*='fiche'], a[href*='plan'], a[href$='.pdf'], a[href]"
            )
            val deviationLink = resolveIdelisUrl(deviationAnchor?.attr("href"))
            val anchorText = deviationAnchor?.text()?.trim().orEmpty()

            // 2. Pictos et codes des lignes impactées
            val pictos = mutableListOf<String>()
            val lineRefs = mutableListOf<String>()
            val pictoImgs = item.select("img.picto-ligne, img[src*='picto'], img[src*='ligne'], img[alt*='Ligne'], img")
            for (img in pictoImgs) {
                val src = resolveIdelisUrl(img.attr("src"))
                val alt = img.attr("alt").trim()
                val looksLikeLinePicto = img.hasClass("picto-ligne") ||
                    (src?.contains("picto", ignoreCase = true) == true) ||
                    (src?.contains("ligne", ignoreCase = true) == true) ||
                    alt.contains("Ligne", ignoreCase = true) ||
                    alt.equals("Fébus", ignoreCase = true) ||
                    alt.equals("Coxitis", ignoreCase = true)

                if (looksLikeLinePicto) {
                    if (src != null && !pictos.contains(src)) {
                        pictos.add(src)
                    }
                    val extracted = extractLineRefOrNull(alt, src)
                    if (extracted != null && !lineRefs.contains(extracted)) {
                        lineRefs.add(extracted)
                    }
                }
            }

            // Fallback badges texte de ligne si aucun picto image
            if (lineRefs.isEmpty()) {
                val lineBadges = item.select(".badge, .ligne-badge, .picto-text")
                for (badge in lineBadges) {
                    val code = badge.text().trim()
                        .removePrefix("Ligne ")
                        .removePrefix("ligne ")
                        .trim()
                    if (code.isNotBlank() && code.length <= 6 && !lineRefs.contains(code.uppercase(Locale.ROOT))) {
                        lineRefs.add(code.uppercase(Locale.ROOT))
                    }
                }
            }

            // 3. Titre de l'alerte
            val titleEl = item.selectFirst(".alert-title, .title, h1, h2, h3, h4, h5, strong, .fw-bold, .font-weight-bold")
            val rawTitle = titleEl?.text()?.trim().orEmpty()

            // 4. Date / Période éventuelle
            val dateText = item.selectFirst(".alert-date, .date, time, .text-muted, small")
                ?.text()?.trim().orEmpty()

            // 5. Description détaillée
            val descElements = item.select(".alert-description, .alert-body, .alert-text, .description, p")
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it != rawTitle && it != anchorText && it != dateText }
                .distinct()

            var description = descElements.joinToString("\n").trim()
            if (description.isBlank()) {
                val clone = item.clone()
                clone.select(".alert-title, .title, h1, h2, h3, h4, h5, a[href], .alert-date, .date, time").remove()
                description = clone.text().trim()
            }

            val title = rawTitle.ifBlank {
                description.lineSequence().firstOrNull()?.take(90) ?: "Perturbation réseau Idelis"
            }
            if (description == title) {
                description = ""
            }

            if (title.isNotBlank() || description.isNotBlank() || deviationLink != null) {
                alerts.add(
                    IdelisWebTrafficAlert(
                        id = "infotrafic_${index}_${title.hashCode()}",
                        title = title,
                        description = description,
                        affectedLines = lineRefs,
                        affectedLinePictos = pictos,
                        deviationLink = deviationLink,
                        publishedDate = dateText
                    )
                )
            }
        }

        return alerts
    }

    private fun extractLineRefOrNull(alt: String, src: String?): String? {
        val altMatch = Regex("""Ligne\s+([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE).find(alt)
        if (altMatch != null) {
            return altMatch.groupValues[1].uppercase(Locale.ROOT)
        }
        if (alt.equals("Fébus", ignoreCase = true) || alt.equals("Febus", ignoreCase = true)) return "F"
        if (alt.equals("Coxitis", ignoreCase = true)) return "COXI"
        if (src != null) {
            val fileMatch = Regex("""/([A-Za-z0-9_-]+)\.(png|jpg|jpeg|svg|webp)""", RegexOption.IGNORE_CASE).find(src)
            if (fileMatch != null) {
                val code = fileMatch.groupValues[1]
                    .removePrefix("picto_")
                    .removePrefix("ligne_")
                    .removePrefix("line_")
                    .uppercase(Locale.ROOT)
                if (code.length in 1..6) {
                    return code
                }
            }
        }
        return null
    }

    /**
     * Convertit les prochains passages extraits du web Idelis en objets [DepartureComparison]
     * immédiatement compatibles avec l'interface Jetpack Compose existante.
     */
    fun convertToDepartureComparisons(
        webDepartures: List<IdelisWebDeparture>,
        poleName: String
    ): List<DepartureComparison> {
        val parisTz = TimeZone.getTimeZone("Europe/Paris")
        val now = Calendar.getInstance(parisTz)

        return webDepartures.mapIndexed { index, dep ->
            val waitMins = dep.waitMinutes?.toLong() ?: 0L
            val routeColor = getLineColorHex(dep.lineRef)

            // Calcul de l'heure estimée au format HH:mm
            val timeCal = Calendar.getInstance(parisTz).apply {
                add(Calendar.MINUTE, waitMins.toInt())
            }
            val formattedTime = String.format(Locale.FRANCE, "%02d:%02d", timeCal.get(Calendar.HOUR_OF_DAY), timeCal.get(Calendar.MINUTE))

            DepartureComparison(
                id = "web_${dep.lineRef}_${dep.direction}_${index}_${dep.timeText}",
                lineRef = dep.lineRef,
                lineName = dep.lineName,
                destinationName = dep.direction + (dep.stopQuai?.let { " ($it)" } ?: ""),
                routeColorHex = routeColor,
                routeTextColorHex = "#FFFFFF",
                aimedDepartureTimeFormatted = if (dep.timeText.contains(":")) dep.timeText else formattedTime,
                expectedDepartureTimeFormatted = if (dep.isRealTime) {
                    if (waitMins <= 0) "Imminent" else "Dans $waitMins min"
                } else null,
                delayMinutes = 0,
                status = DepartureStatus.ON_TIME,
                isRealTime = dep.isRealTime,
                minutesUntilDeparture = waitMins,
                comparisonLabel = if (dep.isRealTime) "Temps réel idelis.fr (direct)" else "Horaire prévu",
                iconUrl = dep.linePictoUrl
            )
        }
    }

    private fun extractLineRef(alt: String, src: String?, fallbackText: String): String {
        // Ex: "Ligne F" -> "F"
        val altMatch = Regex("""Ligne\s+([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE).find(alt)
        if (altMatch != null) {
            return altMatch.groupValues[1].uppercase(Locale.ROOT)
        }

        // Ex: /fileadmin/user_upload/pictos/F.png -> "F"
        if (src != null) {
            val fileMatch = Regex("""/([A-Za-z0-9_-]+)\.(png|jpg|svg)""", RegexOption.IGNORE_CASE).find(src)
            if (fileMatch != null) {
                return fileMatch.groupValues[1].uppercase(Locale.ROOT)
            }
        }

        // Fallback
        val textMatch = Regex("""\b([A-Z][0-9]?|[0-9]{1,2}|COXI)\b""").find(fallbackText)
        return textMatch?.value ?: "BUS"
    }

    private fun parseDirectionAndQuai(text: String): Pair<String, String?> {
        // Ex: "Direction: PAU Gare de Pau -  Arrêt: Pl. d'Espagne - Bosquet Quai A - "
        val dirMatch = Regex("""Direction:\s*([^–-]+?)(?:\s*-\s*Arr[êe]t:|\s*$)""", RegexOption.IGNORE_CASE).find(text)
        val dir = dirMatch?.groupValues?.getOrNull(1)?.trim() ?: text

        val quaiMatch = Regex("""Arr[êe]t:\s*([^–-]+?)(?:\s*-\s*|$)""", RegexOption.IGNORE_CASE).find(text)
        val quai = quaiMatch?.groupValues?.getOrNull(1)?.trim()

        return Pair(dir, quai)
    }

    private fun computeWaitMinutes(timeText: String): Int {
        val clean = timeText.trim()
        if (clean.equals("Imminent", ignoreCase = true) || clean.equals("À l'approche", ignoreCase = true) || clean.isBlank()) {
            return 0
        }

        // Ex: "6 min", "11 min", "57 min"
        val minMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(clean)
        if (minMatch != null) {
            return minMatch.groupValues[1].toIntOrNull() ?: 0
        }

        // Ex: "15:41"
        val timeMatch = Regex("""(\d{1,2}):(\d{2})""").find(clean)
        if (timeMatch != null) {
            val hour = timeMatch.groupValues[1].toInt()
            val minute = timeMatch.groupValues[2].toInt()
            val parisTz = TimeZone.getTimeZone("Europe/Paris")
            val now = Calendar.getInstance(parisTz)

            val target = Calendar.getInstance(parisTz).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // Si l'heure cible est passée depuis longtemps (ex: passage à minuit)
                if (before(now) && get(Calendar.HOUR_OF_DAY) < 4 && now.get(Calendar.HOUR_OF_DAY) >= 20) {
                    add(Calendar.DATE, 1)
                }
            }

            val diffMs = target.timeInMillis - now.timeInMillis
            return (diffMs / 60000L).coerceAtLeast(0).toInt()
        }

        return 0
    }

    private fun getLineColorHex(lineRef: String): String {
        return when (lineRef.uppercase(Locale.ROOT)) {
            "F" -> "#FFC107"     // Jaune Fébus
            "A" -> "#0085CA"     // Ligne A
            "B" -> "#E4002B"     // Ligne B
            "C" -> "#FF8C00"     // Ligne C
            "D" -> "#9C27B0"     // Ligne D
            "T1" -> "#0288D1"    // Bleu T1
            "T2" -> "#E64A19"    // Orange T2
            "T3" -> "#2E7D32"    // Vert T3
            "T4" -> "#8E24AA"    // Violet T4
            "5" -> "#0097A7"     // Cyan L5
            "6" -> "#D81B60"
            "7" -> "#3949AB"
            "8" -> "#00897B"
            "9" -> "#7CB342"
            "10" -> "#FB8C00"
            "11" -> "#5E35B1"
            "12" -> "#039BE5"
            "13" -> "#F4511E"
            "14" -> "#43A047"
            "COXI" -> "#8C935D"
            else -> "#8C935D"
        }
    }
}
