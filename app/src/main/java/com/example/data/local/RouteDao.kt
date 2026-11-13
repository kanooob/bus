package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.GtfsRoute
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY routeShortName ASC")
    fun getAllRoutes(): Flow<List<GtfsRoute>>

    @Query("SELECT * FROM routes WHERE routeId = :routeId OR routeShortName = :routeId LIMIT 1")
    suspend fun getRouteById(routeId: String): GtfsRoute?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<GtfsRoute>)

    @Query("SELECT COUNT(*) FROM routes")
    suspend fun getRouteCount(): Int

    @Query("DELETE FROM routes")
    suspend fun clearAllRoutes()
}
