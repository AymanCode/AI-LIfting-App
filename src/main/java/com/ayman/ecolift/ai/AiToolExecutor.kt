package com.ayman.ecolift.ai

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.data.FuzzyMatcher
import com.ayman.ecolift.data.SetRepository
import com.ayman.ecolift.data.TempSessionSwapRepository
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutRepository
import com.ayman.ecolift.data.WorkoutSet
import kotlin.math.roundToInt

class AiToolExecutor(
    private val exerciseRepository: ExerciseRepository,
    private val setRepository: SetRepository,
    private val workoutRepository: WorkoutRepository,
    private val tempSessionSwapRepository: TempSessionSwapRepository,
    private val manifestRepository: GymContextManifestRepository,
) {
    suspend fun preview(toolCall: AiToolCall): AiActionPreview {
        return when (toolCall.tool) {
            AiToolName.UpdateSetLog -> {
                val exerciseName = toolCall.exercise ?: "this exercise"
                val dateLabel = toolCall.date?.let(WorkoutDates::formatHeader) ?: "that session"
                val fieldLabel = toolCall.field ?: "value"
                val valueLabel = toolCall.newValue?.toString() ?: "updated value"
                val selectorLabel = if (toolCall.setSelector == "last") "the last set" else "the matching top set"
                AiActionPreview(
                    title = "Update $exerciseName on $dateLabel",
                    detail = "This will change $fieldLabel to $valueLabel on $selectorLabel for that session.",
                )
            }

            AiToolName.ModifyCycle -> {
                val sessionLabel = toolCall.activeSessionLabel
                    ?: toolCall.activeSessionType?.let { "Day ${('A' + it)}" }
                    ?: "the selected day"
                AiActionPreview(
                    title = "Override the next workout",
                    detail = "The split engine will treat $sessionLabel as the next session until you log it.",
                )
            }

            AiToolName.CreateTempSwap -> {
                val source = toolCall.exercise ?: "current lift"
                val target = toolCall.targetExercise ?: "alternate lift"
                val sourceDay = toolCall.activeSessionLabel ?: toolCall.activeSessionType?.let(::dayLabel) ?: "today"
                val targetDay = toolCall.targetSessionLabel ?: toolCall.targetSessionType?.let(::dayLabel) ?: "later this week"
                AiActionPreview(
                    title = "Swap $source with $target",
                    detail = "$source will move off $sourceDay and return on $targetDay for this week only.",
                    confirmLabel = "Create swap",
                )
            }

            AiToolName.Calculate1Rm,
            AiToolName.GetSplitAlternatives,
            AiToolName.AnalyzeEquipment,
            AiToolName.EstimateRelativeLoad,
            AiToolName.None,
            -> AiActionPreview(
                title = "Run calculation",
                detail = "This action does not need confirmation.",
            )
        }
    }

    suspend fun execute(toolCall: AiToolCall): AiExecutionResult {
        return when (toolCall.tool) {
            AiToolName.UpdateSetLog -> executeUpdateSetLog(toolCall)
            AiToolName.ModifyCycle -> executeModifyCycle(toolCall)
            AiToolName.Calculate1Rm -> executeCalculate1Rm(toolCall)
            AiToolName.GetSplitAlternatives -> executeGetSplitAlternatives(toolCall)
            AiToolName.CreateTempSwap -> executeCreateTempSwap(toolCall)
            AiToolName.AnalyzeEquipment -> executeAnalyzeEquipment(toolCall)
            AiToolName.EstimateRelativeLoad -> executeEstimateRelativeLoad(toolCall)
            AiToolName.None -> AiExecutionResult(
                title = "No action taken",
                detail = "The model did not request a tool.",
            )
        }
    }

    private suspend fun executeUpdateSetLog(toolCall: AiToolCall): AiExecutionResult {
        val exerciseName = toolCall.exercise
            ?: return AiExecutionResult("Missing exercise", "I need the exercise name to update a historical set.")
        val date = toolCall.date
            ?: return AiExecutionResult("Missing date", "I need a workout date in YYYY-MM-DD format.")
        val field = toolCall.field?.lowercase()
            ?: return AiExecutionResult("Missing field", "I need to know whether to change weight or reps.")
        val newValue = toolCall.newValue
            ?: return AiExecutionResult("Missing value", "I need the new numeric value to apply.")
        val exercise = resolveExercise(exerciseName)
            ?: return AiExecutionResult(
                title = "Exercise not found",
                detail = "I could not match $exerciseName to an exercise in your local library.",
            )
        val matchingSets = setRepository.getSetsForDate(date)
            .filter { it.exerciseId == exercise.id }
            .sortedBy { it.setNumber }
        if (matchingSets.isEmpty()) {
            return AiExecutionResult(
                title = "No session found",
                detail = "There are no logged sets for ${exercise.name} on $date.",
            )
        }
        val target = chooseTargetSet(matchingSets, field, toolCall.setSelector)
        val updated = when (field) {
            "weight", "weight_lbs" -> target.copy(weightLbs = newValue.coerceAtLeast(0))
            "reps" -> target.copy(reps = newValue.coerceAtLeast(0))
            else -> {
                return AiExecutionResult(
                    title = "Unsupported field",
                    detail = "I can currently edit weight or reps for historical sets.",
                )
            }
        }
        setRepository.updateSet(updated)
        val detail = when (field) {
            "reps" -> "Updated ${exercise.name} set ${target.setNumber} on $date to ${updated.reps} reps."
            else -> "Updated ${exercise.name} set ${target.setNumber} on $date to ${updated.weightLbs} lbs."
        }
        return AiExecutionResult(
            title = "Historical log updated",
            detail = detail,
        )
    }

    private suspend fun executeModifyCycle(toolCall: AiToolCall): AiExecutionResult {
        val cycle = workoutRepository.getCycle()
        if (!cycle.isActive) {
            return AiExecutionResult(
                title = "Cycle disabled",
                detail = "Turn the split cycle on before setting the next workout override.",
            )
        }
        val slotType = resolveSlotType(
            explicitType = toolCall.activeSessionType,
            explicitLabel = toolCall.activeSessionLabel,
            numTypes = cycle.numTypes,
        )
            ?: return AiExecutionResult(
                title = "Unknown session",
                detail = "I could not map that request to one of your split day types.",
            )
        workoutRepository.setNextSessionType(slotType)
        manifestRepository.refresh()
        return AiExecutionResult(
            title = "Next workout updated",
            detail = "The next split suggestion is now ${dayLabel(slotType)}.",
        )
    }

    private fun executeCalculate1Rm(toolCall: AiToolCall): AiExecutionResult {
        val weight = toolCall.weight
            ?: return AiExecutionResult("Missing weight", "I need the lifted weight to estimate 1RM.")
        val reps = toolCall.reps
            ?: return AiExecutionResult("Missing reps", "I need the reps completed to estimate 1RM.")
        val estimate = (weight * (1 + reps / 30.0)).roundToInt()
        return AiExecutionResult(
            title = "Estimated 1RM",
            detail = "$weight lbs for $reps reps estimates to roughly $estimate lbs.",
        )
    }

    private suspend fun executeGetSplitAlternatives(toolCall: AiToolCall): AiExecutionResult {
        val date = toolCall.date ?: WorkoutDates.today()
        val cycle = workoutRepository.getCycle()
        if (!cycle.isActive || cycle.numTypes <= 1) {
            return AiExecutionResult(
                title = "Split inactive",
                detail = "Turn on the split cycle and assign workout days before asking for dynamic swaps.",
            )
        }
        val sourceSlotType = workoutRepository.getWorkoutDay(date)?.cycleSlotType
            ?: cycle.nextSessionType
            ?: 0
        val sourceExercise = resolveCurrentExercise(date, sourceSlotType, toolCall.exercise)
            ?: return AiExecutionResult(
                title = "No blocked lift found",
                detail = "I could not determine which exercise to swap out for this session.",
            )
        val sourcePattern = ExercisePatternMatcher.classify(sourceExercise.name)
        val alternatives = (0 until cycle.numTypes)
            .filter { it != sourceSlotType }
            .mapNotNull { targetSlotType ->
                val candidate = latestTemplateExercises(date, targetSlotType)
                    .map { exercise -> exercise to compatibilityScore(sourcePattern, ExercisePatternMatcher.classify(exercise.name)) }
                    .filter { it.second > 0 }
                    .maxByOrNull { it.second }
                    ?: return@mapNotNull null
                SplitAlternative(
                    targetSlotType = targetSlotType,
                    exercise = candidate.first,
                    score = candidate.second,
                )
            }
            .sortedByDescending { it.score }
        val best = alternatives.firstOrNull()
            ?: return AiExecutionResult(
                title = "No clean alternative found",
                detail = "I could not find a compatible lift in the other split templates for this week.",
            )
        val pendingCall = AiToolCall(
            tool = AiToolName.CreateTempSwap,
            requiresConfirmation = true,
            exercise = sourceExercise.name,
            targetExercise = best.exercise.name,
            date = date,
            activeSessionType = sourceSlotType,
            activeSessionLabel = dayLabel(sourceSlotType),
            targetSessionType = best.targetSlotType,
            targetSessionLabel = dayLabel(best.targetSlotType),
        )
        val preview = preview(pendingCall)
        return AiExecutionResult(
            title = "Swap ready",
            detail = "I found ${best.exercise.name} on ${dayLabel(best.targetSlotType)} as the cleanest swap for ${sourceExercise.name}.",
            pendingToolCall = pendingCall,
            pendingPreview = preview,
        )
    }

    private suspend fun executeCreateTempSwap(toolCall: AiToolCall): AiExecutionResult {
        val date = toolCall.date ?: WorkoutDates.today()
        val sourceSlotType = resolveSlotType(
            explicitType = toolCall.activeSessionType,
            explicitLabel = toolCall.activeSessionLabel,
            numTypes = workoutRepository.getCycle().numTypes,
        ) ?: return AiExecutionResult(
            title = "Missing source day",
            detail = "I could not determine the day you want to swap out.",
        )
        val targetSlotType = resolveSlotType(
            explicitType = toolCall.targetSessionType,
            explicitLabel = toolCall.targetSessionLabel,
            numTypes = workoutRepository.getCycle().numTypes,
        ) ?: return AiExecutionResult(
            title = "Missing target day",
            detail = "I could not determine which future split day should receive the swapped lift.",
        )
        val sourceExerciseName = toolCall.exercise
            ?: return AiExecutionResult("Missing source exercise", "I need the blocked lift name to create a swap.")
        val sourceExercise = resolveExercise(sourceExerciseName)
            ?: return AiExecutionResult("Unknown source exercise", "I could not match $sourceExerciseName to a saved exercise.")
        val targetExerciseName = toolCall.targetExercise
            ?: return AiExecutionResult("Missing target exercise", "I need the alternate lift name to create a swap.")
        val targetExercise = resolveExercise(targetExerciseName)
            ?: return AiExecutionResult("Unknown target exercise", "I could not match $targetExerciseName to a saved exercise.")
        tempSessionSwapRepository.createSwap(
            date = date,
            sourceSlotType = sourceSlotType,
            sourceExerciseId = sourceExercise.id,
            targetSlotType = targetSlotType,
            targetExerciseId = targetExercise.id,
        )
        tempSessionSwapRepository.applySwapsToDate(date, sourceSlotType)
        manifestRepository.refresh(date)
        return AiExecutionResult(
            title = "Temporary swap created",
            detail = "${sourceExercise.name} now swaps with ${targetExercise.name} for this week. ${sourceExercise.name} will show back up on ${dayLabel(targetSlotType)}.",
        )
    }

    private suspend fun executeAnalyzeEquipment(toolCall: AiToolCall): AiExecutionResult {
        val machineName = toolCall.machineName ?: toolCall.exercise
            ?: return AiExecutionResult(
                title = "Missing equipment",
                detail = "I need either the analyzed machine name or an image-derived description.",
            )
        val pattern = ExercisePatternMatcher.classify(machineName)
        val closestExercise = bestHistoricalMatchForPattern(pattern)
        val mechanics = toolCall.machineMechanics?.takeIf { it.isNotBlank() }
        val detail = buildString {
            append("This looks most similar to ")
            append(closestExercise?.name ?: "a known ${pattern.name.lowercase()} movement")
            append(".")
            if (mechanics != null) {
                append(" Mechanics: ")
                append(mechanics)
                append(".")
            }
        }
        return AiExecutionResult(
            title = "Equipment analyzed",
            detail = detail,
        )
    }

    private suspend fun executeEstimateRelativeLoad(toolCall: AiToolCall): AiExecutionResult {
        val machineName = toolCall.machineName ?: toolCall.exercise
            ?: return AiExecutionResult(
                title = "Missing machine name",
                detail = "I need the machine name or movement description to estimate a matching load.",
            )
        val benchmark = toolCall.weight ?: findBenchmarkWeight(toolCall.exercise, machineName)
        if (benchmark == null || benchmark <= 0) {
            return AiExecutionResult(
                title = "No benchmark found",
                detail = "I could not find a strong enough historical benchmark to estimate the machine load yet.",
            )
        }
        val estimate = ExercisePatternMatcher.estimateRelativeLoad(machineName, benchmark)
        val detail = if (estimate.perSideLoad != null) {
            "Based on a ${benchmark} lbs benchmark, start around ${estimate.perSideLoad} lbs per side (${estimate.totalLoad} lbs total) for 12-15 reps."
        } else {
            "Based on a ${benchmark} lbs benchmark, start around ${estimate.totalLoad} lbs for 12-15 reps."
        }
        return AiExecutionResult(
            title = "Relative load estimate",
            detail = detail,
        )
    }

    private suspend fun resolveExercise(name: String): Exercise? {
        val exact = exerciseRepository.findExact(name)
        if (exact != null) {
            return exact
        }
        val normalized = exerciseRepository.normalizeName(name)
        return exerciseRepository.getAll()
            .map { exercise ->
                exercise to FuzzyMatcher.levenshteinDistance(normalized, exercise.name)
            }
            .filter { it.second <= 3 }
            .sortedWith(compareBy<Pair<Exercise, Int>> { it.second }.thenBy { it.first.name })
            .firstOrNull()
            ?.first
    }

    private fun chooseTargetSet(
        matchingSets: List<WorkoutSet>,
        field: String,
        setSelector: String?,
    ): WorkoutSet {
        return when {
            setSelector == "last" -> matchingSets.maxByOrNull { it.setNumber } ?: matchingSets.last()
            setSelector == "max_weight" -> matchingSets.maxByOrNull { it.weightLbs * 1000 + it.setNumber } ?: matchingSets.last()
            field == "reps" -> matchingSets.maxByOrNull { it.setNumber } ?: matchingSets.last()
            else -> matchingSets.maxByOrNull { it.weightLbs * 1000 + it.setNumber } ?: matchingSets.last()
        }
    }

    private fun resolveSlotType(
        explicitType: Int?,
        explicitLabel: String?,
        numTypes: Int,
    ): Int? {
        explicitType?.let { type ->
            if (type in 0 until numTypes) return type
        }
        val label = explicitLabel?.trim()?.uppercase() ?: return null
        if (label.startsWith("DAY ") && label.length >= 5) {
            val letter = label.last()
            val type = letter.code - 'A'.code
            if (type in 0 until numTypes) {
                return type
            }
        }
        return null
    }

    private fun dayLabel(slotType: Int): String = "Day ${('A' + slotType)}"

    private suspend fun resolveCurrentExercise(
        date: String,
        slotType: Int,
        requestedName: String?,
    ): Exercise? {
        if (!requestedName.isNullOrBlank()) {
            return resolveExercise(requestedName)
        }
        val todayExerciseId = setRepository.getSetsForDate(date)
            .sortedWith(compareBy<WorkoutSet> { it.exerciseId }.thenBy { it.setNumber })
            .firstOrNull()
            ?.exerciseId
        if (todayExerciseId != null) {
            return exerciseRepository.getById(todayExerciseId)
        }
        return latestTemplateExercises(date, slotType).firstOrNull()
    }

    private suspend fun latestTemplateExercises(date: String, slotType: Int): List<Exercise> {
        val latestDay = workoutRepository.getLatestAssignedDayBefore(
            date = WorkoutDates.addDays(date, 1),
            slotType = slotType,
        ) ?: return emptyList()
        return setRepository.getSetsForDate(latestDay.date)
            .mapNotNull { set -> exerciseRepository.getById(set.exerciseId) }
            .distinctBy { it.id }
    }

    private suspend fun bestHistoricalMatchForPattern(pattern: MovementPattern): Exercise? {
        return exerciseRepository.getAll()
            .map { exercise -> exercise to compatibilityScore(pattern, ExercisePatternMatcher.classify(exercise.name)) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Exercise, Int>> { it.second }.thenBy { it.first.name })
            .firstOrNull()
            ?.first
    }

    private suspend fun findBenchmarkWeight(exerciseName: String?, machineName: String): Int? {
        val allSets = setRepository.getAllSets()
        val allExercises = exerciseRepository.getAll().associateBy { it.id }
        val explicitExercise = if (exerciseName.isNullOrBlank()) {
            null
        } else {
            resolveExercise(exerciseName)
        }
        if (explicitExercise != null) {
            return allSets.filter { it.exerciseId == explicitExercise.id }.maxOfOrNull { it.weightLbs }
        }
        val targetPattern = ExercisePatternMatcher.classify(machineName)
        return allSets
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, sets) ->
                val exercise = allExercises[exerciseId] ?: return@mapNotNull null
                val score = compatibilityScore(targetPattern, ExercisePatternMatcher.classify(exercise.name))
                if (score <= 0) return@mapNotNull null
                score to (sets.maxOfOrNull { it.weightLbs } ?: return@mapNotNull null)
            }
            .sortedByDescending { it.first }
            .firstOrNull()
            ?.second
    }

    private fun compatibilityScore(source: MovementPattern, candidate: MovementPattern): Int {
        if (source == MovementPattern.Unknown || candidate == MovementPattern.Unknown) return 0
        if (source == candidate) return 5
        return when {
            source.isPress() && candidate.isPress() -> 4
            source.isPull() && candidate.isPull() -> 4
            source.isLower() && candidate.isLower() -> 4
            else -> 0
        }
    }
}

private data class SplitAlternative(
    val targetSlotType: Int,
    val exercise: Exercise,
    val score: Int,
)

private fun MovementPattern.isPress(): Boolean = this in setOf(
    MovementPattern.HorizontalPress,
    MovementPattern.InclinePress,
    MovementPattern.VerticalPress,
    MovementPattern.ChestFly,
)

private fun MovementPattern.isPull(): Boolean = this in setOf(
    MovementPattern.HorizontalPull,
    MovementPattern.VerticalPull,
    MovementPattern.Curl,
    MovementPattern.Triceps,
)

private fun MovementPattern.isLower(): Boolean = this in setOf(
    MovementPattern.Squat,
    MovementPattern.Hinge,
    MovementPattern.Calves,
)
