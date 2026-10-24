package com.ayman.ecolift.data

import androidx.compose.ui.graphics.Color

enum class BodyPartRegion(val displayName: String, val color: Color) {
    CHEST("Chest", Color(0xFFF0643C)),
    BACK("Back", Color(0xFF5F80E6)),
    SHOULDERS("Shoulders", Color(0xFFA98BEB)),
    ARMS("Arms", Color(0xFFF07AA8)),
    LEGS("Legs", Color(0xFF8CC163)),
    CORE("Core", Color(0xFFE9B44C)),
    UNKNOWN("Unknown", Color(0xFFA89C90))
}

object BodyPartRegionMapper {
    fun mapGroupToRegion(group: String): BodyPartRegion {
        return when (group.trim().uppercase()) {
            "CHEST" -> BodyPartRegion.CHEST
            "BACK" -> BodyPartRegion.BACK
            "SHOULDERS" -> BodyPartRegion.SHOULDERS
            "BICEPS", "TRICEPS", "FOREARMS" -> BodyPartRegion.ARMS
            "QUADS", "HAMSTRINGS", "GLUTES", "CALVES" -> BodyPartRegion.LEGS
            "CORE" -> BodyPartRegion.CORE
            else -> BodyPartRegion.UNKNOWN
        }
    }
}
