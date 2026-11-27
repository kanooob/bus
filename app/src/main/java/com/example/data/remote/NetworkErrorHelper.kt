package com.example.data.remote

import kotlinx.coroutines.CancellationException
import org.json.JSONException
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.util.zip.ZipException
import javax.net.ssl.SSLException

/**
 * Exception HTTP explicite portant le code de statut et un libellé français lisible par l'utilisateur.
 */
class HttpStatusException(
    val code: Int,
    val serverMessage: String = "",
    message: String = NetworkErrorHelper.formatHttpStatus(code)
) : IOException(message)

/**
 * Exception levée lorsque le serveur répond HTTP 200 avec une charge utile vide,
 * bloquée ("ajax only") ou une page HTML d'erreur/redirection inattendue.
 */
class InvalidServerResponseException(
    message: String = "Réponse invalide reçue du serveur Idelis."
) : IOException(message)

object NetworkErrorHelper {

    fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }

    fun formatHttpStatus(code: Int): String {
        return when (code) {
            400 -> "Requête invalide auprès du serveur (HTTP 400)."
            401, 403 -> "Accès refusé par le serveur (HTTP $code)."
            404 -> "Ressource ou service introuvable sur le serveur (HTTP 404)."
            408 -> "Délai d'attente expiré côté serveur (HTTP 408)."
            429 -> "Trop de requêtes envoyées. Patientez quelques instants."
            in 500..599 -> "Serveur temporairement indisponible (HTTP $code). Réessayez dans un instant."
            else -> "Erreur serveur inattendue (HTTP $code)."
        }
    }

    fun formatError(
        error: Throwable?,
        fallback: String = "Impossible de récupérer les données. Vérifiez votre connexion."
    ): String {
        if (error == null) return fallback
        return when (error) {
            is HttpStatusException -> error.message ?: formatHttpStatus(error.code)
            is InvalidServerResponseException -> error.message ?: "Réponse invalide reçue du serveur."
            is UnknownHostException -> "Aucune connexion Internet. Vérifiez votre accès réseau et réessayez."
            is ConnectException -> "Impossible de joindre le serveur Idelis. Réessayez dans un instant."
            is SocketTimeoutException -> "Délai d'attente dépassé. Le serveur met trop de temps à répondre."
            is SSLException -> "Connexion sécurisée impossible (erreur SSL/TLS)."
            is UnknownServiceException -> "Connexion non sécurisée refusée par le système. Vérifiez l'URL du serveur."
            is SecurityException -> "Accès réseau bloqué par les permissions système."
            is ZipException -> "Archive GTFS reçue corrompue ou incomplète. Veuillez réessayer."
            is EOFException, is SocketException ->
                "Connexion interrompue pendant le chargement. Veuillez réessayer."
            is JSONException -> "Réponse mal formée reçue du serveur (JSON invalide)."
            is IOException -> classifyByMessage(error.message)
                ?: "Erreur réseau lors de la communication avec le serveur."
            else -> classifyByMessage(error.message) ?: fallback
        }
    }

    private fun classifyByMessage(rawMessage: String?): String? {
        val msg = rawMessage?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val lower = msg.lowercase()

        val httpMatch = Regex("""\bhttp\s*(\d{3})\b""", RegexOption.IGNORE_CASE).find(msg)
        if (httpMatch != null) {
            val code = httpMatch.groupValues[1].toIntOrNull()
            if (code != null && code >= 400) {
                return formatHttpStatus(code)
            }
        }

        return when {
            lower.contains("unable to resolve host") || lower.contains("failed to connect") ||
                lower.contains("network is unreachable") || lower.contains("no address associated") ->
                "Aucune connexion Internet. Vérifiez votre accès réseau et réessayez."
            lower.contains("timeout") || lower.contains("timed out") ->
                "Délai d'attente dépassé. Le serveur met trop de temps à répondre."
            lower.contains("ssl") || lower.contains("handshake") || lower.contains("certpath") ->
                "Connexion sécurisée impossible (erreur SSL/TLS)."
            lower.contains("eof") || lower.contains("connection reset") || lower.contains("broken pipe") ->
                "Connexion interrompue pendant le chargement. Veuillez réessayer."
            lower.contains("zip") || lower.contains("corrompu") || lower.contains("corrupted") ->
                "Archive GTFS reçue corrompue ou incomplète. Veuillez réessayer."
            lower.contains("ajax only") || lower.contains("réponse vide") || lower.contains("reponse vide") ->
                "Réponse incomplète du serveur Idelis. Veuillez réessayer."
            lower.contains("annulé") || lower.contains("canceled") || lower.contains("cancelled") ->
                "Requête interrompue. Veuillez réessayer."
            // Si le message est déjà rédigé en français lisible, le conserver
            msg.any { it in listOf('é', 'è', 'ê', 'à', 'ù', 'ç', 'ô', 'î') } -> msg
            else -> null
        }
    }
}
