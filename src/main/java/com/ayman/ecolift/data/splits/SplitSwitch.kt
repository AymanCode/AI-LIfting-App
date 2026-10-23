package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet

sealed interface SplitSwitchTarget {
    /** [skippedExerciseIds] are left out of the seed for this load only (the clipboard's unticked
     *  lines). Empty loads the whole split, as every other caller does. */
    data class Saved(
        val slotId: Long,
        val skippedExerciseIds: Set<Long> = emptySet(),
    ) : SplitSwitchTarget
    data object Clear : SplitSwitchTarget
    /**
     * A learned proposal: seeds exactly [exerciseIds] (each lift's whole last session). With Smart
     * Suggest on it leaves the day's split untouched, because a day belongs to the group its checked
     * sets vote for, never to what was loaded. Off, the day stores [slotId] as a saved split does.
     * [label] names the Undo pill.
     */
    data class Proposal(
        val exerciseIds: List<Long>,
        val label: String,
        val slotId: Long?,
    ) : SplitSwitchTarget
}

enum class SplitSwitchIntent {
    UserSelection,
    DeepLinkLoad,
}

data class SplitSwitchRequest(
    val requestId: Long,
    val date: String,
    val target: SplitSwitchTarget,
    val intent: SplitSwitchIntent,
)

sealed interface SplitSwitchPlan {
    data class Apply(val request: SplitSwitchRequest) : SplitSwitchPlan
    data class NeedsConfirm(
        val request: SplitSwitchRequest,
        val affectedSetCount: Int,
    ) : SplitSwitchPlan
    data object NoOp : SplitSwitchPlan
}

sealed interface SplitSwitchOutcome {
    data class Applied(val requestId: Long, val date: String) : SplitSwitchOutcome
    data class Cancelled(val requestId: Long) : SplitSwitchOutcome
    data class Failed(val requestId: Long, val message: String) : SplitSwitchOutcome
}

data class SplitSwitchSetSignature(
    val exerciseId: Long,
    val date: String,
    val setNumber: Int,
    val weightLbs: Int?,
    val reps: Int?,
    val isBodyweight: Boolean,
    val completed: Boolean,
    val restTimeSeconds: Int?,
)

data class SplitSwitchPrecondition(
    val workoutDay: WorkoutDay?,
    val daySets: List<SplitSwitchSetSignature>,
    val baselineSeed: List<SplitSwitchSetSignature>,
    val desiredSeed: List<SplitSwitchSetSignature>,
) {
    companion object {
        fun capture(
            workoutDay: WorkoutDay?,
            daySets: List<WorkoutSet>,
            baselineSeed: List<WorkoutSet>,
            desiredSeed: List<WorkoutSet>,
        ): SplitSwitchPrecondition = SplitSwitchPrecondition(
            workoutDay = workoutDay,
            daySets = daySets.toSignatures(),
            baselineSeed = baselineSeed.toSignatures(),
            desiredSeed = desiredSeed.toSignatures(),
        )
    }
}

object SplitSwitch {
    fun isDirty(
        daySets: List<WorkoutSet>,
        seedSets: List<WorkoutSet>,
    ): Boolean {
        if (daySets.any { it.completed }) return true

        // Compared as multisets, not as maps keyed by natural key. Two rows can share
        // (exerciseId, setNumber) locally: migration 0004 only deduplicated the server and
        // constrains it there, while optimistic rows still queued on this device can briefly
        // collide. Keying by natural key would silently drop the duplicate and report an
        // edited day as clean, which deletes logged work without ever asking.
        return daySets.dirtySignatures() != seedSets.dirtySignatures()
    }

    private fun List<WorkoutSet>.dirtySignatures(): List<DirtySignature> =
        map {
            DirtySignature(
                exerciseId = it.exerciseId,
                setNumber = it.setNumber,
                weightLbs = it.weightLbs,
                reps = it.reps,
                isBodyweight = it.isBodyweight,
                restTimeSeconds = it.restTimeSeconds,
            )
        }.sortedWith(
            compareBy<DirtySignature>(
                { it.exerciseId },
                { it.setNumber },
                { it.weightLbs },
                { it.reps },
                { it.isBodyweight },
                { it.restTimeSeconds },
            ),
        )

    /** The fields that decide whether the user changed a generated row. Excludes the database
     *  id, which is not part of seed identity, and completion, which is checked separately. */
    private data class DirtySignature(
        val exerciseId: Long,
        val setNumber: Int,
        val weightLbs: Int?,
        val reps: Int?,
        val isBodyweight: Boolean,
        val restTimeSeconds: Int?,
    )

    fun plan(
        request: SplitSwitchRequest,
        currentAssignedSlotId: Long?,
        daySets: List<WorkoutSet>,
        seedSets: List<WorkoutSet>,
    ): SplitSwitchPlan {
        val target = request.target
        val plannedRequest = if (
            request.intent == SplitSwitchIntent.UserSelection &&
            target is SplitSwitchTarget.Saved &&
            target.slotId == currentAssignedSlotId
        ) {
            request.copy(target = SplitSwitchTarget.Clear)
        } else {
            request
        }

        if (plannedRequest.target == SplitSwitchTarget.Clear && daySets.isEmpty() && currentAssignedSlotId == null) {
            return SplitSwitchPlan.NoOp
        }

        return if (isDirty(daySets, seedSets)) {
            SplitSwitchPlan.NeedsConfirm(
                request = plannedRequest,
                affectedSetCount = daySets.size,
            )
        } else {
            SplitSwitchPlan.Apply(plannedRequest)
        }
    }
}

private fun List<WorkoutSet>.toSignatures(): List<SplitSwitchSetSignature> =
    map { set ->
        SplitSwitchSetSignature(
            exerciseId = set.exerciseId,
            date = set.date,
            setNumber = set.setNumber,
            weightLbs = set.weightLbs,
            reps = set.reps,
            isBodyweight = set.isBodyweight,
            completed = set.completed,
            restTimeSeconds = set.restTimeSeconds,
        )
    }.sortedWith(
        compareBy<SplitSwitchSetSignature>(
            { it.date },
            { it.exerciseId },
            { it.setNumber },
            { it.weightLbs },
            { it.reps },
            { it.isBodyweight },
            { it.completed },
            { it.restTimeSeconds },
        ),
    )
