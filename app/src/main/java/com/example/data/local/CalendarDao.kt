package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.GtfsCalendar
import com.example.data.model.GtfsCalendarDate
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar")
    fun getAllCalendars(): Flow<List<GtfsCalendar>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendars(calendars: List<GtfsCalendar>)

    @Query("SELECT COUNT(*) FROM calendar")
    suspend fun getCalendarCount(): Int

    @Query("DELETE FROM calendar")
    suspend fun clearAllCalendars()

    @Query("SELECT * FROM calendar_dates WHERE date = :date")
    suspend fun getExceptionsForDate(date: String): List<GtfsCalendarDate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarDates(dates: List<GtfsCalendarDate>)

    @Query("SELECT COUNT(*) FROM calendar_dates")
    suspend fun getCalendarDatesCount(): Int

    @Query("DELETE FROM calendar_dates")
    suspend fun clearAllCalendarDates()
}
