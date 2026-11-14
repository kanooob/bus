package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.GtfsRoute
import com.example.data.model.GtfsStop
import com.example.data.model.GtfsStopTime
import com.example.data.model.GtfsTrip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.example.data.model.GtfsCalendar
import com.example.data.model.GtfsCalendarDate

@Database(
    entities = [
        GtfsStop::class,
        GtfsRoute::class,
        GtfsTrip::class,
        GtfsStopTime::class,
        GtfsCalendar::class,
        GtfsCalendarDate::class
    ],
    version = 3,
    exportSchema = false
)
abstract class IdelisDatabase : RoomDatabase() {

    abstract fun stopDao(): StopDao
    abstract fun routeDao(): RouteDao
    abstract fun stopTimeDao(): StopTimeDao
    abstract fun calendarDao(): CalendarDao

    companion object {
        @Volatile
        private var INSTANCE: IdelisDatabase? = null

        fun getInstance(context: Context): IdelisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IdelisDatabase::class.java,
                    "idelis_gtfs.db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pré-remplir la base avec le GTFS local Idelis
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            seedDatabase(database)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedDatabase(database: IdelisDatabase) {
            // Arrêt par défaut réel (reçu par requête idelis.fr)
            database.stopDao().insertStops(listOf(
                GtfsStop(
                    stopId = "WEB_Bosquet",
                    stopName = "Pôle Bosquet",
                    stopDesc = "Arrêt réseau idelis.fr (Pôle : Bosquet)",
                    parentStation = "Bosquet"
                )
            ))
            database.routeDao().insertRoutes(GtfsDatabaseSeeder.INITIAL_ROUTES)
            val (trips, stopTimes) = GtfsDatabaseSeeder.generateInitialTripsAndStopTimes()
            database.stopTimeDao().insertTrips(trips)
            database.stopTimeDao().insertStopTimes(stopTimes)
        }
    }
}
