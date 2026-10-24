package com.ayman.ecolift.web.supabase.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The only operations accepted by mutate_workout_set_v1. */
@Serializable
enum class WorkoutSetMutationOperation {
    INSERT,
    UPDATE,
    DELETE,
}

/** Scalar RPC arguments for public.mutate_workout_set_v1. */
@Serializable
data class MutateWorkoutSetV1Params(
    @SerialName("p_schema_version") val schemaVersion: Int,
    @SerialName("p_mutation_id") val mutationId: String,
    @SerialName("p_payload_hash") val payloadHash: String,
    @SerialName("p_operation") val operation: WorkoutSetMutationOperation,
    @SerialName("p_set_sync_id") val setSyncId: String,
    @SerialName("p_expected_entity_revision") val expectedEntityRevision: Long? = null,
    @SerialName("p_expected_workout_revision") val expectedWorkoutRevision: Long? = null,
    @SerialName("p_expected_target_workout_revision") val expectedTargetWorkoutRevision: Long? = null,
    @SerialName("p_exercise_id") val exerciseId: Long? = null,
    @SerialName("p_date") val date: String? = null,
    @SerialName("p_set_number") val setNumber: Int? = null,
    @SerialName("p_weight_lbs") val weightLbs: Int? = null,
    @SerialName("p_reps") val reps: Int? = null,
    @SerialName("p_is_bodyweight") val isBodyweight: Boolean? = null,
    @SerialName("p_completed") val completed: Boolean? = null,
    @SerialName("p_rest_time_seconds") val restTimeSeconds: Int? = null,
    @SerialName("p_is_warmup") val isWarmup: Boolean? = null,
)
