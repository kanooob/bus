package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité Room pour les arrêts du réseau Idelis (stops.txt du GTFS).
 */
@Entity(
    tableName = "stops",
    indices = [Index(value = ["stopName"])]
)
data class GtfsStop(
    @PrimaryKey
    val stopId: String,
    val stopName: String,
    val stopDesc: String? = null,
    val stopLat: Double = 0.0,
    val stopLon: Double = 0.0,
    val locationType: Int = 0,
    val parentStation: String? = null,
    val wheelchairBoarding: Int = 1
)

/**
 * Entité Room pour les lignes du réseau Idelis (routes.txt du GTFS).
 * Ex: Fébus (F), Chronobus T1, T2, T3, T4, Ligne 5, etc.
 */
@Entity(tableName = "routes")
data class GtfsRoute(
    @PrimaryKey
    val routeId: String,
    val routeShortName: String,
    val routeLongName: String,
    val routeColor: String, // Hex sans '#' ou avec '#'
    val routeTextColor: String = "FFFFFF",
    val routeType: Int = 3 // 3 = Bus
)

/**
 * Entité Room pour les trajets (trips.txt du GTFS).
 */
@Entity(
    tableName = "trips",
    indices = [Index(value = ["routeId"])]
)
data class GtfsTrip(
    @PrimaryKey
    val tripId: String,
    val routeId: String,
    val serviceId: String,
    val tripHeadsign: String,
    val directionId: Int = 0
)

/**
 * Entité Room pour les horaires de passage théoriques (stop_times.txt du GTFS).
 */
@Entity(
    tableName = "stop_times",
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["stopId"]),
        Index(value = ["departureTime"])
    ]
)
data class GtfsStopTime(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: String,
    val arrivalTime: String, // "HH:mm:ss"
    val departureTime: String, // "HH:mm:ss"
    val stopId: String,
    val stopSequence: Int
)

/**
 * Projection jointe pour récupérer les départs théoriques prévus par le GTFS.
 */
data class GtfsTheoreticalDeparture(
    val stopId: String,
    val stopName: String,
    val routeId: String,
    val routeShortName: String,
    val routeLongName: String,
    val routeColor: String,
    val routeTextColor: String,
    val tripHeadsign: String,
    val departureTime: String // "HH:mm:ss"
)

/**
 * Entité Room pour le calendrier de circulation GTFS (calendar.txt).
 * Définit les jours de validité (lundi à dimanche) et les périodes de début et fin.
 */
@Entity(tableName = "calendar")
data class GtfsCalendar(
    @PrimaryKey
    val serviceId: String,
    val monday: Int,
    val tuesday: Int,
    val wednesday: Int,
    val thursday: Int,
    val friday: Int,
    val saturday: Int,
    val sunday: Int,
    val startDate: String, // "YYYYMMDD"
    val endDate: String    // "YYYYMMDD"
)

/**
 * Entité Room pour les exceptions au calendrier (calendar_dates.txt).
 * 1 = service ajouté, 2 = service supprimé (ex: jour férié ou travaux).
 */
@Entity(
    tableName = "calendar_dates",
    primaryKeys = ["serviceId", "date"]
)
data class GtfsCalendarDate(
    val serviceId: String,
    val date: String, // "YYYYMMDD"
    val exceptionType: Int // 1 = added, 2 = removed
)
