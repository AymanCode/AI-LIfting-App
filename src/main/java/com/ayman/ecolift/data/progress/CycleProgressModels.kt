package com.ayman.ecolift.data.progress

enum class ComparisonWindow(val months: Int) { M1(1), M3(3), M6(6) }

enum class LiftMetric { E1RM, ADDED_WEIGHT, REPS } // how this lift is measured

enum class Movement { IMPROVED, HELD, REGRESSED } // vs prior window

// One point in a lift's trend (within-cycle), already reduced to per-session.
data class TrendPoint(val date: String, val value: Float)

// Window-INDEPENDENT facts about the cycle itself.
data class CycleProgressCore(
    val startDate: String,
    val endDate: String,
    val spanDays: Int,
    val sessions: Int,                  // distinct workout dates on slot days
    val sessionsPerWeek: Float,
    val sessionDates: List<String>,     // for the heatmap
    val totalSets: Int,
    val repBuckets: List<RepBucket>,    // Strength/Hypertrophy/Endurance/Metabolic
    val lifts: List<LiftTrend>,         // per-exercise within-cycle trend
)

data class RepBucket(
    val label: String,                  // "Strength" etc.
    val minReps: Int, val maxReps: Int, // maxReps = Int.MAX_VALUE for open bucket
    val sets: Int, val pctOfSets: Float,
)

data class LiftTrend(
    val exerciseId: Long,
    val name: String,
    val splitSlotId: Long,
    val splitName: String,
    val metric: LiftMetric,
    val isBodyweight: Boolean,
    val points: List<TrendPoint>,       // within-cycle session series, date-sorted
    val slopePerWeek: Float?,           // null if <2 points or zero variance
    val r2: Float?,                     // null if undefined
    val unitLabel: String,              // "lb/wk" or "reps/wk"
)

// Window-DEPENDENT comparison vs the user's own prior history.
data class CycleComparison(
    val window: ComparisonWindow,
    val lifts: List<LiftComparison>,
    val progression: Float,             // 0..100
    val momentum: Float,                // 0..100
    val consistency: Float,             // 0..100
    val improvedCount: Int, val heldCount: Int, val regressedCount: Int,
    val comparedCount: Int,             // lifts with a usable prior baseline
)

data class LiftComparison(
    val exerciseId: Long,
    val name: String,
    val metric: LiftMetric,
    val isBodyweight: Boolean,
    val isNew: Boolean,                 // no prior data -> excluded from aggregates
    val baselineValue: Float?,          // projected/point prior baseline at cycle end
    val endValue: Float?,               // actual end value
    val vsPct: Float?,                  // (end - baseline)/baseline * 100
    val currentRatePctPerMonth: Float?, // within-cycle % rate
    val priorRatePctPerMonth: Float?,   // prior-window % rate (null if point fallback)
    val momentumScore: Float?,          // 0..100, null if no prior rate
    val movement: Movement,
    val totalSets: Int,
)

data class ScoreWeights(
    val progression: Int = 40,
    val momentum: Int = 35,
    val consistency: Int = 25,
) { companion object { val PRESET = ScoreWeights() } }

data class ScoreBreakdown(
    val composite: Int,                 // 0..100
    val progression: Float, val momentum: Float, val consistency: Float,
)
