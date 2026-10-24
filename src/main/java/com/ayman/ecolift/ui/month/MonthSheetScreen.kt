package com.ayman.ecolift.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.data.progress.MonthRollupResult
import com.ayman.ecolift.data.progress.MuscleSlice
import com.ayman.ecolift.data.progress.RegionShare
import com.ayman.ecolift.ui.theme.SheetSansFontFamily
import com.ayman.ecolift.ui.viewmodel.MonthSheetUiState
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.formatRecordedVolumeLbs
import com.ayman.ecolift.web.util.groupThousands
import com.ayman.ecolift.web.util.noun
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The month sheet: a recap of one month, visual first with one yardstick and every muscle on the body.
 */
@Composable
fun MonthSheetScreen(
    state: MonthSheetUiState,
    onStepMonth: (Int) -> Unit,
    onShare: (String) -> Unit,
    onOpenLift: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedDay by remember(state.yearMonth) { mutableStateOf<Int?>(null) }
    var selectedMuscle by remember(state.yearMonth) { mutableStateOf<MuscleSlice?>(null) }

    val rollup = state.rollup
    val sheet = remember(rollup) { rollup?.let(::monthSheetData) }
    val shownDay = selectedDay ?: sheet?.sessions?.lastOrNull()?.dayOfMonth
    val shownSession = sheet?.sessions?.firstOrNull { it.dayOfMonth == shownDay }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(SheetBg),
        contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 48.dp),
    ) {
        item(key = "entrance") {
            MonthEntranceContent(state, onStepMonth, onOpenLift)
        }
        if (rollup != null && rollup.sessions == 0) {
            item(key = "empty") {
                Text(
                    "NOTHING LOGGED",
                    style = sheetSans(9.0, SheetDim, weight = FontWeight.SemiBold, letterSpacing = 1.8.sp),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        if (rollup != null && sheet != null && rollup.sessions > 0) {
            item(key = "made-of") {
                SheetHairline(verticalMargin = 28.dp)
                SheetReveal {
                    Column {
                        SheetSectionLabel(title = "Made of", trailing = null)
                        Spacer(Modifier.height(18.dp))
                        SheetMix(sheet.mix)
                        Spacer(Modifier.height(18.dp))
                        SheetCalendar(
                            days = sheet.cells,
                            leadingBlanks = sheet.leadingBlanks,
                            selected = shownDay,
                            onSelectDay = { selectedDay = it },
                        )
                        SheetCalendarReadout(shownSession)
                    }
                }
            }

            state.comparison?.let { comparison ->
                item(key = "compare") {
                    SheetHairline(verticalMargin = 28.dp)
                    SheetReveal {
                        Column {
                            MonthCompareContent(comparison)
                        }
                    }
                }
            }

            item(key = "where-it-went") {
                SheetHairline(verticalMargin = 28.dp)
                SheetReveal {
                    Column {
                        SheetSectionLabel(title = "Where it went", trailing = null)
                        Spacer(Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp),
                        ) {
                            Text(
                                text = "SETS",
                                style = sheetSans(9.0, SheetInk, weight = FontWeight.SemiBold, letterSpacing = 1.8.sp),
                            )
                            Text(
                                text = "LB",
                                style = sheetSans(9.0, SheetAcc, weight = FontWeight.SemiBold, letterSpacing = 1.8.sp),
                            )
                        }
                        SheetAnatomyWhereItWent(
                            muscles = rollup.muscles,
                            selected = selectedMuscle,
                            onSelect = { selectedMuscle = if (selectedMuscle == it) null else it },
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 30.dp)
                                .height(34.dp)
                                .border(1.dp, Color(0xFF3A2016), RoundedCornerShape(17.dp))
                                .sheetTap { onShare(shareText(state, rollup, sheet)) }
                                .semantics(mergeDescendants = true) { role = Role.Button }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "SHARE ↗",
                                style = TextStyle(
                                    fontFamily = SheetSansFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.8.sp,
                                    color = SheetAcc,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Everything the sections draw, shaped once per rollup. */
internal data class MonthSheetData(
    val sessions: List<SheetSession>,
    val cells: List<SheetDayCell>,
    val leadingBlanks: Int,
    val splitMode: Boolean,
    val mix: List<SheetMixRow>,
    val heat: List<SheetHeatRow> = emptyList(),
    val byWeight: List<SheetPieSlice> = emptyList(),
    val contrast: String = "",
    val setsByMuscle: Map<MuscleSlice, Int> = emptyMap(),
    val jumps: List<SheetLiftDelta> = emptyList(),
    val roster: List<SheetLiftDelta> = emptyList(),
)

internal fun monthSheetData(rollup: MonthRollupResult): MonthSheetData {
    val abbrev = MONTH_ABBREV[rollup.month - 1]
    val heaviestDate = rollup.heaviestDay?.date
    val trainedDays = rollup.days.filter { it.trained }
    val heaviestLoad = trainedDays.maxOfOrNull { it.loadLbs } ?: 0.0

    val sessions = trainedDays.map { day ->
        val fillFraction = if (heaviestLoad > 0.0) {
            max(0.08f, (day.loadLbs / heaviestLoad).toFloat())
        } else {
            0f
        }
        val muscleNames = if (day.topMuscles.isNotEmpty()) {
            day.topMuscles.joinToString(" + ") { it.displayName.uppercase() }
        } else {
            day.sessionLabel.uppercase()
        }
        SheetSession(
            dayOfMonth = day.date.day,
            dateLabel = "$abbrev ${day.date.day}",
            loadLbs = day.loadLbs,
            label = day.sessionLabel,
            isHeaviest = day.date == heaviestDate,
            hasRecord = day.hasPr,
            color = leadRegionColor(day.leadRegion),
            fillFraction = fillFraction,
            muscleNames = muscleNames,
        )
    }
    val sessionsByDay = sessions.associateBy { it.dayOfMonth }

    val mix = trainedDays
        .groupBy { it.sessionLabel }
        .map { (label, days) ->
            val leadReg = days.first().leadRegion
            SheetMixRow(
                name = label,
                count = days.size,
                color = leadRegionColor(leadReg),
                leadRegion = leadReg,
            )
        }
        .sortedWith(compareByDescending<SheetMixRow> { it.count }.thenBy { it.name })

    val splitMode = rollup.unassignedSessions < rollup.sessions

    val deltas = rollup.recap.map {
        SheetLiftDelta(
            name = it.name,
            sessions = it.sessions,
            pct = it.changePct.roundToInt(),
            thin = it.thin,
        )
    }
    val jumps = deltas.filter { it.pct > 0 }.take(3)

    val byWeight = rollup.regions
        .sortedWith(compareByDescending<RegionShare> { it.loadLbs }.thenBy { it.region.name })
        .let { byLoad ->
            byLoad.zip(wholePercents(byLoad.map { it.loadShare })).map { (region, pct) ->
                SheetPieSlice(
                    region = region.region,
                    name = region.region.displayName,
                    pct = pct,
                    sets = region.sets,
                    loadLbs = region.loadLbs,
                )
            }
        }

    return MonthSheetData(
        sessions = sessions,
        cells = rollup.days.map { SheetDayCell(it.date.day, sessionsByDay[it.date.day]) },
        leadingBlanks = LocalDate(rollup.year, rollup.month, 1).dayOfWeek.isoDayNumber % 7,
        splitMode = splitMode,
        mix = mix,
        heat = rollup.muscles.map {
            SheetHeatRow(
                slice = it.slice,
                name = it.slice.displayName,
                sets = it.sets,
                sharePct = (it.setShare * 100f).roundToInt(),
            )
        },
        byWeight = byWeight,
        contrast = contrastLine(byWeight, rollup.sets),
        setsByMuscle = rollup.muscles.associate { it.slice to it.sets },
        jumps = jumps,
        roster = deltas - jumps.toSet(),
    )
}

/**
 * The month's sets-versus-pounds gap, in one sentence.
 *
 * Names the region that moved the most weight, then the region whose share of the sets most
 * outruns its share of the pounds -- the pair that makes the two measures look like they
 * disagree. A month with nothing to contrast says nothing rather than reaching.
 *
 * Reads the donut's own percentages rather than rounding the shares again, so the sentence
 * and the legend above it can never print two different numbers for the same slice.
 */
internal fun contrastLine(slices: List<SheetPieSlice>, totalSets: Int): String {
    if (totalSets <= 0) return ""
    val ranked = slices.filter { it.region != BodyPartRegion.UNKNOWN && it.sets > 0 }
    val lead = ranked.maxByOrNull { it.pct } ?: return ""

    fun setPct(slice: SheetPieSlice) = (slice.sets.toFloat() / totalSets * 100f).roundToInt()
    fun sentence(slice: SheetPieSlice) =
        "${slice.name} did ${setPct(slice)}% of the sets and ${slice.pct}% of the lifting."

    val foil = ranked
        .filter { it.region != lead.region }
        .maxByOrNull { setPct(it) - it.pct }
        ?.takeIf { setPct(it) - it.pct >= 4 }
        ?: return sentence(lead)

    return sentence(lead) + " " + sentence(foil)
}

/**
 * Shares as whole percents that total exactly 100. For the donut only.
 *
 * Rounding each share alone leaves a pie's legend reading 99%, which is the first thing
 * anyone checks. This hands the leftover points to the shares with the largest fractional
 * parts, so each point lands on the slice with the best claim to it.
 *
 * The cost is that two exactly equal shares can print one apart, since 100 does not divide
 * evenly. That is why the set rows above round independently instead: their ties are common
 * and visible, where two muscles carrying identical poundage to the pound are neither.
 */
internal fun wholePercents(shares: List<Float>): List<Int> {
    if (shares.isEmpty()) return emptyList()
    val exact = shares.map { (it * 100f).toDouble() }
    val floors = exact.map { floor(it).toInt() }.toMutableList()
    var leftover = 100 - floors.sum()
    if (leftover <= 0) return floors
    exact.indices
        .sortedWith(compareByDescending<Int> { exact[it] - floors[it] }.thenBy { it })
        .forEach { index ->
            if (leftover == 0) return@forEach
            floors[index]++
            leftover--
        }
    return floors
}

/**
 * What a day is called. The user's split name when there is one, otherwise the day's two
 * busiest muscles. A muscle the log could not place contributes nothing to the name.
 */
internal fun dayLabel(slotName: String?, topMuscles: List<MuscleSlice>): String {
    if (!slotName.isNullOrBlank()) return slotName
    return topMuscles
        .filter { it != MuscleSlice.UNKNOWN }
        .joinToString(" + ") { it.displayName }
}

private val WEEKDAY_NAMES =
    listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

/**
 * Names the weekdays the month was trained on, and when exactly one weekday went untouched,
 * names that too. Two or more empty weekdays is a schedule, not a joke, so it stays quiet.
 */
internal fun rhythmCaption(rhythm: List<Int>): String {
    if (rhythm.size != 7 || rhythm.all { it == 0 }) return ""
    val trained = rhythm.indices.filter { rhythm[it] > 0 }.joinToString(", ") { WEEKDAY_NAMES[it] }
    val untouched = rhythm.indices.filter { rhythm[it] == 0 }
    return if (untouched.size == 1) {
        "$trained. Never a ${WEEKDAY_NAMES[untouched.single()]}."
    } else {
        "$trained."
    }
}

private val COUNT_WORDS = listOf(
    "No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
    "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen",
    "Nineteen", "Twenty", "Twenty-one", "Twenty-two", "Twenty-three", "Twenty-four",
    "Twenty-five", "Twenty-six", "Twenty-seven", "Twenty-eight", "Twenty-nine", "Thirty",
    "Thirty-one",
)

/** Section 01 counts in words, the way the sheet speaks. A month cannot exceed 31 sessions. */
internal fun countWord(count: Int): String = COUNT_WORDS.getOrElse(count) { count.toString() }

private fun shareText(state: MonthSheetUiState, rollup: MonthRollupResult, sheet: MonthSheetData): String {
    val heaviest = sheet.sessions.firstOrNull { it.isHeaviest }
    return buildString {
        appendLine(state.monthLabel)
        appendLine(
            "${formatRecordedVolumeLbs(rollup.totalLoadLbs)} lb moved · " +
                "${countLabel(rollup.sets, "set")} · ${groupThousands(rollup.reps)} ${noun(rollup.reps, "rep")}",
        )
        appendLine("${countLabel(sheet.sessions.size, "session")}")
        if (heaviest != null) {
            append("Heaviest day: ${heaviest.dateLabel}, ${formatRecordedVolumeLbs(heaviest.loadLbs)} lb")
        }
    }
}
