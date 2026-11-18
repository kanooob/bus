package com.example.data.remote

import android.util.Log
import com.example.data.model.DepartureComparison
import com.example.data.model.DepartureStatus
import com.example.data.model.IdelisPeriod
import com.example.data.model.IdelisWebDeparture
import com.example.data.model.IdelisWebMonitoringResult
import com.example.data.model.IdelisWebStop
import com.example.data.model.IdelisWebTrafficAlert
import com.example.data.model.NotServedEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.Normalizer
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
        .connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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

        private val QUAI_EXTRACT_REGEX = Regex("""\bquai[s]?\s*[:\-]?\s*([a-z]\d?|\d{1,2})\b""", RegexOption.IGNORE_CASE)
        private val ARRET_LABEL_REGEX = Regex("""(?:^|\s*[-–—]\s*|\s+)Arr[êe]t\s*:\s*""", RegexOption.IGNORE_CASE)
        private val DIRECTION_PREFIX_REGEX = Regex("""^\s*Direction\s*:\s*""", RegexOption.IGNORE_CASE)

        /**
         * Extrait uniquement la mention de quai et sa lettre/numéro (ex: "Pl. d'Espagne - Bosquet Quai A" -> "Quai A").
         * Renvoie null si aucun quai explicite n'est présent.
         */
        fun extractQuaiLabel(rawStopOrQuai: String?): String? {
            val clean = rawStopOrQuai?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val match = QUAI_EXTRACT_REGEX.find(clean) ?: return null
            val identifier = match.groupValues[1].uppercase(Locale.ROOT)
            return "Quai $identifier"
        }

        private fun safeLogD(msg: String) {
            try {
                Log.d(TAG, msg)
            } catch (_: RuntimeException) {
            }
        }

        private fun safeLogE(msg: String, tr: Throwable? = null) {
            try {
                if (tr != null) Log.e(TAG, msg, tr) else Log.e(TAG, msg)
            } catch (_: RuntimeException) {
            }
        }
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

            safeLogD("Autocomplétion pour '$searchTerm': ${stopItems.size} arrêts trouvés")
            Result.success(stopItems)
        } catch (e: Exception) {
            safeLogE("Erreur autocomplétion pour '$searchTerm': ${e.message}", e)
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
            safeLogD(
                "Stop monitoring pour '$poleName': ${parsed.departures.size} passages, notServed=${parsed.isStopNotServed}"
            )
            Result.success(parsed)
        } catch (e: Exception) {
            safeLogE("Erreur stop_monitoring pour '$poleName': ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Parse le HTML retourné par stop_monitoring.
     * Détecte si la réponse contient "Cet arrêt n'est pas desservi..." et exclut toute entrée concernée
     * des départs normaux, tout en extrayant la ligne, la direction et les horaires concernés (sans le texte répété).
     */
    fun parseStopMonitoringHtml(poleName: String, html: String): IdelisWebMonitoringResult {
        val doc = Jsoup.parse(html)
        val alertElement = doc.selectFirst(".alert, .alert-info, .alert-warning, .alert-danger")
        val alertMessage = alertElement?.text()?.trim()?.takeIf { it.isNotBlank() }

        val fullDocText = doc.text()
        val isStopNotServed = containsNotServedText(fullDocText)

        val departures = mutableListOf<IdelisWebDeparture>()
        val rawNotServedEntries = mutableListOf<NotServedEntry>()
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
            val lineColorHex = getLineColorHex(lineRef)

            // 2. Extraction des directions et des temps de passage
            var currentDirection = ""
            var currentQuai: String? = null
            var isCurrentDirectionNotServed = false
            var hasNotServedInCurrentDirection = false
            val notServedTimesForCurrentDirection = mutableListOf<String>()

            fun flushCurrentNotServedDirection() {
                if (hasNotServedInCurrentDirection || notServedTimesForCurrentDirection.isNotEmpty()) {
                    val cleanDir = cleanNotServedTextFromLabel(currentDirection)
                        .ifBlank { "Direction non précisée" }
                    val cleanQuai = currentQuai?.let { cleanNotServedTextFromLabel(it) }?.takeIf { it.isNotBlank() }
                    val formattedQuai = extractQuaiLabel(cleanQuai) ?: cleanQuai
                    val dirWithQuai = if (formattedQuai != null) "$cleanDir ($formattedQuai)" else cleanDir
                    rawNotServedEntries.add(
                        NotServedEntry(
                            lineRef = lineRef,
                            lineName = lineName,
                            linePictoUrl = pictoSrc,
                            lineColorHex = lineColorHex,
                            direction = dirWithQuai,
                            times = notServedTimesForCurrentDirection.distinct()
                        )
                    )
                }
                hasNotServedInCurrentDirection = false
                notServedTimesForCurrentDirection.clear()
            }

            val rows = group.children().flatMap { if (it.hasClass("row")) listOf(it) else it.select(".row") }

            for (row in rows) {
                val directionSpan = row.selectFirst("span.text-black-50")
                if (directionSpan != null) {
                    flushCurrentNotServedDirection()
                    val text = directionSpan.text()
                    isCurrentDirectionNotServed = containsNotServedText(text) || containsNotServedText(row.text())
                    if (isCurrentDirectionNotServed) {
                        hasNotServedInCurrentDirection = true
                        notServedTimesForCurrentDirection.addAll(extractTimesFromNotServedText(row.text()))
                    }
                    val parsed = parseDirectionAndQuai(text)
                    currentDirection = cleanNotServedTextFromLabel(parsed.first)
                    currentQuai = parsed.second?.let { cleanNotServedTextFromLabel(it) }?.takeIf { it.isNotBlank() }
                    continue
                }

                // Recherche des cases horaires
                val timeBoxes = row.select(".p-1.text-center.border, .border.rounded")
                if (timeBoxes.isNotEmpty()) {
                    for (box in timeBoxes) {
                        val boxText = box.text().trim()
                        if (containsNotServedText(boxText) || isCurrentDirectionNotServed) {
                            hasNotServedInCurrentDirection = true
                            notServedTimesForCurrentDirection.addAll(extractTimesFromNotServedText(boxText))
                            continue
                        }

                        if (currentDirection.isBlank()) continue

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
                                    stopQuai = extractQuaiLabel(currentQuai),
                                    timeText = rawText,
                                    isRealTime = isRealTime,
                                    waitMinutes = waitMinutes
                                )
                            )
                        }
                    }
                } else if (containsNotServedText(row.text()) || isCurrentDirectionNotServed) {
                    hasNotServedInCurrentDirection = true
                    notServedTimesForCurrentDirection.addAll(extractTimesFromNotServedText(row.text()))
                }
            }

            flushCurrentNotServedDirection()
        }

        // Fusionner les entrées d'une même ligne et direction pour regrouper leurs horaires non desservis
        val mergedNotServedEntries = rawNotServedEntries
            .groupBy { "${it.lineRef.uppercase(Locale.ROOT)}|${it.direction.lowercase(Locale.FRANCE)}" }
            .values
            .map { entries ->
                val first = entries.first()
                first.copy(times = entries.flatMap { it.times }.distinct())
            }

        val notServedMessage = if (isStopNotServed) {
            alertMessage?.takeIf { containsNotServedText(it) }
                ?: if (mergedNotServedEntries.isEmpty()) {
                    doc.select("p, span, div")
                        .map { it.ownText().trim() }
                        .firstOrNull { containsNotServedText(it) && it.length <= 220 }
                        ?: "Cet arrêt n'est pas desservi actuellement."
                } else {
                    null
                }
        } else {
            null
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
            notServedMessage = notServedMessage,
            notServedEntries = mergedNotServedEntries
        )
    }

    internal fun extractTimesFromNotServedText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val withoutPhrase = text
            .replace(Regex("""(?i)cet\s+arr[êe]t\s+n['’ʼ]est\s+pas\s+desservi[^0-9\[]*"""), " ")
            .replace(Regex("""(?i)n['’ʼ]est\s+pas\s+desservi[^0-9\[]*"""), " ")
            .trim()

        val timeRegex = Regex("""\b(\d{1,2}[:h]\d{2}|\d{1,3}\s*min|Imminent|À\s+l'approche)\b""", RegexOption.IGNORE_CASE)
        val matches = timeRegex.findAll(withoutPhrase).map { it.value.trim() }.toList()
        if (matches.isNotEmpty()) {
            return matches
        }

        return Regex("""\[([^\]]+)]""").findAll(withoutPhrase)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() && !containsNotServedText(it) }
            .toList()
    }

    private fun cleanNotServedTextFromLabel(text: String): String {
        return text
            .replace(Regex("""(?i)cet\s+arr[êe]t\s+n['’ʼ]est\s+pas\s+desservi[^.\n]*"""), "")
            .replace(Regex("""(?i)n['’ʼ]est\s+pas\s+desservi[^.\n]*"""), "")
            .trim(' ', '-', '–', ':', ',', ';')
    }

    private data class ParsedWebTrafficAlert(
        val alert: IdelisWebTrafficAlert,
        val rawDateAttr: String
    )

    internal fun cleanPublishedDate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val normalized = Normalizer.normalize(trimmed.lowercase(Locale.FRANCE), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[•·\\-|/]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return when {
            normalized == "en cours" -> ""
            normalized == "idelis.fr" -> ""
            normalized == "en cours idelis.fr" -> ""
            normalized.startsWith("en cours") && normalized.contains("idelis.fr") -> ""
            else -> trimmed
        }
    }

    internal fun isAlertWithinOneMonth(
        title: String,
        description: String,
        publishedDate: String,
        rawDateAttr: String = "",
        nowCal: Calendar = Calendar.getInstance(GtfsScheduleIndex.PARIS_TZ)
    ): Boolean {
        val combined = listOf(rawDateAttr, publishedDate, title, description)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
        if (combined.isBlank()) return true

        val parsedDates = extractExplicitYearDates(combined)
        if (parsedDates.isEmpty()) {
            // Aucune date avec année explicite : ne pas masquer une alerte en cours sans année
            return true
        }

        val cutoff = (nowCal.clone() as Calendar).apply {
            add(Calendar.MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val latestDate = parsedDates.maxByOrNull { it.timeInMillis } ?: return true
        return !latestDate.before(cutoff)
    }

    internal fun isTrafficAlertWithinOneMonth(
        alert: com.example.data.model.TrafficAlert,
        nowCal: Calendar = Calendar.getInstance(GtfsScheduleIndex.PARIS_TZ)
    ): Boolean {
        return isAlertWithinOneMonth(
            title = alert.title,
            description = alert.messageText,
            publishedDate = alert.publishedDate,
            nowCal = nowCal
        )
    }

    private fun extractExplicitYearDates(text: String): List<Calendar> {
        val fold = Normalizer.normalize(text.lowercase(Locale.FRANCE), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val dates = mutableListOf<Calendar>()

        fun addDate(year: Int, month1to12: Int, day: Int) {
            if (year !in 2000..2100 || month1to12 !in 1..12 || day !in 1..31) return
            val cal = Calendar.getInstance(GtfsScheduleIndex.PARIS_TZ).apply {
                isLenient = false
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month1to12 - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            try {
                // Triggers strict validation of calendar fields (e.g. rejects Feb 30)
                cal.timeInMillis
                dates.add(cal)
            } catch (_: Exception) {
            }
        }

        // 1. ISO yyyy-MM-dd
        val isoRegex = Regex("""\b(20\d{2})-(\d{1,2})-(\d{1,2})\b""")
        isoRegex.findAll(fold).forEach { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return@forEach
            val mo = m.groupValues[2].toIntOrNull() ?: return@forEach
            val d = m.groupValues[3].toIntOrNull() ?: return@forEach
            addDate(y, mo, d)
        }

        // 2. Numeric dd/MM/yyyy, dd-MM-yyyy, dd.MM.yyyy or dd/MM/yy
        val dmyRegex = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](20\d{2}|\d{2})\b""")
        dmyRegex.findAll(fold).forEach { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@forEach
            val mo = m.groupValues[2].toIntOrNull() ?: return@forEach
            val rawY = m.groupValues[3].toIntOrNull() ?: return@forEach
            val y = if (rawY < 100) 2000 + rawY else rawY
            addDate(y, mo, d)
        }

        // 3. French month + year: e.g. "12 septembre 2026" or "1er octobre 2026"
        val monthMap = mapOf(
            "janvier" to 1, "janv" to 1,
            "fevrier" to 2, "fev" to 2,
            "mars" to 3,
            "avril" to 4, "avr" to 4,
            "mai" to 5,
            "juin" to 6,
            "juillet" to 7, "juil" to 7,
            "aout" to 8,
            "septembre" to 9, "sept" to 9,
            "octobre" to 10, "oct" to 10,
            "novembre" to 11, "nov" to 11,
            "decembre" to 12, "dec" to 12
        )
        val frDateRegex = Regex(
            """\b(\d{1,2})(?:er)?\s+(janvier|janv|fevrier|fev|mars|avril|avr|mai|juin|juillet|juil|aout|septembre|sept|octobre|oct|novembre|nov|decembre|dec)\.?\s+(20\d{2})\b"""
        )
        frDateRegex.findAll(fold).forEach { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@forEach
            val moName = m.groupValues[2]
            val y = m.groupValues[3].toIntOrNull() ?: return@forEach
            val mo = monthMap[moName] ?: return@forEach
            addDate(y, mo, d)
        }

        return dates
    }

    internal fun isDirectDocumentOrPlanUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        val isDocExt = path.endsWith(".pdf") ||
            path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".jpeg") ||
            path.endsWith(".webp")
        if (!isDocExt) return false
        if (path.contains("/pictos/") || path.contains("picto-ligne") || path.contains("realtime")) return false
        return true
    }

    internal fun isGenericTrafficOverviewUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase(Locale.ROOT).trim()
        val hasSpecificParam = lower.contains("tx_") ||
            lower.contains("deviation=") ||
            lower.contains("perturbation=") ||
            lower.contains("plan=") ||
            lower.contains("file=") ||
            lower.contains("alert=")
        if (hasSpecificParam) return false

        if (lower.contains("type=778")) return true
        val path = lower.substringBefore('?').substringBefore('#').trimEnd('/')
        return path.endsWith("/infos-trafic") ||
            path.endsWith("/info-trafic") ||
            path.endsWith("/perturbations") ||
            path.endsWith("/deviations")
    }

    private fun scorePlanCandidate(anchorText: String, url: String, isImg: Boolean = false): Int {
        val lowerUrl = url.lowercase(Locale.ROOT)
        val lowerText = Normalizer.normalize(anchorText.lowercase(Locale.FRANCE), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val isGeneric = isGenericTrafficOverviewUrl(url)
        val isFileadminDoc = lowerUrl.contains("/fileadmin/") &&
            !lowerUrl.contains("/pictos/") &&
            !lowerUrl.contains("picto")
        val hasPlanKeyword = lowerText.contains("plan") ||
            lowerText.contains("carte") ||
            lowerText.contains("schema") ||
            lowerText.contains("telecharger") ||
            lowerText.contains("pdf") ||
            lowerUrl.contains("plan") ||
            lowerUrl.contains("carte") ||
            lowerUrl.contains("schema")

        return when {
            isDirectDocumentOrPlanUrl(url) && !isImg -> 150 + if (hasPlanKeyword) 20 else 0
            isFileadminDoc && !isImg -> 130
            isDirectDocumentOrPlanUrl(url) && isImg && hasPlanKeyword -> 115
            hasPlanKeyword && !isGeneric && !isImg -> 95
            (lowerUrl.contains("tx_deviation") || lowerUrl.contains("deviation=") || lowerUrl.contains("tx_")) && !isImg -> 80
            (lowerUrl.contains("deviation") || lowerUrl.contains("fiche")) && !isGeneric && !isImg -> 65
            !isGeneric && !isImg -> 25
            !isImg -> 5
            else -> 0
        }
    }

    private fun extractBestPlanCandidateInElement(container: Element): Pair<String, String>? {
        var bestScore = 0
        var bestPair: Pair<String, String>? = null

        for (anchor in container.select("a[href]")) {
            val href = anchor.attr("href").trim()
            if (href.startsWith("mailto:", true) || href.startsWith("tel:", true)) continue
            val resolved = resolveIdelisUrl(href) ?: continue
            val text = (anchor.text() + " " + anchor.attr("title")).trim()
            val score = scorePlanCandidate(text, resolved, isImg = false)
            if (score > bestScore) {
                bestScore = score
                bestPair = resolved to anchor.text().trim()
            }
        }

        if (bestScore < 95) {
            for (img in container.select("img[src]")) {
                val src = resolveIdelisUrl(img.attr("src")) ?: continue
                val alt = img.attr("alt").trim()
                val score = scorePlanCandidate(alt, src, isImg = true)
                if (score > bestScore) {
                    bestScore = score
                    bestPair = src to alt
                }
            }
        }
        return bestPair
    }

    private fun normalizeForMatch(text: String): String {
        return Normalizer.normalize(text.lowercase(Locale.FRANCE), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Lorsque l'alerte pointe vers une page d'ensemble des déviations (ex: /?type=778 ou /infos-trafic),
     * tente de retrouver dans le HTML de cette page le lien direct vers le plan (PDF / image) correspondant à l'alerte.
     */
    internal fun resolveDirectPlanUrlFromOverviewHtml(
        overviewHtml: String,
        alertTitle: String,
        alertDescription: String,
        affectedLines: List<String>
    ): String? {
        if (overviewHtml.isBlank()) return null
        val doc = Jsoup.parse(overviewHtml, IDELIS_HOST)
        val normTitle = normalizeForMatch(alertTitle)
        val titleTokens = normTitle.split(" ").filter { it.length >= 4 }.toSet()
        val descTokens = normalizeForMatch(alertDescription).split(" ").filter { it.length >= 5 }.toSet()
        val upperLines = affectedLines.map { it.uppercase(Locale.ROOT) }.toSet()

        val candidates = doc.select(
            "article, .alert-item, .deviation-item, .infotrafic-item, .perturbation-item, .card, .accordion-item, section, li, div:has(> a[href]), div:has(> p):has(a[href])"
        )

        var bestMatchScore = 0
        var bestUrl: String? = null

        for (block in candidates) {
            // Ignorer les conteneurs trop globaux (qui englobent toute la page)
            val blockText = block.text()
            if (blockText.length > 2500) continue

            val candidate = extractBestPlanCandidateInElement(block) ?: continue
            val url = candidate.first
            val linkScore = scorePlanCandidate(candidate.second, url)
            if (linkScore < 65) continue

            val normBlock = normalizeForMatch(blockText)
            var matchScore = 0
            if (normTitle.length >= 8 && normBlock.contains(normTitle)) {
                matchScore += 100
            } else if (titleTokens.isNotEmpty()) {
                val hits = titleTokens.count { normBlock.contains(it) }
                matchScore += (hits * 60) / titleTokens.size
            }
            if (descTokens.isNotEmpty()) {
                val descHits = descTokens.count { normBlock.contains(it) }
                matchScore += minOf(30, descHits * 8)
            }
            if (upperLines.isNotEmpty()) {
                val upperBlock = blockText.uppercase(Locale.ROOT)
                val lineHits = upperLines.count { line ->
                    Regex("""\b${Regex.escape(line)}\b""").containsMatchIn(upperBlock)
                }
                matchScore += lineHits * 15
            }

            // Exiger un minimum de correspondance sur le sujet pour ne pas associer le plan d'une autre déviation
            if (matchScore >= 35) {
                val totalScore = matchScore * 10 + linkScore
                if (totalScore > bestMatchScore) {
                    bestMatchScore = totalScore
                    bestUrl = url
                }
            }
        }

        return bestUrl
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

            val nowCal = Calendar.getInstance(GtfsScheduleIndex.PARIS_TZ)
            val parsedRecent = parseInfosTraficHtmlInternal(html).filter { (alert, rawDateAttr) ->
                isAlertWithinOneMonth(
                    title = alert.title,
                    description = alert.description,
                    publishedDate = alert.publishedDate,
                    rawDateAttr = rawDateAttr,
                    nowCal = nowCal
                )
            }

            val pageCache = mutableMapOf<String, String>()
            fun fetchHtmlOnce(targetUrl: String): String {
                pageCache[targetUrl]?.let { return it }
                return try {
                    val getReq = Request.Builder()
                        .url(targetUrl)
                        .get()
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", IDELIS_REFERER)
                        .header("Accept", "text/html,application/xhtml+xml,*/*")
                        .build()
                    val body = client.newCall(getReq).execute().use { res ->
                        val contentType = res.header("Content-Type").orEmpty()
                        if (res.isSuccessful && !contentType.contains("pdf", ignoreCase = true) && !contentType.startsWith("image/", ignoreCase = true)) {
                            res.body?.string().orEmpty()
                        } else {
                            ""
                        }
                    }
                    pageCache[targetUrl] = body
                    body
                } catch (_: Exception) {
                    pageCache[targetUrl] = ""
                    ""
                }
            }

            val resolvedAlerts = parsedRecent.map { (alert, _) ->
                val link = alert.deviationLink
                if (link.isNullOrBlank() || isDirectDocumentOrPlanUrl(link)) {
                    alert
                } else {
                    var bestLink: String? = null
                    val firstPageHtml = fetchHtmlOnce(link)
                    if (firstPageHtml.isNotBlank()) {
                        val resolved = resolveDirectPlanUrlFromOverviewHtml(
                            overviewHtml = firstPageHtml,
                            alertTitle = alert.title,
                            alertDescription = alert.description,
                            affectedLines = alert.affectedLines
                        )
                        if (!resolved.isNullOrBlank()) {
                            bestLink = if (!isDirectDocumentOrPlanUrl(resolved) && resolved != link) {
                                val hopHtml = fetchHtmlOnce(resolved)
                                if (hopHtml.isNotBlank()) {
                                    resolveDirectPlanUrlFromOverviewHtml(
                                        overviewHtml = hopHtml,
                                        alertTitle = alert.title,
                                        alertDescription = alert.description,
                                        affectedLines = alert.affectedLines
                                    ) ?: resolved
                                } else {
                                    resolved
                                }
                            } else {
                                resolved
                            }
                        }
                    }
                    val finalLink = bestLink ?: if (isGenericTrafficOverviewUrl(link)) null else link
                    alert.copy(deviationLink = finalLink)
                }
            }

            safeLogD("Info trafic idelis.fr : ${resolvedAlerts.size} alerte(s) récente(s) extraite(s)")
            Result.success(resolvedAlerts)
        } catch (e: Exception) {
            safeLogE("Erreur infostrafic: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Récupère la période de circulation active directement depuis la page d'accueil officielle d'idelis.fr.
     * Renvoie null si le site est inaccessible ou si la période active n'est pas clairement identifiable.
     */
    suspend fun fetchCurrentPeriodFromWeb(): IdelisPeriod? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(IDELIS_REFERER)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string().orEmpty()
            }
            if (html.isBlank()) return@withContext null
            val period = parseCurrentPeriodFromHtml(html)
            if (period != null) {
                safeLogD("Période officielle détectée depuis idelis.fr : ${period.label}")
            }
            period
        } catch (e: Exception) {
            safeLogD("Lecture période sur idelis.fr indisponible : ${e.message}")
            null
        }
    }

    internal fun parseCurrentPeriodFromHtml(html: String): IdelisPeriod? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html, IDELIS_HOST)

        fun periodFromKeyword(keyword: String): IdelisPeriod? {
            return when (keyword.lowercase(Locale.FRANCE)) {
                "orange" -> IdelisPeriod.ORANGE
                "bleu", "bleue" -> IdelisPeriod.BLEUE
                "gris", "grise" -> IdelisPeriod.GRISE
                "vert", "verte" -> IdelisPeriod.VERTE
                "blanc", "blanche" -> IdelisPeriod.BLANCHE
                else -> null
            }
        }

        fun detectSinglePeriodInText(raw: String): IdelisPeriod? {
            val fold = normalizeForMatch(raw)
            if (fold.isBlank()) return null
            val matches = Regex("""\b(?:periode\s+)?(orange|bleue?|grise?|verte?|blanche?)\b""")
                .findAll(fold)
                .mapNotNull { periodFromKeyword(it.groupValues[1]) }
                .distinct()
                .toList()
            return if (matches.size == 1) matches.first() else null
        }

        val fullFold = normalizeForMatch(doc.text())
        // 1. Contextual active phrasing ("aujourd hui periode orange", "periode en cours bleue", etc.)
        val contextualPatterns = listOf(
            Regex("""(?:aujourd\s*hui|ce\s*jour|du\s*jour|en\s*cours|actuelle?)[^.]{0,50}?\b(?:periode\s+)?(orange|bleue?|grise?|verte?|blanche?)\b"""),
            Regex("""\bperiode\s+(orange|bleue?|grise?|verte?|blanche?)\b[^.]{0,30}?(?:aujourd\s*hui|en\s*cours|actuelle?)""")
        )
        for (pattern in contextualPatterns) {
            val found = pattern.findAll(fullFold)
                .mapNotNull { periodFromKeyword(it.groupValues[1]) }
                .distinct()
                .toList()
            if (found.size == 1) return found.first()
        }

        // 2. Targeted period containers (excluding multi-color legends)
        val periodElements = doc.select(
            "[class*=periode], [id*=periode], [class*=period], [id*=period], .jour-type, .calendar-today, .today"
        )
        val targetedDetected = mutableListOf<IdelisPeriod>()
        for (el in periodElements) {
            val text = el.text().trim()
            if (text.length > 220) continue
            val single = detectSinglePeriodInText(text)
            if (single != null) {
                targetedDetected.add(single)
            }
        }
        val distinctTargeted = targetedDetected.distinct()
        if (distinctTargeted.size == 1) return distinctTargeted.first()

        // 3. Active images (ex: <img src=".../periode-bleue.png" alt="Période bleue">)
        val activeImgPeriods = doc.select("img[src*='periode' i], img[alt*='periode' i], img[alt*='période' i]")
            .mapNotNull { img ->
                val combined = "${img.attr("alt")} ${img.attr("title")} ${img.attr("src")}"
                detectSinglePeriodInText(combined)
            }
            .distinct()
        if (activeImgPeriods.size == 1) return activeImgPeriods.first()

        // 4. Unambiguous "période <couleur>" mention across body text
        val bodyExplicit = Regex("""\bperiode\s+(orange|bleue?|grise?|verte?|blanche?)\b""")
            .findAll(fullFold)
            .mapNotNull { periodFromKeyword(it.groupValues[1]) }
            .distinct()
            .toList()
        if (bodyExplicit.size == 1) return bodyExplicit.first()

        return null
    }

    /**
     * Parse le HTML retourné par `requete=infostrafic` avec Jsoup.
     * Extrait les conteneurs `.alert-item` (titre, lignes concernées + pictos, description, lien direct vers le plan/déviation).
     */
    fun parseInfosTraficHtml(html: String): List<IdelisWebTrafficAlert> {
        return parseInfosTraficHtmlInternal(html).map { it.alert }
    }

    private fun parseInfosTraficHtmlInternal(html: String): List<ParsedWebTrafficAlert> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, IDELIS_HOST)

        // Priorité aux conteneurs .alert-item ; repli si la structure utilise une variante proche
        var items = doc.select(".alert-item")
        if (items.isEmpty()) {
            items = doc.select(".infotrafic-item, .perturbation-item, .trafic-item, div:has(> a[href*='type=778'])")
        }

        val alerts = mutableListOf<ParsedWebTrafficAlert>()
        items.forEachIndexed { index, item ->
            // 1. Lien prioritaire vers le plan (PDF / image / fiche) plutôt que la page globale d'infos trafic
            val bestLinkPair = extractBestPlanCandidateInElement(item)
            val deviationLink = bestLinkPair?.first
            val anchorText = bestLinkPair?.second.orEmpty()

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

            // 4. Date / Période éventuelle (sans faux texte "En cours • idelis.fr")
            val dateEl = item.selectFirst(".alert-date, .date, time, .text-muted, small")
            val rawDateText = dateEl?.text()?.trim().orEmpty()
            val rawDateAttr = item.selectFirst("time[datetime]")?.attr("datetime")?.trim()
                ?: dateEl?.attr("datetime")?.trim().orEmpty()
            val cleanedDateText = cleanPublishedDate(rawDateText)

            // 5. Description détaillée
            val descElements = item.select(".alert-description, .alert-body, .alert-text, .description, p")
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it != rawTitle && it != anchorText && it != rawDateText }
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
                    ParsedWebTrafficAlert(
                        alert = IdelisWebTrafficAlert(
                            id = "infotrafic_${index}_${title.hashCode()}",
                            title = title,
                            description = description,
                            affectedLines = lineRefs,
                            affectedLinePictos = pictos,
                            deviationLink = deviationLink,
                            publishedDate = cleanedDateText
                        ),
                        rawDateAttr = rawDateAttr
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

            val quaiLabel = extractQuaiLabel(dep.stopQuai)
            DepartureComparison(
                id = "web_${dep.lineRef}_${dep.direction}_${index}_${dep.timeText}",
                lineRef = dep.lineRef,
                lineName = dep.lineName,
                destinationName = dep.direction + (quaiLabel?.let { " ($it)" } ?: ""),
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
                iconUrl = com.example.data.repository.IdelisRepository.resolveLineIconUrl(dep.lineRef)
                    ?: dep.linePictoUrl
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

    internal fun parseDirectionAndQuai(text: String): Pair<String, String?> {
        // Ex: "Direction: PAU Gare de Pau -  Arrêt: Pl. d'Espagne - Bosquet Quai A - "
        val trimmed = text.trim()
        if (trimmed.isBlank()) return Pair("", null)

        val arretLabelMatch = ARRET_LABEL_REGEX.find(trimmed)
        val dirPart = if (arretLabelMatch != null) {
            trimmed.substring(0, arretLabelMatch.range.first)
        } else {
            trimmed
        }
        val dir = dirPart
            .replace(DIRECTION_PREFIX_REGEX, "")
            .trim(' ', '-', '–', '—')
            .ifBlank { trimmed }

        val rawStop = if (arretLabelMatch != null) {
            trimmed.substring(arretLabelMatch.range.last + 1)
                .trim(' ', '-', '–', '—')
                .takeIf { it.isNotBlank() }
        } else {
            null
        }

        return Pair(dir, rawStop)
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
