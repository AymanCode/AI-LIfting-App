package com.ayman.ecolift.data.progress

import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.data.BodyPartRegionMapper
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.selectableBodyPartParts
import com.ayman.ecolift.web.util.YearMonthW
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * Builds everything the month sheet renders, from rows that already exist.
 *
 * Every figure counts checked sets only. Load accumulates as Double and is rounded
 * once at display, so the sheet never shows a total that per-set truncation produced.
 */
object MonthRollup {

    fun build(
        year: Int,
        month: Int,
        sets: List<WorkoutSet>,
        exercises: List<Exercise>,
        workoutDays: List<WorkoutDay>,
        slots: List<CycleSlot>,
        userBodyweightLbs: Int?,
    ): MonthRollupResult {
        val prefix = monthPrefix(year, month)
        val monthSets = sets.filter { it.completed && it.date.startsWith(prefix) }
        val exercisesById = exercises.associateBy { it.id }

        val sessionDates = monthSets.map { it.date }.distinct().sorted()
        val totalLoad = monthSets.sumOf { loadOf(it, exercisesById[it.exerciseId], userBodyweightLbs) }

        return MonthRollupResult(
            year = year,
            month = month,
            sessions = sessionDates.size,
            sets = monthSets.size,
            reps = monthSets.sumOf { it.reps ?: 0 },
            totalLoadLbs = totalLoad,
            loadPerSessionLbs = if (sessionDates.isEmpty()) 0.0 else totalLoad / sessionDates.size,
            days = daysOf(
                year, month, sessionDates, workoutDays, slots,
                prDates = prDatesOf(sets, prefix),
                setCountByDate = setCountByDate(monthSets),
                loadByDate = loadByDate(monthSets, exercisesById, userBodyweightLbs),
                topMusclesByDate = topMusclesByDate(monthSets, exercisesById),
                sessionNamingByDate = sessionNamingByDate(monthSets, workoutDays, slots, exercisesById),
            ),
            regions = regionsOf(monthSets, exercisesById, userBodyweightLbs),
            muscles = musclesOf(monthSets, exercisesById, userBodyweightLbs, totalLoad),
            recap = recapOf(monthSets, exercisesById, userBodyweightLbs),
            heaviestDay = heaviestDayOf(monthSets, exercisesById, userBodyweightLbs),
            slotRuns = slotRunsOf(sessionDates, workoutDays, slots),
            unassignedSessions = unassignedSessionsOf(sessionDates, workoutDays, slots),
            mostRunLifts = mostRunLiftsOf(monthSets, exercisesById),
            rhythm = rhythmOf(sessionDates),
        )
    }

    internal fun monthPrefix(year: Int, month: Int): String =
        year.toString().padStart(4, '0') + "-" + month.toString().padStart(2, '0')

    /**
     * Dates in the month carrying a PR set.
     *
     * A PR is a weight exceeding every prior weight for that exercise, where "prior" means
     * an earlier date — not an earlier set on the same day — AND the exercise already has
     * at least three distinct prior sessions. Without that second clause a young log flags
     * nearly every first heavy set: the reference export produces 41 in a single month,
     * which tells the user nothing.
     */
    private fun prDatesOf(
        allSets: List<WorkoutSet>,
        monthPrefix: String,
    ): Set<String> {
        val prDates = mutableSetOf<String>()

        allSets.filter { com.ayman.ecolift.data.WorkoutMetrics.isStrengthEligible(it) }
            .groupBy { it.exerciseId }
            .forEach { (_, sets) ->
                val byDate = sets.groupBy { it.date }.entries.sortedBy { it.key }
                var bestSoFar = Int.MIN_VALUE
                var priorSessions = 0

                byDate.forEach { (date, daysSets) ->
                    val dayBest = daysSets.maxOfOrNull { it.weightLbs ?: 0 } ?: 0
                    if (date.startsWith(monthPrefix) && priorSessions >= 3 && dayBest > bestSoFar) {
                        prDates.add(date)
                    }
                    if (dayBest > bestSoFar) bestSoFar = dayBest
                    priorSessions++
                }
            }

        return prDates
    }

    /** Recorded external load; changing profile bodyweight must not rewrite history. */
    internal fun effectiveWeightLbs(set: WorkoutSet, exercise: Exercise?, userBodyweightLbs: Int?): Double =
        WeightLbs.toLbs(set.weightLbs)

    internal fun loadOf(set: WorkoutSet, exercise: Exercise?, userBodyweightLbs: Int?): Double =
        com.ayman.ecolift.data.WorkoutMetrics.loggedVolumeLbs(set)

    /** Checked sets logged on each trained date. */
    private fun setCountByDate(monthSets: List<WorkoutSet>): Map<String, Int> =
        monthSets.groupingBy { it.date }.eachCount()

    /**
     * The two muscles with the most checked sets on each trained date, most sets first.
     *
     * A day with no split name is labelled from these. Ties break on muscle name so the
     * same day never labels itself two different ways between renders.
     */
    private fun topMusclesByDate(
        monthSets: List<WorkoutSet>,
        exercisesById: Map<Long, Exercise>,
    ): Map<String, List<MuscleSlice>> =
        monthSets.groupBy { it.date }.mapValues { (_, daysSets) ->
            daysSets.groupingBy { sliceOf(exercisesById[it.exerciseId]) }.eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<MuscleSlice, Int>> { it.value }.thenBy { it.key.name })
                .take(2)
                .map { it.key }
        }

    /**
     * Every lift run this month, ordered by the number of days it was run.
     *
     * This is the mix's fallback: a month where no session carries a split still has
     * something true to show, derived from the exercise names the user logged.
     */
    private fun mostRunLiftsOf(
        monthSets: List<WorkoutSet>,
        exercisesById: Map<Long, Exercise>,
    ): List<LiftRun> =
        monthSets.groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, sets) ->
                val exercise = exercisesById[exerciseId] ?: return@mapNotNull null
                LiftRun(
                    exerciseId = exerciseId,
                    name = exercise.name,
                    runs = sets.map { it.date }.distinct().size,
                )
            }
            .sortedWith(compareByDescending<LiftRun> { it.runs }.thenBy { it.name })

    /** Sessions per weekday. isoDayNumber is Mon=1..Sun=7, so % 7 puts Sunday at index 0. */
    private fun rhythmOf(sessionDates: List<String>): List<Int> {
        val counts = MutableList(7) { 0 }
        sessionDates.forEach { date ->
            val index = LocalDate.parse(date).dayOfWeek.isoDayNumber % 7
            counts[index] = counts[index] + 1
        }
        return counts
    }

    /** Load moved on each trained date, in pounds. */
    private fun loadByDate(
        monthSets: List<WorkoutSet>,
        exercisesById: Map<Long, Exercise>,
        userBodyweightLbs: Int?,
    ): Map<String, Double> =
        monthSets.groupBy { it.date }.mapValues { (_, daysSets) ->
            daysSets.sumOf { loadOf(it, exercisesById[it.exerciseId], userBodyweightLbs) }
        }

    /**
     * Share of the month by region, in both units.
     *
     * Sets is the honest denominator for "where did the work go" — isolation work moves
     * far less weight than compound machine work, so a load-denominated view paints
     * arms and shoulders as skipped when they weren't. Load is carried alongside so
     * the gap is visible rather than hidden.
     */
    private fun regionsOf(
        monthSets: List<WorkoutSet>,
        exercisesById: Map<Long, Exercise>,
        userBodyweightLbs: Int?,
    ): List<RegionShare> {
        if (monthSets.isEmpty()) return emptyList()

        val setsByRegion = mutableMapOf<BodyPartRegion, Int>()
        val loadByRegion = mutableMapOf<BodyPartRegion, Double>()

        monthSets.forEach { set ->
            val exercise = exercisesById[set.exerciseId]
            val region = regionOf(exercise)
            setsByRegion[region] = (setsByRegion[region] ?: 0) + 1
            loadByRegion[region] = (loadByRegion[region] ?: 0.0) + loadOf(set, exercise, userBodyweightLbs)
        }

        val totalSets = monthSets.size.toFloat()
        val totalLoad = loadByRegion.values.sum()

        return setsByRegion.keys
            .map { region ->
                val load = loadByRegion[region] ?: 0.0
                RegionShare(
                    region = region,
                    sets = setsByRegion[region] ?: 0,
                    loadLbs = load,
                    setShare = (setsByRegion[region] ?: 0) / totalSets,
                    loadShare = if (totalLoad <= 0.0) 0f else (load / totalLoad).toFloat(),
                )
            }
            .sortedWith(compareByDescending<RegionShare> { it.sets }.thenBy { it.region.name })
    }

    /** Share of the month by muscle, in both units. */
    private fun musclesOf(
        monthSets: List<WorkoutSet>,
        exercisesById: Map<Long, Exercise>,
        userBodyweightLbs: Int?,
        totalMonthLoad: Double = 0.0,
    ): List<MuscleShare> {
        if (monthSets.isEmpty()) return emptyList()

        val setsByMuscle = mutableMapOf<MuscleSlice, Int>()
        val loadByMuscle = mutableMapOf<MuscleSlice, Double>()

        monthSets.forEach { set ->
            val exercise = exercisesById[set.exerciseId]
            val slice = sliceOf(exercise)
            setsByMuscle[slice] = (setsByMuscle[slice] ?: 0) + 1
            loadByMuscle[slice] = (loadByMuscle[slice] ?: 0.0) + loadOf(set, exercise, userBodyweightLbs)
        }

        val totalSets = monthSets.size.toFloat()
        val totalLoad = if (totalMonthLoad > 0.0) totalMonthLoad else loadByMuscle.values.sum()

        return setsByMuscle.keys
            .map { slice ->
                val load = loadByMuscle[slice] ?: 0.0
                MuscleShare(
                    slice = slice,
                    sets = setsByMuscle[slice] ?: 0,
                    loadLbs = load,
                    setShare = (setsByMuscle[slice] ?: 0) / totalSets,
                    loadShare = if (totalLoad <= 0.0) 0f else (load / totalLoad).toFloat(),
                )
            }
            .sortedWith(compareByDescending<MuscleShare> { it.sets }.thenBy { it.slice.name })
    }

    /**
     * The primary muscle attribution rule: the first group in the exercise's list.
     * An exercise with no groups maps to UNKNOWN and stays in the denominator, so
     * shares always total 100%.
     *
     * Reuses [selectableBodyPartParts] (the existing muscle-group splitter, also used
     * for primary-muscle attribution in MuscleSuggestion.kt) rather than re-splitting
     * on the middle-dot separator here.
     */
    internal fun regionOf(exercise: Exercise?): BodyPartRegion {
        val first = selectableBodyPartParts(exercise?.muscleGroups.orEmpty()).firstOrNull()
            ?: return BodyPartRegion.UNKNOWN
        return BodyPartRegionMapper.mapGroupToRegion(first)
    }

    /** The same primary-muscle rule as [regionOf], at the log's finer resolution. */
    internal fun sliceOf(exercise: Exercise?): MuscleSlice {
        val first = selectableBodyPartParts(exercise?.muscleGroups.orEmpty()).firstOrNull()
            ?: return MuscleSlice.UNKNOWN
        return MuscleSlice.entries.firstOrNull { it.name == first } ?: MuscleSlice.UNKNOWN
    }

    /**
     * One cell per calendar day. Length comes from [YearMonthW] (the existing
     * java.time.YearMonth replacement, already used for the splits calendar) rather than
     * a hand-rolled leap-year check.
     */
    private fun daysOf(
        year: Int,
        month: Int,
        sessionDates: List<String>,
        workoutDays: List<WorkoutDay>,
        slots: List<CycleSlot>,
        prDates: Set<String>,
        setCountByDate: Map<String, Int>,
        loadByDate: Map<String, Double>,
        topMusclesByDate: Map<String, List<MuscleSlice>>,
        sessionNamingByDate: Map<String, Pair<String, BodyPartRegion>>,
    ): List<MonthDay> {
        val slotsById = slots.associateBy { it.id }
        val dayByDate = workoutDays.associateBy { it.date }
        val trained = sessionDates.toSet()
        val length = YearMonthW(year, month).lengthOfMonth()

        return (1..length).map { dayOfMonth ->
            val date = LocalDate(year, month, dayOfMonth)
            val iso = date.toString()
            val slot = dayByDate[iso]?.cycleSlotId?.let { slotsById[it] }
            val naming = sessionNamingByDate[iso]
            MonthDay(
                date = date,
                trained = iso in trained,
                slotId = slot?.id,
                slotName = slot?.name,
                hasPr = iso in prDates,
                setCount = setCountByDate[iso] ?: 0,
                loadLbs = loadByDate[iso] ?: 0.0,
                topMuscles = topMusclesByDate[iso].orEmpty(),
                sessionLabel = naming?.first.orEmpty(),
                leadRegion = naming?.second ?: BodyPartRegion.UNKNOWN,
            )
        }
    }

    /**
     * Session label and lead body region for each trained date.
     *
     * 1. A session in a split the user named -> that name.
     * 2. Otherwise -> its own top two body regions by number of lifts touching each region,
     *    joined with " · " (same counting as learnedGroupLabel). One region -> that word.
     * Blank labels never render.
     */
    internal fun sessionNamingByDate(
        monthSets: List<WorkoutSet>,
        workoutDays: List<WorkoutDay>,
        slots: List<CycleSlot>,
        exercisesById: Map<Long, Exercise>,
    ): Map<String, Pair<String, BodyPartRegion>> {
        val slotsById = slots.associateBy { it.id }
        val dayByDate = workoutDays.associateBy { it.date }
        return monthSets.groupBy { it.date }.mapValues { (date, daysSets) ->
            val slot = dayByDate[date]?.cycleSlotId?.let { slotsById[it] }
            val counts = HashMap<BodyPartRegion, Int>()
            val dayExercises = daysSets.mapNotNull { exercisesById[it.exerciseId] }.distinctBy { it.id }
            for (exercise in dayExercises) {
                selectableBodyPartParts(exercise.muscleGroups)
                    .map(BodyPartRegionMapper::mapGroupToRegion)
                    .filter { it != BodyPartRegion.UNKNOWN }
                    .distinct()
                    .forEach { counts[it] = (counts[it] ?: 0) + 1 }
            }
            val top = counts.entries
                .sortedWith(compareByDescending<Map.Entry<BodyPartRegion, Int>> { it.value }.thenBy { it.key.ordinal })
                .map { it.key }
            val leadRegion = top.firstOrNull() ?: BodyPartRegion.UNKNOWN

            val label = if (slot != null && slot.name.isNotBlank()) {
                slot.name.trim()
            } else {
                val names = top.take(2).map { it.displayName.uppercase() }
                if (names.isEmpty()) "TRAINING" else names.joinToString(" · ")
            }
            label to leadRegion
        }
    }

    /**
     * A session is unassigned when there is no workout_day row, the slot id is null,
     * or the slot id resolves to neither a current nor an archived slot.
     */
    private fun unassignedSessionsOf(
        sessionDates: List<String>,
        workoutDays: List<WorkoutDay>,
        slots: List<CycleSlot>,
    ): Int {
        val slotsById = slots.associateBy { it.id }
        val dayByDate = workoutDays.associateBy { it.date }
        return sessionDates.count { date ->
            val slotId = dayByDate[date]?.cycleSlotId
            slotId == null || slotsById[slotId] == null
        }
    }

    private fun slotRunsOf(
        sessionDates: List<String>,
        workoutDays: List<WorkoutDay>,
        slots: List<CycleSlot>,
    ): List<SlotRun> {
        val slotsById = slots.associateBy { it.id }
        val dayByDate = workoutDays.associateBy { it.date }
        val counts = mutableMapOf<Long, Int>()
        sessionDates.forEach { date ->
            val slot = dayByDate[date]?.cycleSlotId?.let { slotsById[it] } ?: return@forEach
            counts[slot.id] = (counts[slot.id] ?: 0) + 1
        }
        return counts.entries
            .map { (id, runs) -> SlotRun(slotId = id, name = slotsById.getValue(id).name, runs = runs) }
            .sortedWith(compareByDescending<SlotRun> { it.runs }.thenBy { it.name })
    }

    private fun heaviestDayOf(
        monthSets: List<WorkoutSet>,
        exercisesById: Map<Long, Exercise>,
        userBodyweightLbs: Int?,
    ): HeaviestDay? {
        if (monthSets.isEmpty()) return null
        val byDate = monthSets.groupBy { it.date }
        val best = byDate.entries
            .map { (date, sets) ->
                date to sets.sumOf { loadOf(it, exercisesById[it.exerciseId], userBodyweightLbs) }
            }
            // Ties break on the earlier date.
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .first()
        return HeaviestDay(
            date = LocalDate.parse(best.first),
            loadLbs = best.second,
            sets = byDate.getValue(best.first).size,
        )
    }

    /**
     * Within-month change per exercise, for lifts with 2+ sessions. One session has no
     * change to report. Change is the signed percentage difference in estimated 1RM
     * between the exercise's first and last session in the month.
     */
    private fun recapOf(
        monthSets: List<WorkoutSet>,
        exercisesById: Map<Long, Exercise>,
        userBodyweightLbs: Int?,
    ): List<RecapEntry> {
        return monthSets.groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, sets) ->
                val exercise = exercisesById[exerciseId] ?: return@mapNotNull null
                val dates = sets.map { it.date }.distinct().sorted()
                if (dates.size < 2) return@mapNotNull null

                val first = topEstimatedOneRm(sets.filter { it.date == dates.first() }, exercise, userBodyweightLbs)
                val last = topEstimatedOneRm(sets.filter { it.date == dates.last() }, exercise, userBodyweightLbs)
                if (first <= 0.0) return@mapNotNull null

                RecapEntry(
                    exerciseId = exerciseId,
                    name = exercise.name,
                    sessions = dates.size,
                    changePct = (((last - first) / first) * 100.0).toFloat(),
                    thin = dates.size == 2,
                )
            }
            .sortedWith(compareByDescending<RecapEntry> { it.changePct }.thenBy { it.name })
    }

    /** Epley on the session's best set, matching the formula already used across the app. */
    internal fun topEstimatedOneRm(
        sessionSets: List<WorkoutSet>,
        exercise: Exercise?,
        userBodyweightLbs: Int?,
    ): Double = sessionSets.mapNotNull { set ->
        com.ayman.ecolift.data.WorkoutMetrics.estimatedOneRepMax(set, exercise?.isBodyweight == true)
    }.maxOrNull() ?: 0.0

}
