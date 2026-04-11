package com.ayman.ecolift.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        WorkoutDay::class,
        WorkoutSet::class,
        Cycle::class,
        PendingReview::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDayDao(): WorkoutDayDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun cycleDao(): CycleDao
    abstract fun pendingReviewDao(): PendingReviewDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add restTimeSeconds to workout_set
                db.execSQL("ALTER TABLE workout_set ADD COLUMN restTimeSeconds INTEGER DEFAULT NULL")
                // Add alternativeForDate to workout_day
                db.execSQL("ALTER TABLE workout_day ADD COLUMN alternativeForDate TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ecolift.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
