package com.ayman.ecolift.web.supabase.sync

import com.ayman.ecolift.data.WorkoutCardDisplayMode
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Scalar RPC arguments for public.mutate_workout_card_display_v1. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MutateWorkoutCardDisplayV1Params(
    @EncodeDefault @SerialName("p_schema_version") val schemaVersion: Int = 1,
    @SerialName("p_mutation_id") val mutationId: String,
    @SerialName("p_payload_hash") val payloadHash: String,
    @SerialName("p_card_id") val cardId: String,
    @SerialName("p_expected_card_revision") val expectedCardRevision: Long,
    @SerialName("p_display_mode") val displayMode: WorkoutCardDisplayMode,
)
