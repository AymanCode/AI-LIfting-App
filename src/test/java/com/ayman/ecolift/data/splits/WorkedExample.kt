package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet

/**
 * The spec's worked example (Ayman's account as of 2026-09-28), rebuilt as a synthetic fixture:
 * the lift names, the training dates and which lifts were checked each day reproduce the spec's
 * table; the weights and reps are illustrative (the spec gives none). Cards follow listing order.
 */
internal object WorkedExample {
    const val AS_OF = "2026-09-28"

    // Upper 1 (named seed)
    const val INCLINE_BENCH = 101L
    const val LAT_PULLDOWN = 102L
    const val REAR_SHOULDER_SINGLE_PEC = 103L
    const val CBUM_TRICEP_EXTENSION = 104L
    const val LEANING_HAMMER_CURL = 105L
    // Chest · Arms
    const val DIP = 111L
    const val ISO_LATERAL_HIGH_RAISE = 112L
    const val LATERAL_RAISE_MACHINE = 113L
    const val LEANING_DUMBELL_CURL = 114L
    // Legs (Hack Squat group)
    const val HACK_SQUAT = 121L
    const val SEATED_LEG_CURL = 122L
    const val STANDING_CALF_RAISE_MACHINE = 123L
    // Legs (Leg Extension group)
    const val LYING_LEG_CURL = 131L
    const val LEG_EXTENSION = 132L
    // Solo held
    const val BENCH_PRESS = 141L
    const val PEC_DECK = 142L
    const val INCLINE_DUMBELL_HAMMER = 143L
    const val HIP_ABDUCTION = 144L
    // Not held
    const val SKULL_CRUSHER = 151L
    const val ONE_LEGGED_EXTENSION = 152L
    const val CALVE_RAISE_LEG_PRESS = 153L
    const val HIP_ABDUCTOR = 154L
    const val PECTORAL_FLY = 155L

    const val UPPER_1_SLOT = 7L

    val exercises: List<Exercise> = listOf(
        Exercise(INCLINE_BENCH, "Incline Bench Press", "CHEST"),
        Exercise(LAT_PULLDOWN, "Lat Pulldown", "BACK"),
        Exercise(REAR_SHOULDER_SINGLE_PEC, "Rear Shoulder Single Pec", "SHOULDERS"),
        Exercise(CBUM_TRICEP_EXTENSION, "Cbum Tricep Extension", "TRICEPS"),
        Exercise(LEANING_HAMMER_CURL, "Leaning Hammer Curl", "BICEPS"),
        Exercise(DIP, "Dip", "CHEST · TRICEPS", isBodyweight = true),
        Exercise(ISO_LATERAL_HIGH_RAISE, "Iso Lateral High Raise", "CHEST"),
        Exercise(LATERAL_RAISE_MACHINE, "Lateral Raise Machine", "SHOULDERS"),
        Exercise(LEANING_DUMBELL_CURL, "Leaning Dumbell Curl", "BICEPS"),
        Exercise(HACK_SQUAT, "Hack Squat", "QUADS"),
        Exercise(SEATED_LEG_CURL, "Seated Leg Curl", "HAMSTRINGS"),
        Exercise(STANDING_CALF_RAISE_MACHINE, "Standing Calf Raise Machine", "CALVES"),
        Exercise(LYING_LEG_CURL, "Lying Leg Curl", "HAMSTRINGS"),
        Exercise(LEG_EXTENSION, "Leg Extension", "QUADS"),
        Exercise(BENCH_PRESS, "Bench Press", "CHEST"),
        Exercise(PEC_DECK, "Pec Deck", "CHEST"),
        Exercise(INCLINE_DUMBELL_HAMMER, "Incline Dumbell Hammer", "BICEPS"),
        Exercise(HIP_ABDUCTION, "Hip Abduction", "GLUTES"),
        Exercise(SKULL_CRUSHER, "Standing Behind The Back Skull Crusher", "TRICEPS"),
        Exercise(ONE_LEGGED_EXTENSION, "One Legged Extension", "QUADS"),
        Exercise(CALVE_RAISE_LEG_PRESS, "Calve Raise Leg Press", "CALVES"),
        Exercise(HIP_ABDUCTOR, "Hip Abductor", "GLUTES"),
        Exercise(PECTORAL_FLY, "Pectoral Fly", "CHEST"),
    )

    /** Checked lifts per training day, in card order. */
    val days: List<Pair<String, List<Long>>> = listOf(
        "2026-08-20" to listOf(HIP_ABDUCTOR),
        "2026-08-24" to listOf(INCLINE_DUMBELL_HAMMER, PEC_DECK),
        "2026-09-01" to listOf(LEANING_DUMBELL_CURL),
        "2026-09-02" to listOf(HACK_SQUAT, HIP_ABDUCTION, BENCH_PRESS, CALVE_RAISE_LEG_PRESS),
        "2026-09-03" to listOf(SKULL_CRUSHER, LEANING_DUMBELL_CURL),
        "2026-09-05" to listOf(LEANING_DUMBELL_CURL, PEC_DECK),
        "2026-09-07" to listOf(DIP, ISO_LATERAL_HIGH_RAISE, LEANING_DUMBELL_CURL, SKULL_CRUSHER),
        "2026-09-09" to listOf(INCLINE_BENCH, LAT_PULLDOWN, REAR_SHOULDER_SINGLE_PEC, CBUM_TRICEP_EXTENSION, LEANING_HAMMER_CURL),
        "2026-09-10" to listOf(LYING_LEG_CURL, LEG_EXTENSION, BENCH_PRESS),
        "2026-09-12" to listOf(DIP, ISO_LATERAL_HIGH_RAISE, LATERAL_RAISE_MACHINE, LEANING_DUMBELL_CURL, PECTORAL_FLY),
        "2026-09-14" to listOf(HACK_SQUAT, SEATED_LEG_CURL, STANDING_CALF_RAISE_MACHINE),
        "2026-09-15" to listOf(INCLINE_BENCH, LAT_PULLDOWN, REAR_SHOULDER_SINGLE_PEC, CBUM_TRICEP_EXTENSION, LEANING_HAMMER_CURL),
        "2026-09-17" to listOf(DIP, LATERAL_RAISE_MACHINE, LEANING_DUMBELL_CURL, BENCH_PRESS),
        "2026-09-18" to listOf(LYING_LEG_CURL, LEG_EXTENSION),
        "2026-09-20" to listOf(LAT_PULLDOWN, REAR_SHOULDER_SINGLE_PEC, BENCH_PRESS, INCLINE_DUMBELL_HAMMER),
        "2026-09-21" to listOf(HACK_SQUAT, SEATED_LEG_CURL, STANDING_CALF_RAISE_MACHINE, PEC_DECK),
        "2026-09-23" to listOf(DIP, ISO_LATERAL_HIGH_RAISE, LATERAL_RAISE_MACHINE, LEANING_DUMBELL_CURL, STANDING_CALF_RAISE_MACHINE),
        "2026-09-24" to listOf(LEG_EXTENSION, HIP_ABDUCTION, ONE_LEGGED_EXTENSION),
        "2026-09-26" to listOf(LAT_PULLDOWN, REAR_SHOULDER_SINGLE_PEC, CBUM_TRICEP_EXTENSION, LEANING_HAMMER_CURL, BENCH_PRESS),
        "2026-09-27" to listOf(HACK_SQUAT, SEATED_LEG_CURL, CBUM_TRICEP_EXTENSION),
    )

    /** Illustrative top set per lift, stored tenths: (weightLbs, reps). */
    private val topSet: Map<Long, Pair<Int, Int>> = mapOf(
        INCLINE_BENCH to (1350 to 8), LAT_PULLDOWN to (1400 to 10), REAR_SHOULDER_SINGLE_PEC to (225 to 12),
        CBUM_TRICEP_EXTENSION to (500 to 12), LEANING_HAMMER_CURL to (300 to 10), DIP to (0 to 12),
        ISO_LATERAL_HIGH_RAISE to (900 to 10), LATERAL_RAISE_MACHINE to (700 to 12), LEANING_DUMBELL_CURL to (250 to 10),
        HACK_SQUAT to (2700 to 8), SEATED_LEG_CURL to (1100 to 10), STANDING_CALF_RAISE_MACHINE to (1800 to 12),
        LYING_LEG_CURL to (900 to 10), LEG_EXTENSION to (1300 to 12), BENCH_PRESS to (1850 to 5),
        PEC_DECK to (1200 to 10), INCLINE_DUMBELL_HAMMER to (350 to 10), HIP_ABDUCTION to (1500 to 12),
        SKULL_CRUSHER to (400 to 10), ONE_LEGGED_EXTENSION to (500 to 10), CALVE_RAISE_LEG_PRESS to (2000 to 15),
        HIP_ABDUCTOR to (1400 to 12), PECTORAL_FLY to (800 to 12),
    )

    /** A warm-up and two working sets per lift per day; the warm-up is checked but never counts. */
    val sets: List<WorkoutSet> = days.flatMap { (date, lifts) ->
        lifts.flatMap { lift ->
            val (weight, reps) = topSet.getValue(lift)
            val bodyweight = lift == DIP
            listOf(
                LearnedFixtures.set(date, lift, setNumber = 1, weightLbs = weight / 2, reps = reps, warmup = true, bodyweight = bodyweight),
                LearnedFixtures.set(date, lift, setNumber = 2, weightLbs = weight, reps = reps, bodyweight = bodyweight),
                LearnedFixtures.set(date, lift, setNumber = 3, weightLbs = weight, reps = reps - 1, bodyweight = bodyweight),
            )
        }
    }

    val cards: List<WorkoutExerciseCard> = days.flatMap { (date, lifts) -> LearnedFixtures.cards(date, *lifts.toLongArray()) }

    /** "Upper 1": the one hand-made split, created before learned splits existed (created_at 0). */
    val upper1 = LearnedFixtures.slot(UPPER_1_SLOT, name = "Upper 1", learned = false, createdAt = 0, orderIndex = 0, colorIndex = 0)
    val upper1Members = LearnedFixtures.members(
        UPPER_1_SLOT, INCLINE_BENCH, LAT_PULLDOWN, REAR_SHOULDER_SINGLE_PEC, CBUM_TRICEP_EXTENSION, LEANING_HAMMER_CURL,
    )

    /** The stored stamps as they are today: Upper 1 on Sep 20 and on the 2026-09-27 leg day. */
    val storedDays: List<WorkoutDay> = listOf(
        WorkoutDay("2026-09-20", cycleSlotType = 0, cycleSlotOccurrence = 1, cycleSlotId = UPPER_1_SLOT),
        WorkoutDay("2026-09-27", cycleSlotType = 0, cycleSlotOccurrence = 2, cycleSlotId = UPPER_1_SLOT),
    )

    fun inputs(asOf: String = AS_OF) = LearnedFixtures.inputs(
        asOf = asOf,
        sets = sets,
        cards = cards,
        slots = listOf(upper1),
        slotMembers = upper1Members,
        workoutDays = storedDays,
        cycleStartDate = "2026-08-01",
    )

    val UPPER = listOf(INCLINE_BENCH, LAT_PULLDOWN, REAR_SHOULDER_SINGLE_PEC, CBUM_TRICEP_EXTENSION, LEANING_HAMMER_CURL)
    val CHEST_ARMS = listOf(DIP, ISO_LATERAL_HIGH_RAISE, LATERAL_RAISE_MACHINE, LEANING_DUMBELL_CURL)
    val LEGS_HACK = listOf(HACK_SQUAT, SEATED_LEG_CURL, STANDING_CALF_RAISE_MACHINE)
    val LEGS_EXTENSION = listOf(LYING_LEG_CURL, LEG_EXTENSION)

    fun key(members: List<Long>): String = "c:" + members.sorted().joinToString("-")
}
