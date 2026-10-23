package com.ayman.ecolift.data

val selectableBodyPartGroups: List<String> = listOf(
    "CHEST",
    "BACK",
    "SHOULDERS",
    "BICEPS",
    "TRICEPS",
    "QUADS",
    "HAMSTRINGS",
    "GLUTES",
    "CALVES",
    "CORE",
    "FOREARMS",
)

val selectableBodyPartGroupSet: Set<String> = selectableBodyPartGroups.toSet()

fun normalizeBodyPartGroup(raw: String): String? =
    raw.trim().uppercase().takeIf { it in selectableBodyPartGroupSet }

fun selectableBodyPartParts(muscleGroups: String): List<String> =
    muscleGroups
        .split("·")
        .mapNotNull(::normalizeBodyPartGroup)
        .distinct()
