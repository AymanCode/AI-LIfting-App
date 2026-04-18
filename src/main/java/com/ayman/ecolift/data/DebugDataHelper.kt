package com.ayman.ecolift.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/**
 * Seeds realistic workout history for UI and AI debugging.
 * ADDITIVE — never deletes existing data. Safe to call multiple times (exercise inserts use IGNORE).
 *
 * Generates ~20 sessions across 90 days with:
 *  - Bench Press: progressive overload trend (good for progress chart)
 *  - Squat: plateau (interesting flat chart)
 *  - Deadlift: strong upward trend (AI progress query)
 *  - Overhead Press: moderate improvement
 *  - Barbell Row: consistent volume
 *  - Pull-ups: bodyweight + added weight progression
 *  - Lateral Raises: high-rep accessory (light weight)
 *
 * Sessions alternate A/B so multiple exercises land on the same date
 * (tests the QueryDate intent: "what did I do on Tuesday?").
 */
object DebugDataHelper {

    suspend fun seed(context: Context) = withContext(Dispatchers.IO) {
        val db  = AppDatabase.getInstance(context)
        val exDao  = db.exerciseDao()
        val setDao = db.workoutSetDao()
        val rng = Random(System.currentTimeMillis())
        val today = LocalDate.now()

        suspend fun getOrCreate(name: String, muscles: String, bw: Boolean): Long {
            return exDao.getByExactName(name)?.id
                ?: exDao.insert(Exercise(name = name, muscleGroups = muscles, isBodyweight = bw))
        }

        val benchId  = getOrCreate("Bench Press",    "CHEST · TRICEPS",       false)
        val squatId  = getOrCreate("Squat",           "QUADS · GLUTES",        false)
        val dlId     = getOrCreate("Deadlift",        "BACK · HAMSTRINGS",     false)
        val ohpId    = getOrCreate("Overhead Press",  "SHOULDERS · TRICEPS",   false)
        val rowId    = getOrCreate("Barbell Row",     "BACK · BICEPS",         false)
        val pullId   = getOrCreate("Pull-ups",        "BACK · BICEPS",         true)
        val latId    = getOrCreate("Lateral Raises",  "SHOULDERS",             false)

        // ── Session schedule ─────────────────────────────────────────────
        // A sessions: Bench, OHP, Lateral Raises (push)
        // B sessions: Squat, Deadlift, Barbell Row (pull/legs)
        // Pull-ups sprinkled on both

        data class SetSpec(val exId: Long, val weight: Int, val reps: Int, val bw: Boolean = false)

        fun sessionA(daysAgo: Long, progress: Float): List<SetSpec> {
            val bench = (135 + (progress * 80).toInt() + rng.nextInt(5)).coerceAtMost(215)
            val ohp   = (75  + (progress * 50).toInt() + rng.nextInt(5)).coerceAtMost(125)
            val lat   = (15  + (progress * 15).toInt() + rng.nextInt(3)).coerceAtMost(30)
            return listOf(
                SetSpec(benchId, bench,    8 + rng.nextInt(3)),
                SetSpec(benchId, bench,    7 + rng.nextInt(3)),
                SetSpec(benchId, bench + 5, 5 + rng.nextInt(2)),
                SetSpec(ohpId,   ohp,      8 + rng.nextInt(3)),
                SetSpec(ohpId,   ohp,      7 + rng.nextInt(2)),
                SetSpec(ohpId,   ohp,      6 + rng.nextInt(2)),
                SetSpec(latId,   lat,      15 + rng.nextInt(5)),
                SetSpec(latId,   lat,      15 + rng.nextInt(5)),
            )
        }

        fun sessionB(daysAgo: Long, progress: Float): List<SetSpec> {
            val squat = (195 + rng.nextInt(15)).coerceAtMost(235) // plateau
            val dl    = (185 + (progress * 100).toInt() + rng.nextInt(10)).coerceAtMost(295)
            val row   = (115 + (progress * 55).toInt() + rng.nextInt(5)).coerceAtMost(175)
            return listOf(
                SetSpec(squatId, squat,    5),
                SetSpec(squatId, squat,    5),
                SetSpec(squatId, squat,    5),
                SetSpec(squatId, squat,    4 + rng.nextInt(2)),
                SetSpec(dlId,    dl,       5),
                SetSpec(dlId,    dl,       5),
                SetSpec(dlId,    dl + 10, 3 + rng.nextInt(2)),
                SetSpec(rowId,   row,      8 + rng.nextInt(3)),
                SetSpec(rowId,   row,      8 + rng.nextInt(3)),
                SetSpec(rowId,   row,      7 + rng.nextInt(2)),
            )
        }

        fun pullSession(progress: Float): List<SetSpec> {
            val added = (progress * 25).toInt()
            return listOf(
                SetSpec(pullId, added,     10 + rng.nextInt(3), bw = true),
                SetSpec(pullId, added,     9  + rng.nextInt(3), bw = true),
                SetSpec(pullId, added,     8  + rng.nextInt(2), bw = true),
            )
        }

        // Alternating session offsets over ~90 days
        // (daysAgo, sessionType: "A" / "B" / "pull")
        val schedule = listOf(
            88L to "A", 85L to "B", 83L to "pull",
            80L to "A", 77L to "B",
            73L to "A", 70L to "B", 68L to "pull",
            64L to "A", 61L to "B",
            57L to "A", 54L to "B", 52L to "pull",
            48L to "A", 45L to "B",
            41L to "A", 38L to "B", 36L to "pull",
            32L to "A", 29L to "B",
            25L to "A", 22L to "B", 20L to "pull",
            16L to "A", 13L to "B",
            9L  to "A", 6L  to "B", 4L to "pull",
            2L  to "A", 0L  to "B",  // today
        )

        for ((daysAgo, type) in schedule) {
            val date = today.minusDays(daysAgo).toString()
            val progress = 1f - (daysAgo / 90f) // 0 = oldest, 1 = today
            val specs = when (type) {
                "A"    -> sessionA(daysAgo, progress)
                "B"    -> sessionB(daysAgo, progress)
                else   -> pullSession(progress)
            }
            specs.forEachIndexed { idx, s ->
                setDao.insert(WorkoutSet(
                    exerciseId = s.exId,
                    date       = date,
                    setNumber  = (idx % 4) + 1,
                    weightLbs  = s.weight,
                    reps       = s.reps,
                    isBodyweight = s.bw,
                    completed  = true
                ))
            }
        }
    }
}
