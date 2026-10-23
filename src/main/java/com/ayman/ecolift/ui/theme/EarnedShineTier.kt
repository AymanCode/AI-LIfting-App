package com.ayman.ecolift.ui.theme

enum class EarnedShineTier(val displayName: String) {
    MATTE("Matte"),
    SATIN("Satin"),
    GLOSSY("Glossy"),
    LUMINOUS("Luminous"),
    BLING("Bling");

    companion object {
        fun from(count: Int): EarnedShineTier {
            return when {
                count in 0..24 -> MATTE
                count in 25..99 -> SATIN
                count in 100..249 -> GLOSSY
                count in 250..499 -> LUMINOUS
                count >= 500 -> BLING
                else -> MATTE
            }
        }
    }
}
