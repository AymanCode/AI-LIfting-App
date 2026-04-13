package com.ayman.ecolift.data

import android.content.Context
import androidx.room.AutoMigration
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
        CycleSlot::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDayDao(): WorkoutDayDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun cycleDao(): CycleDao
    abstract fun pendingReviewDao(): PendingReviewDao
    abstract fun cycleSlotDao(): CycleSlotDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cycle_slot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("ALTER TABLE workout_day ADD COLUMN cycleSlotId INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise ADD COLUMN muscleGroups TEXT NOT NULL DEFAULT 'CHEST · TRICEPS'")
            }
        }

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
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
