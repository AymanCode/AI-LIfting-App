package com.ayman.ecolift.web.demo

import com.ayman.ecolift.data.ArchivedCycle
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.data.CardioSessionSource
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.CycleSnapshotBuilder
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseMeta
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.plusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class DemoSeedData(
    val anchorDate: LocalDate,
    val exercises: List<Exercise>,
    val sets: List<WorkoutSet>,
    val settings: UserSettings,
    val cycle: Cycle,
    val slots: List<CycleSlot>,
    val splitExercises: List<SplitExercise>,
    val workoutDays: List<WorkoutDay>,
    val archives: List<ArchivedCycle>,
    val cardio: List<CardioSession>,
    val pendingReviews: List<PendingReview>,
    val usageCounts: Map<Long, Int>,
)

object DemoData {
    private const val TOTAL_WEEKS = 39
    private const val HIGHLIGHT_SHOWCASE_WEEKS = 4
    private const val USER_BODYWEIGHT_LBS = 185
    private const val EXERCISE_ID_START = 9_000_001L
    private const val SET_ID_START = 9_100_001L
    private const val SLOT_ID_START = 9_200_001L
    private const val SPLIT_EXERCISE_ID_START = 9_300_001L
    private const val ARCHIVE_ID_START = 9_400_001L
    private const val CARDIO_ID_START = 9_500_001L
    private val HIGHLIGHT_SESSION_REPS = listOf(84, 92, 88, 96, 78, 90, 86, 98)
    private val archiveJson = Json { encodeDefaults = true }

    fun generate(anchorDate: LocalDate = todayLocalDate()): DemoSeedData {
        val exerciseSeeds = exerciseSeeds()
        val exercises = exerciseSeeds.mapIndexed { index, seed ->
            Exercise(
                id = EXERCISE_ID_START + index,
                name = seed.name,
                muscleGroups = seed.muscleGroups,
                isBodyweight = seed.isBodyweight,
                createdAt = stableMillis(anchorDate.minusDays((TOTAL_WEEKS * 7L) + index)),
            )
        }
        val exerciseIdsByName = exercises.associate { it.name to it.id }
        val slots = splitSeeds().mapIndexed { index, seed ->
            CycleSlot(
                id = SLOT_ID_START + index,
                name = seed.name,
                orderIndex = index,
                colorIndex = index,
            )
        }
        val splitExercises = splitSeeds().flatMapIndexed { slotIndex, seed ->
            seed.exerciseNames.mapIndexed { exerciseIndex, name ->
                SplitExercise(
                    id = SPLIT_EXERCISE_ID_START + (slotIndex * 10) + exerciseIndex,
                    splitId = slots[slotIndex].id,
                    exerciseId = requireNotNull(exerciseIdsByName[name]) { "Missing demo exercise: $name" },
                    orderIndex = exerciseIndex,
                )
            }
        }
        val splitRowsBySlot = splitExercises.groupBy { it.splitId }
        val exerciseSpecsById = exerciseSeeds.associateBy { requireNotNull(exerciseIdsByName[it.name]) }
        val currentMonday = anchorDate.minusDays((anchorDate.dayOfWeek.isoDayNumber - 1).toLong())
        val firstMonday = currentMonday.minusDays((TOTAL_WEEKS - 1) * 7L)
        val occurrenceBySlot = mutableMapOf<Long, Int>()
        val workoutDays = mutableListOf<WorkoutDay>()
        val sets = mutableListOf<WorkoutSet>()
        var nextSetId = SET_ID_START

        repeat(TOTAL_WEEKS) { week ->
            splitSeeds().forEachIndexed { splitIndex, split ->
                val isHighlightShowcaseWeek = week >= TOTAL_WEEKS - HIGHLIGHT_SHOWCASE_WEEKS
                // The public demo's latest chapter is intentionally two honest workouts per
                // week. That gives the approved four-band and eight-receipt posters complete,
                // reconcilable source data without fabricating anything in the renderer.
                if (isHighlightShowcaseWeek && splitIndex != 0 && splitIndex != 3) {
                    return@forEachIndexed
                }
                val showcaseDayOffset = if (isHighlightShowcaseWeek && splitIndex == 0) 2L else split.dayOffset
                val date = firstMonday.plusDays((week * 7L) + showcaseDayOffset)
                if (date > anchorDate) return@forEachIndexed
                val slot = slots[splitIndex]
                val occurrence = (occurrenceBySlot[slot.id] ?: 0) + 1
                occurrenceBySlot[slot.id] = occurrence
                workoutDays += WorkoutDay(
                    date = date.toString(),
                    cycleSlotType = splitIndex,
                    cycleSlotOccurrence = occurrence,
                    cycleSlotId = slot.id,
                )
                val rows = splitRowsBySlot.getValue(slot.id).sortedBy { it.orderIndex }
                val showcaseTargetReps = if (isHighlightShowcaseWeek) {
                    val weekOffset = week - (TOTAL_WEEKS - HIGHLIGHT_SHOWCASE_WEEKS)
                    val workoutOffset = if (splitIndex == 0) 0 else 1
                    HIGHLIGHT_SESSION_REPS[(weekOffset * 2) + workoutOffset]
                } else {
                    null
                }
                val activeRows = if (showcaseTargetReps != null) rows.take(3) else rows
                val showcaseReps = showcaseTargetReps?.let {
                    buildHighlightShowcaseReps(
                        totalReps = it,
                        pinCloseGripTopSet = splitIndex == 3,
                    )
                }
                var showcaseSetOffset = 0
                activeRows.forEachIndexed { exerciseIndex, row ->
                    val spec = requireNotNull(exerciseSpecsById[row.exerciseId])
                    val topWeight = topWeightFor(spec, week, anchorDate, date)
                    val reps = repsFor(spec, week, exerciseIndex)
                    val setCount = if (showcaseReps == null || exerciseIndex < 2) 3 else 2
                    repeat(setCount) { setIndex ->
                        sets += WorkoutSet(
                            id = nextSetId++,
                            exerciseId = row.exerciseId,
                            date = date.toString(),
                            setNumber = setIndex + 1,
                            weightLbs = storedWeightFor(spec, topWeight, setIndex),
                            reps = showcaseReps?.get(showcaseSetOffset + setIndex)
                                ?: (reps - setIndex).coerceAtLeast(4),
                            isBodyweight = spec.isBodyweight,
                            completed = true,
                            restTimeSeconds = restFor(spec, setIndex),
                        )
                    }
                    showcaseSetOffset += setCount
                }
            }
        }

        // IronMind's smart-split slip needs a live example in the public demo: a short recurring
        // session that never gets a WorkoutDay slot assignment, so smartSplitDays() picks it up the
        // same way it would pick up an unassigned habit outside the demo. Four full-body sessions
        // land on days the schedule above already leaves free, spaced across the last eight weeks
        // and kept well clear of the last-four-week highlight-reel window so the showcase posters
        // above are untouched. "Face Pull" rides along on two of the four as the occasional
        // accessory lift.
        val smartSplitSlipCoreExercises = listOf(
            "Barbell Bench Press",
            "Barbell Row",
            "Back Squat",
            "Romanian Deadlift",
        )
        val smartSplitSlipSometimesExercise = "Face Pull"
        val takenDates = workoutDays.mapTo(mutableSetOf()) { it.date }
        val smartSplitSlipDates = listOf(33L, 40L, 47L, 54L)
            .map { offset ->
                var candidate = anchorDate.minusDays(offset)
                while (candidate.toString() in takenDates) {
                    candidate = candidate.plusDays(1)
                }
                takenDates += candidate.toString()
                candidate
            }
            .sorted()
        smartSplitSlipDates.forEachIndexed { sessionIndex, date ->
            val dateString = date.toString()
            val exerciseNames = if (sessionIndex % 2 == 1) {
                smartSplitSlipCoreExercises + smartSplitSlipSometimesExercise
            } else {
                smartSplitSlipCoreExercises
            }
            exerciseNames.forEach { name ->
                val exerciseId = requireNotNull(exerciseIdsByName[name]) { "Missing demo exercise: $name" }
                val spec = requireNotNull(exerciseSpecsById[exerciseId])
                val topWeight = requireNotNull(spec.baseWeightLbs) + (sessionIndex * spec.incrementLbs)
                repeat(3) { setIndex ->
                    sets += WorkoutSet(
                        id = nextSetId++,
                        exerciseId = exerciseId,
                        date = dateString,
                        setNumber = setIndex + 1,
                        weightLbs = storedWeightFor(spec, topWeight, setIndex),
                        reps = (spec.targetReps - setIndex).coerceAtLeast(4),
                        isBodyweight = spec.isBodyweight,
                        completed = true,
                        restTimeSeconds = restFor(spec, setIndex),
                    )
                }
            }
        }

        val sortedWorkoutDays = workoutDays.sortedBy { it.date }
        val sortedSets = sets.sortedWith(compareBy<WorkoutSet> { it.date }.thenBy { it.setNumber }.thenBy { it.id })
        val lastSlotId = sortedWorkoutDays.lastOrNull()?.cycleSlotId
        val lastSlotIndex = slots.indexOfFirst { it.id == lastSlotId }.takeIf { it >= 0 } ?: 0
        val archives = buildArchives(
            exercises = exercises,
            sets = sortedSets,
            slots = slots,
            splitExercises = splitExercises,
            workoutDays = sortedWorkoutDays,
        )
        val activeStart = archives.firstOrNull()
            ?.endDate
            ?.let { LocalDate.parse(it).plusDays(1).toString() }
            ?: sortedWorkoutDays.firstOrNull()?.date
        val seed = DemoSeedData(
            anchorDate = anchorDate,
            exercises = exercises.sortedBy { it.name },
            sets = sortedSets,
            settings = UserSettings(
                userBodyweightLbs = USER_BODYWEIGHT_LBS,
                glassPaletteChoice = GlassPaletteChoice.Boreal.name,
            ),
            cycle = Cycle(
                numTypes = slots.size,
                isActive = true,
                nextSessionType = (lastSlotIndex + 1) % slots.size,
                startDate = activeStart,
                name = "Showcase Cycle",
            ),
            slots = slots,
            splitExercises = splitExercises.sortedWith(compareBy({ it.splitId }, { it.orderIndex })),
            workoutDays = sortedWorkoutDays,
            archives = archives,
            cardio = cardioSessions(anchorDate),
            pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(sortedSets),
        )
        // An unchecked practice day lets visitors open the card's taken sheet without editing
        // history or highlight receipts. Its saved group has both chest alternatives and fixed lifts.
        val practiceDate = anchorDate.plusDays(1).toString()
        val practiceSets = splitExercises.filter { it.splitId == slots.first().id && it.orderIndex in setOf(0, 2, 5) }.sortedBy { it.orderIndex }.flatMap { row ->
            SetQueries.buildLastSessionSetCopies(practiceDate, row.exerciseId, sortedSets)
        }.map { it.copy(id = nextSetId++) }
        return seed.copy(
            sets = sortedSets + practiceSets,
            workoutDays = sortedWorkoutDays + WorkoutDay(practiceDate, cycleSlotId = slots.first().id),
            usageCounts = SetQueries.usageCounts(sortedSets + practiceSets),
        )
    }

    private fun buildArchives(
        exercises: List<Exercise>,
        sets: List<WorkoutSet>,
        slots: List<CycleSlot>,
        splitExercises: List<SplitExercise>,
        workoutDays: List<WorkoutDay>,
    ): List<ArchivedCycle> {
        val dates = workoutDays.map { it.date }.distinct().sorted()
        if (dates.size < 12) return emptyList()
        val start = dates.first()
        val end = dates.getOrElse(63) { dates[dates.lastIndex / 2] }
        val exerciseMeta = exercises.associate { it.id to ExerciseMeta(it.name, it.isBodyweight) }
        val snapshot = CycleSnapshotBuilder.build(
            startDate = start,
            endDate = end,
            slots = slots,
            splitExercises = splitExercises,
            workoutDays = workoutDays,
            sets = sets,
            exerciseNames = exerciseMeta,
            userBodyweightLbs = USER_BODYWEIGHT_LBS,
        )
        // A recent sleeve includes the showcase's repeated tracks and the occasional older
        // accessories. It reuses real demo rows so liner-notes QA can open a nonempty fold.
        val linerEnd = dates.last()
        val linerStart = LocalDate.parse(linerEnd).minusDays(34).toString()
        val linerSnapshot = CycleSnapshotBuilder.build(
            startDate = linerStart, endDate = linerEnd, slots = slots,
            splitExercises = splitExercises, workoutDays = workoutDays, sets = sets,
            exerciseNames = exerciseMeta, userBodyweightLbs = USER_BODYWEIGHT_LBS,
        )
        return listOf(
            ArchivedCycle(
                id = ARCHIVE_ID_START,
                name = "Foundation Cycle",
                startDate = start,
                endDate = end,
                splitCount = CycleSnapshotBuilder.splitCount(snapshot),
                totalVolumeLbs = snapshot.totals.totalVolumeLbs,
                totalSessions = snapshot.totals.sessions,
                archivedAt = stableMillis(LocalDate.parse(end).plusDays(1)),
                schemaVersion = snapshot.schemaVersion,
                snapshotJson = archiveJson.encodeToString(snapshot),
            ),
            ArchivedCycle(
                id = ARCHIVE_ID_START + 1, name = "Liner Notes Cycle",
                startDate = linerStart, endDate = linerEnd,
                splitCount = CycleSnapshotBuilder.splitCount(linerSnapshot),
                totalVolumeLbs = linerSnapshot.totals.totalVolumeLbs,
                totalSessions = linerSnapshot.totals.sessions,
                archivedAt = stableMillis(LocalDate.parse(linerEnd).plusDays(1)),
                schemaVersion = linerSnapshot.schemaVersion,
                snapshotJson = archiveJson.encodeToString(linerSnapshot),
            ),
        )
    }

    private fun cardioSessions(anchorDate: LocalDate): List<CardioSession> {
        val specs = listOf(
            // Treadmill (16 sessions over 90 days, speed 2.35 -> 2.60 m/s, HR 155 -> 148, two without HR)
            CardioSpec(88, CardioActivityType.TREADMILL, 30, calories = 310, notes = "Base pace", avgSpeed = 2.35, avgHeartRate = 155, avgInclinePercent = 2.0),
            CardioSpec(82, CardioActivityType.TREADMILL, 30, calories = 315, notes = "Steady incline", avgSpeed = 2.37, avgHeartRate = 155, avgInclinePercent = 2.5),
            CardioSpec(76, CardioActivityType.TREADMILL, 30, calories = 320, notes = "Easy pace", avgSpeed = 2.38, avgHeartRate = 154, avgInclinePercent = 2.0),
            CardioSpec(70, CardioActivityType.TREADMILL, 30, calories = 325, notes = "Morning jog", avgSpeed = 2.40, avgHeartRate = 154, avgInclinePercent = 2.0),
            CardioSpec(64, CardioActivityType.TREADMILL, 30, calories = 330, notes = "Steady effort", avgSpeed = 2.42, avgHeartRate = 153, avgInclinePercent = 2.5),
            CardioSpec(58, CardioActivityType.TREADMILL, 30, calories = 335, notes = "Even splits", avgSpeed = 2.44, avgHeartRate = 153, avgInclinePercent = 2.0),
            CardioSpec(52, CardioActivityType.TREADMILL, 30, calories = 335, notes = "Watch dead", avgSpeed = 2.45, avgHeartRate = null, avgInclinePercent = 2.0),
            CardioSpec(46, CardioActivityType.TREADMILL, 30, calories = 340, notes = "Consistent pace", avgSpeed = 2.47, avgHeartRate = 152, avgInclinePercent = 2.0),
            CardioSpec(40, CardioActivityType.TREADMILL, 30, calories = 345, notes = "Tempo run", avgSpeed = 2.49, avgHeartRate = 152, avgInclinePercent = 2.5),
            CardioSpec(34, CardioActivityType.TREADMILL, 30, calories = 350, notes = "Smooth stride", avgSpeed = 2.51, avgHeartRate = 151, avgInclinePercent = 2.0),
            CardioSpec(28, CardioActivityType.TREADMILL, 30, calories = 355, notes = "Midday run", avgSpeed = 2.53, avgHeartRate = 150, avgInclinePercent = 2.0),
            CardioSpec(22, CardioActivityType.TREADMILL, 30, calories = 355, notes = "Forgot chest strap", avgSpeed = 2.54, avgHeartRate = null, avgInclinePercent = 2.0),
            CardioSpec(16, CardioActivityType.TREADMILL, 30, calories = 360, notes = "Controlled pace", avgSpeed = 2.56, avgHeartRate = 149, avgInclinePercent = 2.5),
            CardioSpec(10, CardioActivityType.TREADMILL, 30, calories = 365, notes = "Strong finish", avgSpeed = 2.58, avgHeartRate = 149, avgInclinePercent = 2.0),
            CardioSpec(6, CardioActivityType.TREADMILL, 30, calories = 370, notes = "Legs felt fresh", avgSpeed = 2.59, avgHeartRate = 148, avgInclinePercent = 2.0),
            CardioSpec(2, CardioActivityType.TREADMILL, 30, calories = 375, notes = "Quick tempo", avgSpeed = 2.60, avgHeartRate = 148, avgInclinePercent = 2.0),
            // Bike (8 sessions weekly, watts 170 -> 192)
            CardioSpec(85, CardioActivityType.BIKE, 40, calories = 380, notes = "Base ride", avgPowerWatts = 170, cadenceRpm = 84, resistanceLevel = 7.0, avgHeartRate = 136),
            CardioSpec(78, CardioActivityType.BIKE, 40, calories = 385, notes = "Zone 2 spin", avgPowerWatts = 173, cadenceRpm = 85, resistanceLevel = 7.0, avgHeartRate = 137),
            CardioSpec(67, CardioActivityType.BIKE, 40, calories = 390, notes = "Steady cadence", avgPowerWatts = 176, cadenceRpm = 86, resistanceLevel = 8.0, avgHeartRate = 138),
            CardioSpec(55, CardioActivityType.BIKE, 45, calories = 420, notes = "Longer endurance", avgPowerWatts = 179, cadenceRpm = 86, resistanceLevel = 8.0, avgHeartRate = 139),
            CardioSpec(43, CardioActivityType.BIKE, 40, calories = 405, notes = "Smooth spin", avgPowerWatts = 182, cadenceRpm = 87, resistanceLevel = 8.0, avgHeartRate = 140),
            CardioSpec(31, CardioActivityType.BIKE, 50, calories = 460, notes = "Long weekend ride", avgPowerWatts = 185, cadenceRpm = 88, resistanceLevel = 9.0, avgHeartRate = 142),
            CardioSpec(19, CardioActivityType.BIKE, 40, calories = 415, notes = "Fast leg turnover", avgPowerWatts = 188, cadenceRpm = 88, resistanceLevel = 9.0, avgHeartRate = 141),
            CardioSpec(8, CardioActivityType.BIKE, 42, calories = 435, notes = "Power intervals", avgPowerWatts = 192, cadenceRpm = 89, resistanceLevel = 9.0, avgHeartRate = 143),
            // Rower (7 sessions every ~10 days, pace 134 -> 124 s/500m)
            CardioSpec(80, CardioActivityType.ROW, 20, calories = 220, notes = "Technique warmup", paceSecPer500m = 134, strokeRateSpm = 24, avgHeartRate = 145),
            CardioSpec(69, CardioActivityType.ROW, 20, calories = 225, notes = "Power stroke", paceSecPer500m = 132, strokeRateSpm = 24, avgHeartRate = 146),
            CardioSpec(59, CardioActivityType.ROW, 20, calories = 230, notes = "Pyramid pace", paceSecPer500m = 130, strokeRateSpm = 25, avgHeartRate = 147),
            CardioSpec(48, CardioActivityType.ROW, 20, calories = 235, notes = "Steady rhythm", paceSecPer500m = 128, strokeRateSpm = 25, avgHeartRate = 147),
            CardioSpec(37, CardioActivityType.ROW, 20, calories = 240, notes = "Mid-distance row", paceSecPer500m = 127, strokeRateSpm = 26, avgHeartRate = 148),
            CardioSpec(16, CardioActivityType.ROW, 15, calories = 190, notes = "Post-run cooldown", paceSecPer500m = 126, strokeRateSpm = 26, avgHeartRate = 148),
            CardioSpec(5, CardioActivityType.ROW, 18, calories = 245, notes = "Pace PR attempt", paceSecPer500m = 124, strokeRateSpm = 27, avgHeartRate = 149),
            // Stairs (2 sessions)
            CardioSpec(60, CardioActivityType.STAIR_CLIMBER, 15, calories = 140, notes = "Stair interval", floors = 64, avgHeartRate = 152),
            CardioSpec(14, CardioActivityType.STAIR_CLIMBER, 15, calories = 155, notes = "Steep climb", floors = 72, avgHeartRate = 155),
        )
        return specs.mapIndexed { index, spec ->
            val date = anchorDate.minusDays(spec.daysAgo.toLong())
            val id = CARDIO_ID_START + index
            val durationSec = spec.minutes * 60
            val distanceM = when {
                spec.avgSpeed != null -> spec.avgSpeed * durationSec
                spec.paceSecPer500m != null -> (durationSec.toDouble() / spec.paceSecPer500m) * 500.0
                spec.miles > 0.0 -> spec.miles * 1609.344
                else -> null
            }
            CardioSession(
                id = id,
                localUuid = "demo-cardio-$id",
                date = date.toString(),
                activityType = spec.type.name,
                activityLabel = when (spec.type) {
                    CardioActivityType.TREADMILL -> "Treadmill"
                    CardioActivityType.BIKE -> "Bike"
                    CardioActivityType.ROW -> "Rower"
                    CardioActivityType.RUN -> "Run"
                    CardioActivityType.STAIR_CLIMBER -> "Stair climber"
                    else -> spec.type.name.lowercase().replace("_", " ")
                },
                durationSec = durationSec,
                distanceM = distanceM,
                calories = spec.calories,
                avgHeartRate = spec.avgHeartRate,
                maxHeartRate = spec.avgHeartRate?.let { it + 15 },
                avgSpeed = spec.avgSpeed ?: distanceM?.let { it / durationSec },
                avgInclinePercent = spec.avgInclinePercent,
                cadenceRpm = spec.cadenceRpm,
                resistanceLevel = spec.resistanceLevel,
                avgPowerWatts = spec.avgPowerWatts,
                strokeRateSpm = spec.strokeRateSpm,
                paceSecPer500m = spec.paceSecPer500m,
                floors = spec.floors,
                source = CardioSessionSource.MANUAL,
                notes = spec.notes,
                createdAt = stableMillis(date) + (index * 1000L),
                updatedAt = stableMillis(date) + (index * 1000L),
            )
        }
    }

    private fun topWeightFor(spec: ExerciseSeed, week: Int, anchor: LocalDate, date: LocalDate): Int {
        if (spec.baseWeightLbs == null) return 0
        if (spec.name == "Close-Grip Bench" && week >= TOTAL_WEEKS - 2) {
            return if (week == TOTAL_WEEKS - 2) 172 else 176
        }
        val progress = (week / 4) * spec.incrementLbs
        val wave = when (week % 4) {
            0 -> -5
            1 -> 0
            2 -> 5
            else -> 0
        }
        val recentPush = if (date >= anchor.minusDays(18) && spec.showcase) spec.incrementLbs else 0
        return (spec.baseWeightLbs + progress + wave + recentPush).coerceAtLeast(5)
    }

    private fun storedWeightFor(spec: ExerciseSeed, topWeight: Int, setIndex: Int): Int? {
        if (spec.baseWeightLbs == null) return null
        val wholePounds = (topWeight - ((2 - setIndex) * spec.backoffLbs)).coerceAtLeast(if (spec.isBodyweight) 0 else 5)
        return WeightLbs.fromWholePounds(wholePounds)
    }

    private fun repsFor(spec: ExerciseSeed, week: Int, exerciseIndex: Int): Int {
        val wave = (week + exerciseIndex) % 3
        return spec.targetReps + when (wave) {
            0 -> 1
            1 -> 0
            else -> -1
        }
    }

    private fun restFor(spec: ExerciseSeed, setIndex: Int): Int =
        (if (spec.baseWeightLbs != null && spec.baseWeightLbs >= 225) 150 else 95) + (setIndex * 10)

    private fun buildHighlightShowcaseReps(
        totalReps: Int,
        pinCloseGripTopSet: Boolean,
    ): List<Int> {
        val reps = MutableList(8) { totalReps / 8 }
        repeat(totalReps % 8) { index -> reps[index] += 1 }
        if (pinCloseGripTopSet) {
            val pinnedIndex = 5 // Second exercise, third (top-load) set.
            var remainder = reps[pinnedIndex] - 7
            reps[pinnedIndex] = 7
            var index = 0
            while (remainder > 0) {
                if (index != pinnedIndex) {
                    reps[index] += 1
                    remainder -= 1
                }
                index = (index + 1) % reps.size
            }
        }
        check(reps.sum() == totalReps)
        return reps
    }

    private fun stableMillis(date: LocalDate): Long =
        date.toEpochDays().toLong() * 86_400_000L

    private data class SplitSeed(
        val name: String,
        val dayOffset: Long,
        val exerciseNames: List<String>,
    )

    private data class ExerciseSeed(
        val name: String,
        val muscleGroups: String,
        val isBodyweight: Boolean,
        val baseWeightLbs: Int?,
        val incrementLbs: Int,
        val backoffLbs: Int,
        val targetReps: Int,
        val showcase: Boolean = false,
    )

    private data class CardioSpec(
        val daysAgo: Int,
        val type: CardioActivityType,
        val minutes: Int,
        val miles: Double = 0.0,
        val calories: Int,
        val notes: String,
        val avgSpeed: Double? = null,
        val avgHeartRate: Int? = null,
        val avgInclinePercent: Double? = null,
        val avgPowerWatts: Int? = null,
        val cadenceRpm: Int? = null,
        val resistanceLevel: Double? = null,
        val strokeRateSpm: Int? = null,
        val paceSecPer500m: Int? = null,
        val floors: Int? = null,
    )

    private fun splitSeeds(): List<SplitSeed> = listOf(
        SplitSeed(
            name = "Upper Strength",
            dayOffset = 0,
            exerciseNames = listOf(
                "Barbell Bench Press",
                "Incline Dumbbell Press",
                "Pull Up",
                "Barbell Row",
                "Seated Dumbbell Shoulder Press",
                "Cable Triceps Pressdown",
            ),
        ),
        SplitSeed(
            name = "Lower Strength",
            dayOffset = 1,
            exerciseNames = listOf(
                "Back Squat",
                "Romanian Deadlift",
                "Leg Press",
                "Walking Lunge",
                "Seated Leg Curl",
                "Standing Calf Raise",
            ),
        ),
        SplitSeed(
            name = "Pull + Posterior",
            dayOffset = 3,
            exerciseNames = listOf(
                "Deadlift",
                "Lat Pulldown",
                "Chest Supported Row",
                "Face Pull",
                "Hammer Curl",
                "Reverse Fly",
            ),
        ),
        SplitSeed(
            name = "Push + Athletic",
            dayOffset = 5,
            exerciseNames = listOf(
                "Overhead Press",
                "Close-Grip Bench",
                "Bulgarian Split Squat",
                "Hip Thrust",
                "Hanging Knee Raise",
                "Farmer Carry",
            ),
        ),
    )

    private fun exerciseSeeds(): List<ExerciseSeed> = listOf(
        ExerciseSeed("Barbell Bench Press", "CHEST · TRICEPS", false, 165, 5, 10, 7, showcase = true),
        ExerciseSeed("Incline Dumbbell Press", "CHEST · SHOULDERS", false, 55, 2, 5, 9),
        ExerciseSeed("Pull Up", "BACK · BICEPS", true, 0, 1, 0, 8, showcase = true),
        ExerciseSeed("Barbell Row", "BACK · BICEPS", false, 135, 5, 10, 8),
        ExerciseSeed("Seated Dumbbell Shoulder Press", "SHOULDERS · TRICEPS", false, 45, 2, 5, 9),
        ExerciseSeed("Cable Triceps Pressdown", "TRICEPS", false, 70, 2, 5, 12),
        ExerciseSeed("Back Squat", "QUADS · GLUTES", false, 225, 5, 15, 6, showcase = true),
        ExerciseSeed("Romanian Deadlift", "HAMSTRINGS · GLUTES", false, 185, 5, 10, 8),
        ExerciseSeed("Leg Press", "QUADS · GLUTES", false, 360, 10, 20, 10),
        ExerciseSeed("Walking Lunge", "QUADS · GLUTES", false, 35, 2, 5, 10),
        ExerciseSeed("Seated Leg Curl", "HAMSTRINGS", false, 95, 3, 5, 11),
        ExerciseSeed("Standing Calf Raise", "CALVES", false, 140, 5, 10, 12),
        ExerciseSeed("Deadlift", "BACK · HAMSTRINGS · GLUTES", false, 275, 5, 20, 5, showcase = true),
        ExerciseSeed("Lat Pulldown", "BACK · BICEPS", false, 125, 4, 10, 10),
        ExerciseSeed("Chest Supported Row", "BACK", false, 95, 3, 5, 9),
        ExerciseSeed("Face Pull", "SHOULDERS · BACK", false, 45, 1, 5, 14),
        ExerciseSeed("Hammer Curl", "BICEPS · FOREARMS", false, 30, 1, 5, 10),
        ExerciseSeed("Reverse Fly", "SHOULDERS · BACK", false, 20, 1, 5, 13),
        ExerciseSeed("Overhead Press", "SHOULDERS · TRICEPS", false, 105, 3, 10, 6, showcase = true),
        ExerciseSeed("Close-Grip Bench", "TRICEPS · CHEST", false, 135, 4, 10, 8),
        ExerciseSeed("Bulgarian Split Squat", "QUADS · GLUTES", false, 40, 2, 5, 9),
        ExerciseSeed("Hip Thrust", "GLUTES · HAMSTRINGS", false, 185, 5, 15, 10),
        ExerciseSeed("Hanging Knee Raise", "CORE", true, null, 0, 0, 12),
        ExerciseSeed("Farmer Carry", "FOREARMS · CORE", false, 70, 2, 5, 10),
    )
}
