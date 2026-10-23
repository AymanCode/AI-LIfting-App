package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.selectableBodyPartParts
import com.ayman.ecolift.web.supabase.simpleNameKey

/** One lift on the clipboard's pages: the catalog row plus the last day it had a checked set. */
data class ClipboardLiftUi(
    val exerciseId: Long,
    val name: String,
    val primaryGroup: String?,
    val lastCheckedDate: String?,
)

/** Top set in a lift's completed session: whole pounds (0 for unweighted bodyweight), reps, isBodyweight. */
data class TopSetUi(
    val weight: Int,
    val reps: Int,
    val isBodyweight: Boolean,
)

/**
 * One learned group or hand-made split, in due order ([position] 0 is due, [isExpected]).
 * [lifts] are exactly what Start adds by default; [rows] are every row the ticket shows.
 * [slotId] is negative for a group whose slot is not saved yet. Colour comes from [colorIndex].
 */
data class ClipboardProgramUi(
    val slotId: Long,
    val name: String,
    val position: Int,
    val isExpected: Boolean,
    val lifts: List<ClipboardProgramLiftUi>,
    val lastLoadedDate: String? = null,
    val lastSetCount: Int = 0,
    val colorIndex: Int = position,
    val groupKey: String = "",
    val rows: List<TicketRowUi> = emptyList(),
    val isUnnamed: Boolean = false,
    val ledgerLastDateLabel: String? = null,
    val daysSinceLastSession: Int? = null,
    val activeGroupCount: Int = 0,
    /** False while the saved boards are unread or this split's board is saving: Start waits. */
    val startable: Boolean = true,
)

/** [exerciseId] is the id the split seeds with, which is the id a skip must name. */
data class ClipboardProgramLiftUi(
    val exerciseId: Long,
    val name: String,
    val primaryGroup: String?,
    val lastTopSet: TopSetUi? = null,
    /** The ticket's formatted top set ("22.5×8"); preferred over [lastTopSet] when present. */
    val topSetLabel: String? = null,
)

/** A line the user ticked: a lift from the catalog, or a name written in because it isn't one. */
sealed interface ClipboardPick {
    val key: String
    val label: String

    data class Existing(val exerciseId: Long, val name: String) : ClipboardPick {
        override val key: String get() = "id:$exerciseId"
        override val label: String get() = name
    }

    data class Typed(val name: String) : ClipboardPick {
        override val key: String get() = "new:${simpleNameKey(name)}"
        override val label: String get() = name.trim()
    }
}

/** What a ticked line needs before it can be added. Checking writes nothing. */
sealed interface ClipboardCheck {
    /** Nothing to ask. */
    data object Clear : ClipboardCheck

    /** The written name already is a lift (an exact name or a remembered spelling). */
    data class Exact(val exerciseId: Long, val name: String) : ClipboardCheck

    /** The line looks like another lift, so the note asks "Same lift?". */
    data class LookAlike(
        val exerciseId: Long,
        val name: String,
        val lastCheckedDate: String?,
    ) : ClipboardCheck
}

enum class ClipboardAnswer { Same, Different }

/** A ticked line on its way into the workout, with the note's answer when it needed one. */
data class ClipboardPickRequest(
    val pick: ClipboardPick,
    val lookAlike: ClipboardCheck.LookAlike? = null,
    val answer: ClipboardAnswer? = null,
)

internal fun primaryGroupOf(muscleGroups: String): String? = selectableBodyPartParts(muscleGroups).firstOrNull()

/**
 * Every lift with the last day it had a checked set (warm-ups count; unchecked rows never do),
 * folded onto the keeper when exercises were merged. Newest first; never-logged lifts last, A to Z.
 */
internal fun buildClipboardLifts(
    exercises: List<Exercise>,
    sets: List<WorkoutSet>,
    canonicalId: (Long) -> Long,
): List<ClipboardLiftUi> {
    val lastChecked = HashMap<Long, String>()
    for (set in sets) {
        if (!set.completed) continue
        val id = canonicalId(set.exerciseId)
        val seen = lastChecked[id]
        if (seen == null || set.date > seen) lastChecked[id] = set.date
    }
    return exercises
        .map { exercise ->
            ClipboardLiftUi(
                exerciseId = exercise.id,
                name = exercise.name,
                primaryGroup = primaryGroupOf(exercise.muscleGroups),
                lastCheckedDate = lastChecked[exercise.id],
            )
        }
        .sortedWith(
            compareByDescending<ClipboardLiftUi> { it.lastCheckedDate ?: "" }
                .thenBy { it.name.lowercase() },
        )
}
