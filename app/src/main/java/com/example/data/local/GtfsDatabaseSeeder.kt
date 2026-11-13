package com.example.data.local

import com.example.data.model.GtfsRoute
import com.example.data.model.GtfsStop
import com.example.data.model.GtfsStopTime
import com.example.data.model.GtfsTrip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pré-remplissage GTFS du réseau Idelis (Pau Béarn Pyrénées).
 * Données conformes aux jeux officiels :
 * - lignes-du-reseau-idelis-gtfs (couleurs, terminus officiels)
 * - liste-des-arrets-par-ligne-et-par-sens-du-reseau-idelis (lignes et directions exactes par arrêt)
 */
object GtfsDatabaseSeeder {

    val INITIAL_STOPS = listOf(
        GtfsStop("ID_BOSQUET", "Pôle Bosquet", "Pau Centre • Lignes F, T1, T2, T3, COXI, 5, 6, 7, 8, 9", 43.2995, -0.3668),
        GtfsStop("ID_GARE", "Gare de Pau", "Correspondance SNCF • Lignes F, T1, 5, 9", 43.2928, -0.3705),
        GtfsStop("ID_HOPITAL", "Hôpital François Mitterrand", "Pôle Santé • Lignes F, T1", 43.3256, -0.3475),
        GtfsStop("ID_UNIV", "Université", "UPPA Avenue de l'Université • Lignes F, 10, 12", 43.3152, -0.3643),
        GtfsStop("ID_VERDUN", "Place de Verdun", "Place Historique • Lignes T1, T2, COXI, 5", 43.3012, -0.3752),
        GtfsStop("ID_218_RI", "218ème RI", "Commune de Pau • Ligne 7", 43.2970, -0.3540),
        GtfsStop("ID_ABBE_BREMOND", "Abbé Brémond", "Commune de Pau • Lignes 6, T4", 43.3080, -0.3510),
        GtfsStop("ID_LESCAR", "Lescar Soleil", "Centre Commercial • Lignes T1, T3, 7, 13", 43.3241, -0.4285),
        GtfsStop("ID_LONS", "Lons Perlic", "Terminus Ouest • Lignes T2, T4", 43.3187, -0.3982),
        GtfsStop("ID_ESPAGNE", "Place d'Espagne", "Centre-ville • Lignes F, T2, T3", 43.2982, -0.3689),
        GtfsStop("ID_CATHERINE", "Catherine de Bourbon", "P+R Relais Nord • Lignes F, T1", 43.3289, -0.3541),
        GtfsStop("ID_BILLERE", "Mairie de Billère", "Route de Bayonne • Ligne T1", 43.3032, -0.3951),
        GtfsStop("ID_MAZERES", "Mazères-Lezons", "Terminus Sud • Ligne 5", 43.2730, -0.3520),
        GtfsStop("ID_BIZANOS", "Bizanos Laurets", "Terminus Est • Ligne 6", 43.2870, -0.3490),
        GtfsStop("ID_ARTIGUELOUTAN", "Artigueloutan", "Terminus Est • Ligne 7", 43.2710, -0.2520),
        GtfsStop("ID_MORLAAS", "Morlaàs", "Terminus Est • Ligne 9", 43.3440, -0.2610)
    )

    val INITIAL_ROUTES = listOf(
        GtfsRoute("F", "F", "Fébus", "F18700", "121212"),
        GtfsRoute("T1", "T1", "Chronobus T1", "E30613", "FFFFFF"),
        GtfsRoute("T2", "T2", "Chronobus T2", "00943C", "FFFFFF"),
        GtfsRoute("T3", "T3", "Chronobus T3", "4A5DA8", "FFFFFF"),
        GtfsRoute("T4", "T4", "Chronobus T4", "FFCC00", "121212"),
        GtfsRoute("5", "5", "Ligne 5", "F193BD", "FFFFFF"),
        GtfsRoute("6", "6", "Ligne 6", "009BD9", "FFFFFF"),
        GtfsRoute("7", "7", "Ligne 7", "CF007F", "FFFFFF"),
        GtfsRoute("9", "9", "Ligne 9", "312783", "FFFFFF"),
        GtfsRoute("10", "10", "Ligne 10", "8EC89A", "121212"),
        GtfsRoute("12", "12", "Ligne 12", "71B238", "FFFFFF"),
        GtfsRoute("COXI", "COXI", "Coxitis", "F39200", "FFFFFF")
    )

    /**
     * Association exacte des lignes et directions officielles par arrêt initial.
     */
    data class InitialServing(
        val stopId: String,
        val lineRef: String,
        val direction: String,
        val offsetMinutes: Int
    )

    val INITIAL_SERVINGS = listOf(
        // Arrêt 218ème RI (uniquement la Ligne 7 avec ses deux directions officielles)
        InitialServing("ID_218_RI", "7", "ARTIGUELOUTAN Salle des Fêtes", 0),
        InitialServing("ID_218_RI", "7", "LESCAR Soleil", 1),
        InitialServing("ID_218_RI", "7", "ARTIGUELOUTAN Salle des Fêtes", 14),
        InitialServing("ID_218_RI", "7", "LESCAR Soleil", 18),

        // Arrêt Abbé Brémond (uniquement Ligne 6 et T4)
        InitialServing("ID_ABBE_BREMOND", "6", "BIZANOS Laurets", 0),
        InitialServing("ID_ABBE_BREMOND", "T4", "PAU CST Jean Feger", 1),
        InitialServing("ID_ABBE_BREMOND", "6", "BIZANOS Laurets", 12),
        InitialServing("ID_ABBE_BREMOND", "T4", "PAU CST Jean Feger", 16),

        // Arrêt Université
        InitialServing("ID_UNIV", "F", "PAU Hôpital F. Mitterrand", 0),
        InitialServing("ID_UNIV", "F", "PAU Gare de Pau", 1),
        InitialServing("ID_UNIV", "12", "LESCAR Quartier Libre", 4),
        InitialServing("ID_UNIV", "10", "PAU Bosquet", 7),
        InitialServing("ID_UNIV", "F", "PAU Hôpital F. Mitterrand", 10),
        InitialServing("ID_UNIV", "12", "PAU Porte des Pyrénées", 13),

        // Arrêt Gare de Pau
        InitialServing("ID_GARE", "F", "PAU Hôpital F. Mitterrand", 0),
        InitialServing("ID_GARE", "T1", "PAU Cité Multimédia", 1),
        InitialServing("ID_GARE", "5", "MAZERES-LEZONS L'Arriou", 4),
        InitialServing("ID_GARE", "9", "MORLAÀS Communauté de Communes", 7),
        InitialServing("ID_GARE", "F", "PAU Hôpital F. Mitterrand", 9),
        InitialServing("ID_GARE", "9", "PAU Rives du Gave", 12),

        // Arrêt Pôle Bosquet
        InitialServing("ID_BOSQUET", "F", "PAU Hôpital F. Mitterrand", 0),
        InitialServing("ID_BOSQUET", "F", "PAU Gare de Pau", 1),
        InitialServing("ID_BOSQUET", "T1", "PAU Cité Multimédia", 3),
        InitialServing("ID_BOSQUET", "T1", "JURANÇON LP A. Campa", 6),
        InitialServing("ID_BOSQUET", "T2", "PAU Stades du Hameau", 9),
        InitialServing("ID_BOSQUET", "T2", "LONS Perlic", 12),
        InitialServing("ID_BOSQUET", "T3", "PAU CST Jean Feger", 15),
        InitialServing("ID_BOSQUET", "COXI", "Navette Centre Ville Gratuite", 18),

        // Arrêt Hôpital F. Mitterrand
        InitialServing("ID_HOPITAL", "F", "PAU Gare de Pau", 0),
        InitialServing("ID_HOPITAL", "T1", "JURANÇON LP A. Campa", 1),
        InitialServing("ID_HOPITAL", "F", "PAU Gare de Pau", 9),
        InitialServing("ID_HOPITAL", "T1", "JURANÇON LP A. Campa", 15),

        // Arrêt Lescar Soleil
        InitialServing("ID_LESCAR", "T1", "PAU Cité Multimédia", 0),
        InitialServing("ID_LESCAR", "7", "ARTIGUELOUTAN Salle des Fêtes", 1),
        InitialServing("ID_LESCAR", "T3", "PAU CST Jean Feger", 5),
        InitialServing("ID_LESCAR", "T1", "PAU Cité Multimédia", 12),

        // Arrêt Lons Perlic
        InitialServing("ID_LONS", "T2", "PAU Stades du Hameau", 0),
        InitialServing("ID_LONS", "T4", "PAU CST Jean Feger", 1),
        InitialServing("ID_LONS", "T2", "PAU Stades du Hameau", 12),
        InitialServing("ID_LONS", "T4", "PAU CST Jean Feger", 16)
    )

    fun generateInitialTripsAndStopTimes(): Pair<List<GtfsTrip>, List<GtfsStopTime>> {
        val trips = mutableListOf<GtfsTrip>()
        val stopTimes = mutableListOf<GtfsStopTime>()

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)

        var tripCounter = 1
        val hoursToGenerate = (currentHour - 1..currentHour + 4).map { (it + 24) % 24 }.distinct()

        for (hour in hoursToGenerate) {
            for (serving in INITIAL_SERVINGS) {
                val trip = GtfsTrip(
                    tripId = "TRIP_${serving.lineRef}_${tripCounter++}",
                    routeId = serving.lineRef,
                    serviceId = "WEEKDAY",
                    tripHeadsign = serving.direction,
                    directionId = 0
                )
                trips.add(trip)

                val timeCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, serving.offsetMinutes % 60)
                    set(Calendar.SECOND, 0)
                }
                val timeStr = timeFormat.format(timeCal.time)

                stopTimes.add(
                    GtfsStopTime(
                        tripId = trip.tripId,
                        arrivalTime = timeStr,
                        departureTime = timeStr,
                        stopId = serving.stopId,
                        stopSequence = 1
                    )
                )
            }
        }

        return Pair(trips, stopTimes)
    }
}
