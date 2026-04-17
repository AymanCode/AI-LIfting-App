package com.ayman.ecolift.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ayman.ecolift.agent.model.AgentTurnLog
import com.ayman.ecolift.agent.model.AgentTurnLogDao
import com.ayman.ecolift.agent.model.AuditEntity
import com.ayman.ecolift.agent.patches.AuditDao

@Database(
    entities = [
        Exercise::class,
        WorkoutDay::class,
        WorkoutSet::class,
        Cycle::class,
        PendingReview::class,
        CycleSlot::class,
        TempSessionSwap::class,
        AuditEntity::class,
        AgentTurnLog::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDayDao(): WorkoutDayDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun cycleDao(): CycleDao
    abstract fun pendingReviewDao(): PendingReviewDao
    abstract fun cycleSlotDao(): CycleSlotDao
    abstract fun tempSessionSwapDao(): TempSessionSwapDao
    abstract fun auditDao(): AuditDao
    abstract fun agentTurnLogDao(): AgentTurnLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ecolift.db"
                )
                    .addMigrations(*Migrations.ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
