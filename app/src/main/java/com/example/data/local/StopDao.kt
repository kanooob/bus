package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.GtfsStop
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {
    @Query("SELECT * FROM stops ORDER BY stopName ASC")
    fun getAllStops(): Flow<List<GtfsStop>>

    @Query("SELECT * FROM stops WHERE stopId = :stopId LIMIT 1")
    suspend fun getStopById(stopId: String): GtfsStop?

    @Query("SELECT * FROM stops WHERE stopName LIKE '%' || :query || '%' ORDER BY stopName ASC")
    fun searchStops(query: String): Flow<List<GtfsStop>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<GtfsStop>)

    @Query("SELECT COUNT(*) FROM stops")
    suspend fun getStopCount(): Int

    @Query("SELECT * FROM stops ORDER BY stopName ASC")
    suspend fun getAllStopsList(): List<GtfsStop>

    @Query("DELETE FROM stops WHERE stopId = :stopId")
    suspend fun deleteStopById(stopId: String)

    @Query("DELETE FROM stops")
    suspend fun clearAllStops()
}
