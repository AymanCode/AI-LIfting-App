package com.ayman.ecolift.cardio

import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.data.CardioSessionSource
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun session(
    date: String, type: CardioActivityType = CardioActivityType.TREADMILL, minutes: Int? = 30,
    metres: Double? = null, calories: Int? = null, hr: Int? = null, speedMps: Double? = null,
    watts: Int? = null, floors: Int? = null, pace500: Int? = null, id: Long = 0L, createdAt: Long = 0L,
) = CardioSession(
    id = id, localUuid = "t-$date-$id", date = date, activityType = type.name,
    durationSec = minutes?.let { it * 60 }, distanceM = metres, calories = calories, avgHeartRate = hr,
    avgSpeed = speedMps, avgPowerWatts = watts, floors = floors, paceSecPer500m = pace500,
    source = CardioSessionSource.MANUAL, createdAt = createdAt, updatedAt = createdAt,
)

class CardioInsightsTest {
    private val today = LocalDate(2026, 9, 9) // a Wednesday

    @Test fun week_arc_has_seven_dots_ending_today_and_sizes_by_minutes() {
        val dots = CardioInsights.weekArc(listOf(session("2026-09-09", minutes = 32), session("2026-09-07", minutes = 42, type = CardioActivityType.BIKE), session("2026-09-05", minutes = 18, type = CardioActivityType.ROW), session("2026-09-05", minutes = 12)), today)
        assertEquals(7, dots.size); assertEquals(LocalDate(2026, 9, 3), dots.first().date); assertEquals(today, dots.last().date)
        assertEquals(32, dots.last().minutes)
        val fri = dots.first { it.date == LocalDate(2026, 9, 5) }
        assertEquals(30, fri.minutes); assertEquals(2, fri.count); assertEquals(CardioActivityType.ROW, fri.machine)
        assertEquals(0, dots.first { it.date == LocalDate(2026, 9, 4) }.count)
    }

    @Test fun week_totals_use_a_rolling_seven_days_and_skip_future_dates() {
        val t = CardioInsights.weekTotals(listOf(session("2026-09-09", minutes = 30, metres = 1609.344, calories = 300), session("2026-09-03", minutes = 10), session("2026-09-02", minutes = 99), session("2026-09-10", minutes = 99)), today)
        assertEquals(40 * 60, t.seconds); assertEquals(1609.344, t.metres, 1e-6); assertEquals(300, t.calories); assertEquals(2, t.count)
    }

    @Test fun day_runs_count_consecutive_days_and_an_empty_today_does_not_break() {
        val s = listOf("2026-09-05", "2026-09-06", "2026-09-07", "2026-09-08", "2026-08-11", "2026-08-12", "2026-08-13", "2026-08-14", "2026-08-15").map { session(it) }
        val r = CardioInsights.dayRuns(s, today)
        assertEquals(LocalDate(2026, 9, 5), r.currentRunStart); assertEquals(4, r.currentRunLength)
        assertEquals(5, r.longestRunLength); assertEquals(LocalDate(2026, 8, 11), r.longestRunStart); assertEquals(LocalDate(2026, 8, 15), r.longestRunEnd)
        assertEquals(4, r.daysMovedThisMonth); assertEquals(9, r.daysElapsedThisMonth)
    }

    @Test fun day_runs_ignore_sessions_without_duration_and_cross_month_boundaries() {
        val r = CardioInsights.dayRuns(listOf(session("2026-08-31"), session("2026-09-01"), session("2026-09-02", minutes = null)), today)
        assertEquals(2, r.longestRunLength); assertNull(r.currentRunStart); assertEquals(0, r.currentRunLength)
    }

    @Test fun week_streak_counts_complete_monday_weeks_and_reports_what_this_week_needs() {
        val s = listOf("2026-08-18", "2026-08-20", "2026-08-25", "2026-08-27", "2026-09-01", "2026-09-03", "2026-09-08").map { session(it) }
        val w = CardioInsights.weekStreak(s, today)
        assertEquals(3, w.weeksInARow); assertEquals(false, w.currentWeekQualifies); assertEquals(1, w.currentWeekNeeds)
    }

    @Test fun week_streak_breaks_on_a_week_with_one_session() {
        val s = listOf("2026-08-18", "2026-08-20", "2026-08-25", "2026-09-01", "2026-09-03").map { session(it) }
        assertEquals(1, CardioInsights.weekStreak(s, today).weeksInARow)
    }

    @Test fun month_bars_split_seconds_by_machine_oldest_first() {
        val bars = CardioInsights.monthBars(listOf(session("2026-09-02", minutes = 30), session("2026-08-10", minutes = 40, type = CardioActivityType.BIKE), session("2026-08-12", minutes = 20)), today)
        assertEquals(4, bars.size); assertEquals(6, bars.first().month); assertTrue(bars.last().isCurrent)
        assertEquals(60 * 60, bars[2].totalSeconds); assertEquals(40 * 60, bars[2].secondsByMachine[CardioActivityType.BIKE])
    }

    @Test fun year_columns_and_machine_order() {
        val s = listOf(session("2026-05-01", minutes = 60), session("2026-05-02", minutes = 30, type = CardioActivityType.BIKE), session("2026-09-01", type = CardioActivityType.BIKE))
        assertEquals(90 * 60, CardioInsights.yearColumns(s, 2026)[4]); assertEquals(0, CardioInsights.yearColumns(s, 2026)[0])
        val order = CardioInsights.defaultMachineOrder(s)
        assertEquals(CardioActivityType.BIKE, order[0]); assertEquals(CardioActivityType.TREADMILL, order[1]); assertEquals(CardioActivityType.entries.size, order.size)
    }

    @Test fun last_on_machine_prefers_latest_date_then_created_at() {
        val a = session("2026-09-01", id = 1, createdAt = 10); val b = session("2026-09-01", id = 2, createdAt = 20); val c = session("2026-08-30", id = 3, createdAt = 99)
        assertEquals(2L, CardioInsights.lastOnMachine(listOf(a, b, c), CardioActivityType.TREADMILL)?.id)
    }
}
