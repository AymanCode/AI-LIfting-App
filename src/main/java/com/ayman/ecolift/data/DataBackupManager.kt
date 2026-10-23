package com.ayman.ecolift.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.ayman.ecolift.agent.model.AgentTurnLog
import com.ayman.ecolift.agent.model.AuditEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import com.ayman.ecolift.data.room.ArchivedCycle as RoomArchivedCycle
import com.ayman.ecolift.data.room.CardioSession as RoomCardioSession
import com.ayman.ecolift.data.room.Cycle as RoomCycle
import com.ayman.ecolift.data.room.CycleSlot as RoomCycleSlot
import com.ayman.ecolift.data.room.Exercise as RoomExercise
import com.ayman.ecolift.data.room.ExerciseAlias as RoomExerciseAlias
import com.ayman.ecolift.data.room.ExerciseDistinctPair as RoomExerciseDistinctPair
import com.ayman.ecolift.data.room.ExerciseSwapReceiptRow as RoomExerciseSwapReceipt
import com.ayman.ecolift.data.room.PendingReview as RoomPendingReview
import com.ayman.ecolift.data.room.SplitAlternativeRow as RoomSplitAlternative
import com.ayman.ecolift.data.room.SplitExercise as RoomSplitExercise
import com.ayman.ecolift.data.room.SwapAlternativeQueueRow as RoomSwapAlternativeQueue
import com.ayman.ecolift.data.room.TempSessionSwap as RoomTempSessionSwap
import com.ayman.ecolift.data.room.UserSettings as RoomUserSettings
import com.ayman.ecolift.data.room.WorkoutDay as RoomWorkoutDay
import com.ayman.ecolift.data.room.WorkoutDayState as RoomWorkoutDayState
import com.ayman.ecolift.data.room.WorkoutExerciseCard as RoomWorkoutExerciseCard
import com.ayman.ecolift.data.room.WorkoutSet as RoomWorkoutSet

@Serializable
data class BackupMetadata(
    val formatVersion: Int = 1,
    val createdAtEpochMs: Long,
    val source: String,
    val appDbVersion: Int,
)

@Serializable
data class UserDataBackup(
    val metadata: BackupMetadata,
    val cycle: RoomCycle?,
    val exercises: List<RoomExercise>,
    val workoutDays: List<RoomWorkoutDay>,
    val workoutSets: List<RoomWorkoutSet>,
    val pendingReviews: List<RoomPendingReview>,
    val cycleSlots: List<RoomCycleSlot>,
    val splitExercises: List<RoomSplitExercise>,
    val tempSessionSwaps: List<RoomTempSessionSwap>,
    val auditEntries: List<AuditEntity> = emptyList(),
    val agentTurns: List<AgentTurnLog> = emptyList(),
    val archivedCycles: List<RoomArchivedCycle> = emptyList(),
    val userSettings: RoomUserSettings? = null,
    val cardioSessions: List<RoomCardioSession> = emptyList(),
    val workoutExerciseCards: List<RoomWorkoutExerciseCard> = emptyList(),
    val exerciseAliases: List<RoomExerciseAlias> = emptyList(),
    val exerciseDistinctPairs: List<RoomExerciseDistinctPair> = emptyList(),
    val splitAlternatives: List<RoomSplitAlternative> = emptyList(),
    val exerciseSwapReceipts: List<RoomExerciseSwapReceipt> = emptyList(),
    val workoutDayStates: List<RoomWorkoutDayState> = emptyList(),
    val swapAlternativeQueue: List<RoomSwapAlternativeQueue> = emptyList(),
)

data class LocalBackupInfo(
    val fileName: String,
    val createdAtEpochMs: Long,
    val sizeBytes: Long,
)

data class BackupResult(
    val entryCount: Int,
    val createdAtEpochMs: Long,
)

object DataBackupManager {
    private const val DB_NAME = "ecolift.db"
    private const val AUTO_BACKUP_RETENTION = 8
    private const val PREFLIGHT_RETENTION = 3
    private const val AUTO_BACKUP_MIN_INTERVAL_MS = 12L * 60L * 60L * 1000L

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val timestampFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
    private val autoBackupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val autoBackupScheduled = AtomicBoolean(false)

    suspend fun exportToUri(context: Context, db: AppDatabase, uri: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            val snapshot = buildSnapshot(db, source = "manual-export")
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(json.encodeToString(snapshot))
            } ?: throw IOException("Unable to open export destination.")
            snapshot.toResult()
        }

    suspend fun importFromUri(context: Context, db: AppDatabase, uri: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            createAutomaticBackup(context, db, reason = "pre-import", force = true)
            val snapshot = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                json.decodeFromString<UserDataBackup>(reader.readText())
            } ?: throw IOException("Unable to read backup file.")
            restoreSnapshot(db, snapshot)
            snapshot.toResult()
        }

    suspend fun createAutomaticBackup(
        context: Context,
        db: AppDatabase,
        reason: String,
        force: Boolean = false,
    ): LocalBackupInfo? = withContext(Dispatchers.IO) {
        val existing = listAutomaticBackups(context)
        val latest = existing.firstOrNull()
        val now = System.currentTimeMillis()
        if (!force && latest != null && now - latest.createdAtEpochMs < AUTO_BACKUP_MIN_INTERVAL_MS) {
            return@withContext null
        }

        val snapshot = buildSnapshot(db, source = "automatic-$reason")
        val directory = automaticBackupDirectory(context).apply { mkdirs() }
        val fileName = "ecolift-auto-${timestampFormatter.format(Instant.ofEpochMilli(snapshot.metadata.createdAtEpochMs))}.json"
        val file = File(directory, fileName)
        file.writeText(json.encodeToString(snapshot))
        trimFiles(directory, AUTO_BACKUP_RETENTION)
        LocalBackupInfo(
            fileName = file.name,
            createdAtEpochMs = file.lastModified(),
            sizeBytes = file.length(),
        )
    }

    suspend fun restoreAutomaticBackup(
        context: Context,
        db: AppDatabase,
        fileName: String,
    ): BackupResult = withContext(Dispatchers.IO) {
        createAutomaticBackup(context, db, reason = "pre-restore", force = true)
        val file = File(automaticBackupDirectory(context), fileName)
        if (!file.exists()) {
            throw IOException("Backup file not found: $fileName")
        }
        val snapshot = json.decodeFromString<UserDataBackup>(file.readText())
        restoreSnapshot(db, snapshot)
        snapshot.toResult()
    }

    fun listAutomaticBackups(context: Context): List<LocalBackupInfo> {
        val directory = automaticBackupDirectory(context)
        if (!directory.exists()) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .sortedByDescending(File::lastModified)
            .map { file ->
                LocalBackupInfo(
                    fileName = file.name,
                    createdAtEpochMs = file.lastModified(),
                    sizeBytes = file.length(),
                )
            }
    }

    fun snapshotExistingDatabaseFiles(context: Context) {
        val database = context.getDatabasePath(DB_NAME)
        if (!database.exists()) return

        val token = "${database.length()}-${database.lastModified()}"
        val root = preflightBackupDirectory(context).apply { mkdirs() }
        val targetDir = File(root, token)
        if (targetDir.exists()) return

        targetDir.mkdirs()
        copyIfExists(database, File(targetDir, database.name))
        copyIfExists(File(database.parentFile, "$DB_NAME-wal"), File(targetDir, "$DB_NAME-wal"))
        copyIfExists(File(database.parentFile, "$DB_NAME-shm"), File(targetDir, "$DB_NAME-shm"))
        trimDirectories(root, PREFLIGHT_RETENTION)
    }

    fun scheduleAutomaticBackup(context: Context, db: AppDatabase) {
        if (!autoBackupScheduled.compareAndSet(false, true)) return
        autoBackupScope.launch {
            runCatching { createAutomaticBackup(context, db, reason = "startup") }
        }
    }

    fun suggestedExportFileName(): String {
        val now = timestampFormatter.format(Instant.now())
        return "ecolift-backup-$now.json"
    }

    private suspend fun buildSnapshot(db: AppDatabase, source: String): UserDataBackup =
        db.withTransaction {
            val exercises = db.exerciseDao().getAll()
            val workoutDays = db.workoutDayDao().getAll()
            val workoutSets = db.workoutSetDao().getAll()
            val pendingReviews = db.pendingReviewDao().getAll()
            val cycleSlots = db.cycleSlotDao().getAll()
            val splitExercises = db.splitExerciseDao().getAll()
            val tempSessionSwaps = db.tempSessionSwapDao().getAll()
            val auditEntries = db.auditDao().getAll()
            val agentTurns = db.agentTurnLogDao().getAll()
            val archivedCycles = db.archivedCycleDao().getAll()
            val userSettings = db.userSettingsDao().get()
            val cardioSessions = db.cardioSessionDao().getAll()
            val workoutExerciseCards = db.workoutExerciseCardDao().getAll()
            val exerciseAliases = db.exerciseAliasDao().getAll()
            val exerciseDistinctPairs = db.exerciseDistinctPairDao().getAll()
            val splitAlternatives = db.splitAlternativeDao().getAll()
            val exerciseSwapReceipts = db.exerciseSwapReceiptDao().getAll()
            val workoutDayStates = db.workoutDayStateDao().getAll()
            val swapAlternativeQueue = db.swapAlternativeQueueDao().all()
            val cycle = db.cycleDao().getCycle()
            UserDataBackup(
                metadata = BackupMetadata(
                    createdAtEpochMs = System.currentTimeMillis(),
                    source = source,
                    appDbVersion = APP_DATABASE_VERSION,
                ),
                cycle = cycle,
                exercises = exercises,
                workoutDays = workoutDays,
                workoutSets = workoutSets,
                pendingReviews = pendingReviews,
                cycleSlots = cycleSlots,
                splitExercises = splitExercises,
                tempSessionSwaps = tempSessionSwaps,
                auditEntries = auditEntries,
                agentTurns = agentTurns,
                archivedCycles = archivedCycles,
                userSettings = userSettings,
                cardioSessions = cardioSessions,
                workoutExerciseCards = workoutExerciseCards,
                exerciseAliases = exerciseAliases,
                exerciseDistinctPairs = exerciseDistinctPairs,
                splitAlternatives = splitAlternatives,
                exerciseSwapReceipts = exerciseSwapReceipts,
                workoutDayStates = workoutDayStates,
                swapAlternativeQueue = swapAlternativeQueue,
            )
        }

    private suspend fun restoreSnapshot(db: AppDatabase, snapshot: UserDataBackup) {
        db.withTransaction {
            clearUserTables(db)

            if (snapshot.exercises.isNotEmpty()) db.exerciseDao().insertAll(snapshot.exercises)
            snapshot.cycle?.let { db.cycleDao().upsert(it) }
            if (snapshot.cycleSlots.isNotEmpty()) db.cycleSlotDao().insertAll(snapshot.cycleSlots)
            if (snapshot.workoutDays.isNotEmpty()) db.workoutDayDao().insertAll(snapshot.workoutDays)
            if (snapshot.exerciseAliases.isNotEmpty()) db.exerciseAliasDao().insertAll(snapshot.exerciseAliases)
            if (snapshot.exerciseDistinctPairs.isNotEmpty()) {
                db.exerciseDistinctPairDao().insertAll(snapshot.exerciseDistinctPairs)
            }
            if (snapshot.workoutExerciseCards.isNotEmpty()) {
                db.workoutExerciseCardDao().insertAll(snapshot.workoutExerciseCards)
            }
            if (snapshot.workoutSets.isNotEmpty()) db.workoutSetDao().insertAll(snapshot.workoutSets)
            if (snapshot.pendingReviews.isNotEmpty()) db.pendingReviewDao().insertAll(snapshot.pendingReviews)
            if (snapshot.splitExercises.isNotEmpty()) db.splitExerciseDao().insertAll(snapshot.splitExercises)
            if (snapshot.splitAlternatives.isNotEmpty()) db.splitAlternativeDao().insertAll(snapshot.splitAlternatives)
            if (snapshot.exerciseSwapReceipts.isNotEmpty()) {
                db.exerciseSwapReceiptDao().insertAll(snapshot.exerciseSwapReceipts)
            }
            if (snapshot.workoutDayStates.isNotEmpty()) db.workoutDayStateDao().insertAll(snapshot.workoutDayStates)
            if (snapshot.swapAlternativeQueue.isNotEmpty()) {
                db.swapAlternativeQueueDao().insertAll(snapshot.swapAlternativeQueue)
            }
            if (snapshot.tempSessionSwaps.isNotEmpty()) db.tempSessionSwapDao().insertAll(snapshot.tempSessionSwaps)
            if (snapshot.auditEntries.isNotEmpty()) db.auditDao().insertAll(snapshot.auditEntries)
            if (snapshot.agentTurns.isNotEmpty()) db.agentTurnLogDao().insertAll(snapshot.agentTurns)
            if (snapshot.archivedCycles.isNotEmpty()) db.archivedCycleDao().insertAll(snapshot.archivedCycles)
            snapshot.userSettings?.let { db.userSettingsDao().upsert(it) }
            if (snapshot.cardioSessions.isNotEmpty()) db.cardioSessionDao().insertAll(snapshot.cardioSessions)
            Migrations.backfillLegacyCards(db.openHelper.writableDatabase)
        }
    }

    private fun clearUserTables(db: AppDatabase) {
        val writableDb = db.openHelper.writableDatabase
        writableDb.execSQL("DELETE FROM `split_alternative`")
        writableDb.execSQL("DELETE FROM `exercise_swap_receipt`")
        writableDb.execSQL("DELETE FROM `workout_day_state`")
        writableDb.execSQL("DELETE FROM `swap_alternative_queue`")
        writableDb.execSQL("DELETE FROM `exercise_alias`")
        writableDb.execSQL("DELETE FROM `exercise_distinct_pair`")
        writableDb.execSQL("DELETE FROM `workout_exercise_card`")
        writableDb.execSQL("DELETE FROM `split_exercise`")
        writableDb.execSQL("DELETE FROM `temp_session_swap`")
        writableDb.execSQL("DELETE FROM `workout_set`")
        writableDb.execSQL("DELETE FROM `workout_day`")
        writableDb.execSQL("DELETE FROM `pending_review`")
        writableDb.execSQL("DELETE FROM `cycle_slot`")
        writableDb.execSQL("DELETE FROM `exercise`")
        writableDb.execSQL("DELETE FROM `cycle`")
        writableDb.execSQL("DELETE FROM `audit_log`")
        writableDb.execSQL("DELETE FROM `agent_turn_log`")
        writableDb.execSQL("DELETE FROM `archived_cycle`")
        writableDb.execSQL("DELETE FROM `user_settings`")
        writableDb.execSQL("DELETE FROM `cardio_sessions`")
        writableDb.execSQL("DELETE FROM sqlite_sequence")
    }

    private fun automaticBackupDirectory(context: Context): File =
        File(context.filesDir, "automatic-backups")

    private fun preflightBackupDirectory(context: Context): File =
        File(context.filesDir, "db-preflight-backups")

    private fun copyIfExists(source: File, target: File) {
        if (!source.exists()) return
        source.copyTo(target, overwrite = true)
    }

    private fun trimFiles(directory: File, keep: Int) {
        directory.listFiles()
            .orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(keep)
            .forEach(File::delete)
    }

    private fun trimDirectories(directory: File, keep: Int) {
        directory.listFiles()
            .orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(keep)
            .forEach { file ->
                file.deleteRecursively()
            }
    }

    private fun UserDataBackup.toResult(): BackupResult {
        val entryCount = exercises.size +
            workoutDays.size +
            workoutSets.size +
            pendingReviews.size +
            cycleSlots.size +
            splitExercises.size +
            tempSessionSwaps.size +
            auditEntries.size +
            agentTurns.size +
            archivedCycles.size +
            cardioSessions.size +
            workoutExerciseCards.size +
            exerciseAliases.size +
            exerciseDistinctPairs.size +
            splitAlternatives.size +
            exerciseSwapReceipts.size +
            workoutDayStates.size +
            swapAlternativeQueue.size +
            (if (userSettings != null) 1 else 0) +
            (if (cycle != null) 1 else 0)
        return BackupResult(
            entryCount = entryCount,
            createdAtEpochMs = metadata.createdAtEpochMs,
        )
    }
}
