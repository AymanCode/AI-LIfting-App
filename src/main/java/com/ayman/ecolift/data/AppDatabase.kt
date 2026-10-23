package com.ayman.ecolift.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ayman.ecolift.agent.model.AgentTurnLog
import com.ayman.ecolift.agent.model.AgentTurnLogDao
import com.ayman.ecolift.agent.model.AuditEntity
import com.ayman.ecolift.agent.patches.AuditDao

const val APP_DATABASE_VERSION = 19

@Database(
    entities = [
        com.ayman.ecolift.data.room.Exercise::class,
        com.ayman.ecolift.data.room.WorkoutDay::class,
        com.ayman.ecolift.data.room.WorkoutSet::class,
        com.ayman.ecolift.data.room.Cycle::class,
        com.ayman.ecolift.data.room.PendingReview::class,
        com.ayman.ecolift.data.room.CycleSlot::class,
        com.ayman.ecolift.data.room.SplitExercise::class,
        com.ayman.ecolift.data.room.TempSessionSwap::class,
        AuditEntity::class,
        AgentTurnLog::class,
        com.ayman.ecolift.data.room.ArchivedCycle::class,
        com.ayman.ecolift.data.room.UserSettings::class,
        com.ayman.ecolift.data.room.CardioSession::class,
    ],
    version = APP_DATABASE_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDayDao(): WorkoutDayDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun cycleDao(): CycleDao
    abstract fun pendingReviewDao(): PendingReviewDao
    abstract fun cycleSlotDao(): CycleSlotDao
    abstract fun splitExerciseDao(): SplitExerciseDao
    abstract fun tempSessionSwapDao(): TempSessionSwapDao
    abstract fun auditDao(): AuditDao
    abstract fun agentTurnLogDao(): AgentTurnLogDao
    abstract fun archivedCycleDao(): ArchivedCycleDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun cardioSessionDao(): CardioSessionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                DataBackupManager.snapshotExistingDatabaseFiles(context.applicationContext)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ecolift.db"
                )
                    .createFromAsset("database/ecolift.db")
                    .addMigrations(*Migrations.ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                DataBackupManager.scheduleAutomaticBackup(context.applicationContext, instance)
                instance
            }
        }
    }
}
