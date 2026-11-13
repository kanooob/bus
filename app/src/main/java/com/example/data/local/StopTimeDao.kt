package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.GtfsStopTime
import com.example.data.model.GtfsTheoreticalDeparture
import com.example.data.model.GtfsTrip

@Dao
interface StopTimeDao {
    @Query("""
        SELECT 
            s.stopId AS stopId,
            s.stopName AS stopName,
            r.routeId AS routeId,
            r.routeShortName AS routeShortName,
            r.routeLongName AS routeLongName,
            r.routeColor AS routeColor,
            r.routeTextColor AS routeTextColor,
            t.tripHeadsign AS tripHeadsign,
            st.departureTime AS departureTime
        FROM stop_times st
        INNER JOIN trips t ON st.tripId = t.tripId
        INNER JOIN routes r ON t.routeId = r.routeId
        INNER JOIN stops s ON st.stopId = s.stopId
        WHERE st.stopId = :stopId 
          AND st.departureTime >= :fromTime
        ORDER BY st.departureTime ASC
        LIMIT :limit
    """)
    suspend fun getUpcomingDepartures(
        stopId: String,
        fromTime: String,
        limit: Int = 15
    ): List<GtfsTheoreticalDeparture>

    @Query("""
        SELECT 
            s.stopId AS stopId,
            s.stopName AS stopName,
            r.routeId AS routeId,
            r.routeShortName AS routeShortName,
            r.routeLongName AS routeLongName,
            r.routeColor AS routeColor,
            r.routeTextColor AS routeTextColor,
            t.tripHeadsign AS tripHeadsign,
            st.departureTime AS departureTime
        FROM stop_times st
        INNER JOIN trips t ON st.tripId = t.tripId
        INNER JOIN routes r ON t.routeId = r.routeId
        INNER JOIN stops s ON st.stopId = s.stopId
        WHERE st.stopId = :stopId
        ORDER BY st.departureTime ASC
        LIMIT :limit
    """)
    suspend fun getAllDeparturesForStop(
        stopId: String,
        limit: Int = 20
    ): List<GtfsTheoreticalDeparture>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<GtfsTrip>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTimes(stopTimes: List<GtfsStopTime>)

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun getTripCount(): Int

    @Query("SELECT COUNT(*) FROM stop_times")
    suspend fun getStopTimeCount(): Int

    @Query("DELETE FROM trips")
    suspend fun clearAllTrips()

    @Query("DELETE FROM stop_times")
    suspend fun clearAllStopTimes()
}
