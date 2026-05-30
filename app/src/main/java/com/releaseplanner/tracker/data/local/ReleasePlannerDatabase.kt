package com.releaseplanner.tracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ReleaseUpdateEntity::class,
        UserTrackingEntity::class,
        ChangeEventEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(AppConverters::class)
abstract class ReleasePlannerDatabase : RoomDatabase() {
    abstract fun releaseUpdateDao(): ReleaseUpdateDao
    abstract fun trackingDao(): TrackingDao
    abstract fun changeEventDao(): ChangeEventDao

    companion object {
        @Volatile
        private var instance: ReleasePlannerDatabase? = null

        fun get(context: Context): ReleasePlannerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReleasePlannerDatabase::class.java,
                    "release_planner_tracker.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_tracking ADD COLUMN isSkipped INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE release_updates ADD COLUMN earlyAccessDate TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE release_updates ADD COLUMN firstGitHubPushDate TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
