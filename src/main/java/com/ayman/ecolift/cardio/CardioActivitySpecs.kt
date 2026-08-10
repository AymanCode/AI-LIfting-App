package com.ayman.ecolift.cardio

import com.ayman.ecolift.data.CardioActivityType

enum class CardioEntryField {
    DURATION,
    DISTANCE_MILES,
    AVG_SPEED_MPH,
    AVG_INCLINE_PERCENT,
    CALORIES,
    AVG_HEART_RATE,
    MAX_HEART_RATE,
    CADENCE_RPM,
    RESISTANCE_LEVEL,
    AVG_POWER_WATTS,
    STROKE_RATE_SPM,
    PACE_500M,
    FLOORS,
    STEPS,
    NOTES,
}

enum class CardioPrimaryMetric {
    DURATION,
    DISTANCE,
    SPEED,
    PACE_500M,
    CADENCE,
    FLOORS,
}

data class CardioActivitySpec(
    val type: CardioActivityType,
    val label: String,
    val supportingLabel: String,
    val primaryMetric: CardioPrimaryMetric,
    val fields: List<CardioEntryField>,
)

object CardioActivitySpecs {
    val entryOrder: List<CardioActivityType> = listOf(
        CardioActivityType.TREADMILL,
        CardioActivityType.RUN,
        CardioActivityType.BIKE,
        CardioActivityType.ROW,
        CardioActivityType.ELLIPTICAL,
        CardioActivityType.STAIR_CLIMBER,
        CardioActivityType.WALK,
        CardioActivityType.SWIM,
        CardioActivityType.OTHER,
    )

    fun fromStoredType(value: String): CardioActivityType =
        runCatching { CardioActivityType.valueOf(value) }.getOrDefault(CardioActivityType.OTHER)

    fun specFor(type: CardioActivityType): CardioActivitySpec = when (type) {
        CardioActivityType.TREADMILL -> CardioActivitySpec(
            type = type,
            label = "Treadmill",
            supportingLabel = "Speed and incline",
            primaryMetric = CardioPrimaryMetric.SPEED,
            fields = listOf(
                CardioEntryField.DURATION,
                CardioEntryField.DISTANCE_MILES,
                CardioEntryField.AVG_SPEED_MPH,
                CardioEntryField.AVG_INCLINE_PERCENT,
                CardioEntryField.CALORIES,
                CardioEntryField.AVG_HEART_RATE,
                CardioEntryField.MAX_HEART_RATE,
                CardioEntryField.NOTES,
            ),
        )

        CardioActivityType.RUN -> CardioActivitySpec(
            type = type,
            label = "Run",
            supportingLabel = "Outdoor distance",
            primaryMetric = CardioPrimaryMetric.DISTANCE,
            fields = standardDistanceFields(),
        )

        CardioActivityType.BIKE -> CardioActivitySpec(
            type = type,
            label = "Bike",
            supportingLabel = "Cadence and power",
            primaryMetric = CardioPrimaryMetric.CADENCE,
            fields = listOf(
                CardioEntryField.DURATION,
                CardioEntryField.DISTANCE_MILES,
                CardioEntryField.AVG_SPEED_MPH,
                CardioEntryField.CADENCE_RPM,
                CardioEntryField.RESISTANCE_LEVEL,
                CardioEntryField.AVG_POWER_WATTS,
                CardioEntryField.CALORIES,
                CardioEntryField.AVG_HEART_RATE,
                CardioEntryField.MAX_HEART_RATE,
                CardioEntryField.NOTES,
            ),
        )

        CardioActivityType.ROW -> CardioActivitySpec(
            type = type,
            label = "Rower",
            supportingLabel = "Split and stroke rate",
            primaryMetric = CardioPrimaryMetric.PACE_500M,
            fields = listOf(
                CardioEntryField.DURATION,
                CardioEntryField.DISTANCE_MILES,
                CardioEntryField.PACE_500M,
                CardioEntryField.STROKE_RATE_SPM,
                CardioEntryField.AVG_POWER_WATTS,
                CardioEntryField.CALORIES,
                CardioEntryField.AVG_HEART_RATE,
                CardioEntryField.MAX_HEART_RATE,
                CardioEntryField.NOTES,
            ),
        )

        CardioActivityType.ELLIPTICAL -> CardioActivitySpec(
            type = type,
            label = "Elliptical",
            supportingLabel = "Resistance and cadence",
            primaryMetric = CardioPrimaryMetric.DURATION,
            fields = listOf(
                CardioEntryField.DURATION,
                CardioEntryField.DISTANCE_MILES,
                CardioEntryField.RESISTANCE_LEVEL,
                CardioEntryField.CADENCE_RPM,
                CardioEntryField.CALORIES,
                CardioEntryField.AVG_HEART_RATE,
                CardioEntryField.MAX_HEART_RATE,
                CardioEntryField.NOTES,
            ),
        )

        CardioActivityType.STAIR_CLIMBER -> CardioActivitySpec(
            type = type,
            label = "Stairs",
            supportingLabel = "Floors and level",
            primaryMetric = CardioPrimaryMetric.FLOORS,
            fields = listOf(
                CardioEntryField.DURATION,
                CardioEntryField.FLOORS,
                CardioEntryField.STEPS,
                CardioEntryField.RESISTANCE_LEVEL,
                CardioEntryField.CALORIES,
                CardioEntryField.AVG_HEART_RATE,
                CardioEntryField.MAX_HEART_RATE,
                CardioEntryField.NOTES,
            ),
        )

        CardioActivityType.WALK -> CardioActivitySpec(
            type = type,
            label = "Walk",
            supportingLabel = "Steady distance",
            primaryMetric = CardioPrimaryMetric.DISTANCE,
            fields = standardDistanceFields(),
        )

        CardioActivityType.SWIM -> CardioActivitySpec(
            type = type,
            label = "Swim",
            supportingLabel = "Duration and distance",
            primaryMetric = CardioPrimaryMetric.DURATION,
            fields = standardDistanceFields(),
        )

        CardioActivityType.OTHER -> CardioActivitySpec(
            type = type,
            label = "Other",
            supportingLabel = "Custom cardio",
            primaryMetric = CardioPrimaryMetric.DURATION,
            fields = standardDistanceFields(),
        )
    }

    fun labelFor(type: CardioActivityType): String = specFor(type).label

    private fun standardDistanceFields(): List<CardioEntryField> = listOf(
        CardioEntryField.DURATION,
        CardioEntryField.DISTANCE_MILES,
        CardioEntryField.CALORIES,
        CardioEntryField.AVG_HEART_RATE,
        CardioEntryField.MAX_HEART_RATE,
        CardioEntryField.NOTES,
    )
}
