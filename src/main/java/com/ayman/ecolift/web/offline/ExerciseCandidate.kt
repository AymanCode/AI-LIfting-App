package com.ayman.ecolift.web.offline

import kotlinx.serialization.Serializable

/** An existing server exercise reported when a local name is already taken. */
@Serializable
data class ExerciseCandidate(
    val serverId: Long,
    val name: String,
    val aliases: List<String> = emptyList(),
    val isBodyweight: Boolean,
    val muscleGroups: String,
    val mergedIntoId: Long? = null,
)
