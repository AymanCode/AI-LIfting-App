package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.*
import com.ayman.ecolift.data.splits.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

class ExerciseSwapTest {
    private val exercises = listOf(
        Exercise(1, "Overhead Press", "SHOULDERS"),
        Exercise(2, "Seated Dumbbell Press", "SHOULDERS"),
        Exercise(3, "Romanian Deadlift", "HAMSTRINGS"),
        Exercise(4, "Pull Up", "BACK", isBodyweight = true),
        Exercise(5, "Cable Lateral Raise", "SHOULDERS"),
        Exercise(6, "Old Shoulder Press", "SHOULDERS", mergedIntoId = 2),
        Exercise(7, "Landmine Press", "SHOULDERS"),
    )
    private val aliases = listOf(ExerciseAlias(exerciseId = 6, alias = "Arnold lift", normalizedKey = "arnold lift"))
    private fun results(query: String = "", sets: List<WorkoutSet> = emptyList()) = swapCandidates(
        1, 10, query, "2026-10-04", exercises, aliases, sets,
        listOf(SplitAlternative(10, 1, 2)), setOf(1, 3), { if (it == 6L) 2 else it },
    )

    @Test fun searchSuggestsAsTheNameIsTypedAndAcceptsShorthand() {
        for (query in listOf("s", "se", "sea", "seated", "db", "db press", "dumbell")) {
            assertTrue(results(query).any { it.exerciseId == 2L }, "Missing dumbbell suggestion for $query")
        }
        assertEquals(3L, results("rdl").first().exerciseId)
        assertTrue(results("  ARNOLD lift ").any { it.exerciseId == 2L })
        assertTrue(results("zzzznonexistent").isEmpty())
    }

    @Test fun mergedAliasesResolveToTheKeeperAndSourceNeverAppears() {
        for (query in listOf("", "press", "arnold", "ohp")) {
            val ids = results(query).map { it.exerciseId }
            assertFalse(1L in ids)
            assertFalse(6L in ids)
            assertEquals(ids.distinct(), ids)
        }
    }

    @Test fun suggestionsUseOnlyTheTargetsCheckedPastHistory() {
        val history = listOf(
            WorkoutSet(exerciseId = 2, date = "2026-10-01", setNumber = 1, weightLbs = 250, reps = 12, completed = true, isWarmup = true),
            WorkoutSet(exerciseId = 2, date = "2026-10-01", setNumber = 2, weightLbs = 505, reps = 8, completed = true),
            WorkoutSet(exerciseId = 2, date = "2026-10-02", setNumber = 1, weightLbs = 9999, reps = 99),
            WorkoutSet(exerciseId = 2, date = "2026-10-04", setNumber = 1, weightLbs = 9999, reps = 99, completed = true),
            WorkoutSet(exerciseId = 2, date = "2026-10-05", setNumber = 1, weightLbs = 9999, reps = 99, completed = true),
        )
        val row = results("db", history).single()
        assertTrue(row.detail.contains("Last 50.5 lb × 8"), row.detail)
        assertTrue(row.alreadyToday)
        assertTrue(row.detail.contains("In today"))
    }

    @Test fun bodyweightAndUnknownHistoryRemainDistinct() {
        val history = listOf(WorkoutSet(exerciseId = 4, date = "2026-10-01", setNumber = 1,
            weightLbs = 125, reps = 6, isBodyweight = true, completed = true))
        assertTrue(results("pull", history).single().detail.contains("BW + 12.5 lb × 6"))
        assertTrue(results("landmine", history).single().detail.contains("No sets"))
        assertFalse(results("landmine", history).single().alreadyToday)
    }

    private fun inputs(members: List<Long>) = LearnedInputs(
        asOf = LocalDate.parse("2026-10-04"), sets = emptyList(), cards = emptyList(), resolve = { it },
        slots = listOf(CycleSlot(10, "Push A")),
        slotMembers = members.mapIndexed { index, id -> SplitExercise(index.toLong(), 10, id, index) },
        alternatives = listOf(SplitAlternative(10, 1, 2)),
    )

    @Test fun fourSelectedLiftsRememberFiveWithoutRequiringFive() {
        val model = LearnedSplitsEngine.compute(inputs(listOf(1, 3, 4, 5)))
        val group = model.visibleGroups.single()
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), group.members.toSet())
        assertEquals(setOf(2L), group.optionalMembers)
        val program = buildLearnedPrograms(model, "2026-10-04", exercises, emptyList()) { it }.single()
        assertEquals(4, program.lifts.size)
        assertEquals(TicketRowKind.OPTION, program.rows.single { it.exerciseId == 2L }.kind)
    }

    @Test fun bothVersionsLearningIntoTheCoreStillUseOneDefaultChoice() {
        val model = LearnedSplitsEngine.compute(inputs(listOf(1, 2, 3, 4, 5)))
        val program = buildLearnedPrograms(model, "2026-10-04", exercises, emptyList()) { it }.single()
        assertEquals(5, program.rows.size)
        assertEquals(4, program.lifts.size)
        assertEquals(1, program.rows.count { it.kind == TicketRowKind.OPTION })
    }

    @Test fun aSmallSplitKeepsItsIdentityWhenHalfItsExercisesWereSwapped() {
        val matched = matchSlots(
            listOf(LiftCluster(listOf(2, 3), listOf("2026-10-01", "2026-10-03"))),
            listOf(CycleSlot(10, "Push A", learned = true)), mapOf(10L to listOf(1, 3)), LearnedTunables(),
            listOf(SplitAlternative(10, 1, 2)),
        )
        assertEquals(10L, matched.units.single().slotId)
        assertTrue(matched.duplicateArchives.isEmpty())
    }

    @Test fun chainedAlternativesRemainOneChoiceAndDoNotAffectOtherSplits() {
        val input = inputs(listOf(1, 3, 4, 5)).copy(alternatives = listOf(
            SplitAlternative(10, 1, 2), SplitAlternative(10, 2, 7), SplitAlternative(11, 3, 4),
        ))
        val group = LearnedSplitsEngine.compute(input).visibleGroups.single()
        assertEquals(setOf(2L, 7L), group.optionalMembers)
        assertEquals(6, group.members.size)
        assertEquals(4, group.members.size - group.optionalMembers.size)
    }

    @Test fun splitScopeUsesTheWholeWorkoutAndHonorsAnExplicitChoice() {
        val input = inputs(listOf(1, 3, 4)).copy(
            slots = listOf(CycleSlot(10, "A"), CycleSlot(11, "B")),
            slotMembers = listOf(10L to 1L, 10L to 3L, 10L to 4L, 11L to 1L, 11L to 5L, 11L to 7L)
                .mapIndexed { i, (slot, lift) -> SplitExercise(i.toLong(), slot, lift, i) },
        )
        val model = LearnedSplitsEngine.compute(input)
        assertEquals(11L, swapGroupForDay(model, null, setOf(1, 5, 7))?.slotId)
        assertEquals(10L, swapGroupForDay(model, 10, setOf(1, 5, 7))?.slotId)
        assertNull(swapGroupForDay(model, null, setOf(1)), "A shared exercise alone cannot identify a split")
    }

    @Test fun reusingAnotherSelectedLiftDoesNotMergeIndependentSplitPositions() {
        val input = inputs(listOf(1, 3, 4, 5)).copy(alternatives = listOf(
            SplitAlternative(10, 1, 2), SplitAlternative(10, 2, 3, replacesSlot = false),
        ))
        val group = LearnedSplitsEngine.compute(input).visibleGroups.single()
        assertEquals(setOf(2L), group.optionalMembers)
        val program = buildLearnedPrograms(LearnedSplitsEngine.compute(input), "2026-10-04", exercises, emptyList()) { it }.single()
        assertEquals(4, program.lifts.size)
    }
}
