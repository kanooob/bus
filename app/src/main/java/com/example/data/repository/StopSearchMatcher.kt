package com.example.data.repository

import com.example.data.model.IdelisWebStop
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Utilitaire de recherche progressive et de classement par similarité pour associer
 * un nom d'arrêt géolocalisé (OpenData/GTFS) à l'arrêt officiel retourné par l'autocomplétion idelis.fr (?type=777).
 */
object StopSearchMatcher {

    private val STOP_WORDS = setOf(
        "de", "du", "des", "la", "le", "les", "l", "d", "et", "en",
        "a", "au", "aux", "sur", "sous", "par", "pour",
        "quai", "quais", "arret", "pole"
    )

    private val GENERIC_PLACE_WORDS = setOf(
        "rue", "av", "ave", "avenue", "bd", "blvd", "boulevard",
        "pl", "place", "imp", "impasse", "all", "allee",
        "ch", "chemin", "rte", "route", "rond", "point",
        "cours", "parc", "centre", "zi", "zac", "mairie",
        "lycee", "college", "ecole", "stade", "stades", "cite", "residence"
    )

    private val COMMUNE_PREFIX_REGEX = Regex(
        """^(PAU|LESCAR|LONS|BILL[ÈE]RE|BIZANOS|JURAN[ÇC]ON|SERRES[- ]CASTET|ARTIGUELOUTAN|MAZ[ÈE]RES[- ]LEZONS|MAZ[ÈE]RES|MORLA[ÀA]S|IDRON|SENDETS|OUSSE|L[ÉE]E|ARESSY|GELOS|GAN|LAROIN|POEY[- ]DE[- ]LESCAR|SAUVAGNON|MONTARDON|BOUILLON|UZOS|RONTIGNON|NARCASTET|MEILLON|ASSAT|BORDES|NOUSTY|ARTIX|SOUMOULOU|BOSDARROS)\s*[-–]?\s+""",
        RegexOption.IGNORE_CASE
    )

    private val QUAI_SUFFIX_REGEX = Regex(
        """(?i)\s*[-–]?\s*\bquai[s]?\s+[a-z0-9]+\b|\s*[-–]?\s*\bquai[s]?\b"""
    )

    fun normalizeForComparison(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
        return withoutAccents
            .lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
    }

    fun stripQuai(raw: String): String {
        return raw.replace(QUAI_SUFFIX_REGEX, " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '–', ',', ':')
    }

    fun stripLeadingCommune(raw: String): String {
        val stripped = raw.replace(COMMUNE_PREFIX_REGEX, "").trim(' ', '-', '–', ',', ':')
        return stripped.ifBlank { raw.trim() }
    }

    /**
     * Génère une séquence ordonnée de requêtes de recherche, exactement comme une recherche manuelle :
     * 1. Le nom complet de l'arrêt
     * 2. Le nom nettoyé (sans quai / sans commune)
     * 3. Des sous-requêtes à quelques mots (2 mots puis mots distinctifs) en cas d'absence de résultat
     */
    fun buildProgressiveSearchQueries(
        rawStopName: String,
        fallbackPole: String? = null
    ): List<String> {
        val rawTrimmed = rawStopName.trim()
        if (rawTrimmed.isBlank()) return emptyList()

        val ordered = LinkedHashMap<String, String>()
        fun addQuery(q: String?) {
            val clean = q?.replace(Regex("""\s+"""), " ")?.trim(' ', '-', '–', ',', ':', '.')
            if (!clean.isNullOrBlank() && clean.length >= 2) {
                val key = normalizeForComparison(clean)
                if (key.isNotBlank() && !ordered.containsKey(key)) {
                    ordered[key] = clean
                }
            }
        }

        val withoutQuai = stripQuai(rawTrimmed)
        val withoutCommuneAndQuai = stripLeadingCommune(withoutQuai)

        // 1. Recherche directe (même logique que la saisie manuelle)
        addQuery(rawTrimmed)
        addQuery(withoutQuai)
        addQuery(withoutCommuneAndQuai)
        addQuery(fallbackPole)

        // 2. Extraction de mots significatifs pour le repli "quelques mots"
        val baseForWords = withoutCommuneAndQuai.ifBlank { withoutQuai }.ifBlank { rawTrimmed }
        val rawWords = baseForWords
            .split(Regex("""[\s\-–/'’(),.:;]+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val meaningfulWords = rawWords.filter { word ->
            val norm = normalizeForComparison(word)
            norm.length >= 2 && norm !in STOP_WORDS
        }

        val distinctiveWords = meaningfulWords.filter { word ->
            normalizeForComparison(word) !in GENERIC_PLACE_WORDS
        }

        // Combinaisons de 2 mots
        if (distinctiveWords.size >= 2) {
            addQuery("${distinctiveWords[0]} ${distinctiveWords[1]}")
            if (distinctiveWords.size > 2) {
                addQuery("${distinctiveWords[distinctiveWords.size - 2]} ${distinctiveWords.last()}")
            }
        }
        if (meaningfulWords.size >= 2) {
            addQuery("${meaningfulWords[0]} ${meaningfulWords[1]}")
            if (meaningfulWords.size > 2) {
                addQuery("${meaningfulWords[meaningfulWords.size - 2]} ${meaningfulWords.last()}")
            }
        }

        // Repli mot unique le plus distinctif (plus longs mots d'abord)
        val singleCandidates = (distinctiveWords.ifEmpty { meaningfulWords })
            .sortedByDescending { normalizeForComparison(it).length }
        for (word in singleCandidates) {
            if (normalizeForComparison(word).length >= 3) {
                addQuery(word)
            }
        }
        if (ordered.size <= 1) {
            for (word in singleCandidates) {
                addQuery(word)
            }
        }

        return ordered.values.take(8).toList()
    }

    /**
     * Choisit l'arrêt web le plus ressemblant au nom d'arrêt cible.
     */
    fun selectMostSimilarWebStop(
        targetStopName: String,
        candidates: List<IdelisWebStop>
    ): IdelisWebStop? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        return candidates.maxByOrNull { candidate ->
            computeStopSimilarityScore(targetStopName, candidate)
        }
    }

    fun computeStopSimilarityScore(
        targetRaw: String,
        candidate: IdelisWebStop
    ): Double {
        return max(
            scorePair(targetRaw, candidate.name),
            scorePair(targetRaw, candidate.pole)
        )
    }

    private fun scorePair(targetRaw: String, candidateRaw: String): Double {
        val targetFullNorm = normalizeForComparison(targetRaw)
        val candFullNorm = normalizeForComparison(candidateRaw)
        if (targetFullNorm.isBlank() || candFullNorm.isBlank()) return 0.0

        if (targetFullNorm == candFullNorm) return 1.5

        val targetNoQuai = normalizeForComparison(stripQuai(targetRaw))
        val candNoQuai = normalizeForComparison(stripQuai(candidateRaw))
        if (targetNoQuai.isNotBlank() && targetNoQuai == candNoQuai) return 1.4

        val targetNoCity = normalizeForComparison(stripLeadingCommune(stripQuai(targetRaw)))
        val candNoCity = normalizeForComparison(stripLeadingCommune(stripQuai(candidateRaw)))

        // Score par recoupement de mots significatifs (F1)
        val targetTokens = tokenizeMeaningful(targetNoQuai)
        val candTokens = tokenizeMeaningful(candNoQuai)

        val tokenF1 = if (targetTokens.isNotEmpty() && candTokens.isNotEmpty()) {
            val matchedTarget = targetTokens.count { t -> candTokens.any { c -> tokensMatch(t, c) } }
            val matchedCand = candTokens.count { c -> targetTokens.any { t -> tokensMatch(t, c) } }
            val precision = matchedCand.toDouble() / candTokens.size
            val recall = matchedTarget.toDouble() / targetTokens.size
            if (precision + recall > 0.0) (2.0 * precision * recall) / (precision + recall) else 0.0
        } else {
            0.0
        }

        // Similarité de Levenshtein normalisée (comparaison complète et sans commune)
        val charSimFull = normalizedLevenshtein(targetNoQuai, candNoQuai)
        val charSimNoCity = if (targetNoCity.isNotBlank() && candNoCity.isNotBlank()) {
            normalizedLevenshtein(targetNoCity, candNoCity)
        } else 0.0
        val bestCharSim = max(charSimFull, charSimNoCity)

        var bonus = 0.0
        if (targetNoCity.isNotBlank() && targetNoCity == candNoCity) {
            bonus += 0.20
        } else if (targetNoCity.isNotBlank() && candNoCity.isNotBlank() &&
            (candNoCity.contains(targetNoCity) || targetNoCity.contains(candNoCity))
        ) {
            bonus += 0.08
        }

        // Bonus léger si le même quai est mentionné des deux côtés
        val targetQuai = Regex("""\bquai\s+([a-z0-9]+)\b""").find(targetFullNorm)?.groupValues?.getOrNull(1)
        val candQuai = Regex("""\bquai\s+([a-z0-9]+)\b""").find(candFullNorm)?.groupValues?.getOrNull(1)
        if (targetQuai != null && targetQuai == candQuai) {
            bonus += 0.05
        }

        return 0.60 * tokenF1 + 0.40 * bestCharSim + bonus
    }

    private fun tokenizeMeaningful(normalizedText: String): List<String> {
        val all = normalizedText.split(" ").filter { it.isNotBlank() }
        val filtered = all.filter { it !in STOP_WORDS }
        return filtered.ifEmpty { all }
    }

    private fun tokensMatch(a: String, b: String): Boolean {
        if (a == b) return true
        // Abréviations courantes (ex: "f" pour "francois", "av" pour "avenue", "pl" pour "place", "bd" pour "boulevard")
        if (a.length == 1 && b.startsWith(a)) return true
        if (b.length == 1 && a.startsWith(b)) return true
        if (a.length >= 2 && b.length >= 2 && (a.startsWith(b) || b.startsWith(a)) &&
            min(a.length, b.length).toDouble() / max(a.length, b.length) >= 0.45
        ) {
            return true
        }
        val maxLen = max(a.length, b.length)
        if (maxLen >= 4) {
            val dist = levenshteinDistance(a, b)
            return dist <= 1 || (maxLen >= 7 && dist <= 2)
        }
        return false
    }

    private fun normalizedLevenshtein(a: String, b: String): Double {
        if (a.isBlank() && b.isBlank()) return 1.0
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 0.0
        val dist = levenshteinDistance(a, b)
        return (1.0 - dist.toDouble() / maxLen.toDouble()).coerceIn(0.0, 1.0)
    }

    fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            val ca = a[i - 1]
            for (j in 1..b.length) {
                val cost = if (ca == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
