package com.ayman.ecolift.cardio

import com.ayman.ecolift.data.CardioActivityType
import kotlin.math.abs
import kotlin.math.round

enum class FieldUnit(val suffix: String) {
    SECONDS(""),
    MILES("mi"),
    METRES("m"),
    FLOORS("floors"),
    BPM("bpm"),
    MPH("mph"),
    PERCENT("%"),
    KCAL("cal"),
    RPM("rpm"),
    LEVEL(""),
    WATTS("w"),
    SPM("spm"),
    SEC_PER_500(""),
    STEPS("steps"),
    TEXT(""),
}

data class FieldRule(
    val field: CardioEntryField,
    val unit: FieldUnit,
    val step: Double,
    val min: Double,
    val max: Double,
    val zeroAllowed: Boolean,
    val decimals: Int,
)

object CardioFieldRules {
    fun rule(field: CardioEntryField, type: CardioActivityType): FieldRule = when (field) {
        CardioEntryField.DURATION -> FieldRule(
            field = field,
            unit = FieldUnit.SECONDS,
            step = 15.0,
            min = 60.0,
            max = 21600.0,
            zeroAllowed = false,
            decimals = 0,
        )
        CardioEntryField.DISTANCE_MILES -> {
            if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) {
                FieldRule(
                    field = field,
                    unit = FieldUnit.METRES,
                    step = 50.0,
                    min = 50.0,
                    max = 50000.0,
                    zeroAllowed = false,
                    decimals = 0,
                )
            } else {
                FieldRule(
                    field = field,
                    unit = FieldUnit.MILES,
                    step = 0.05,
                    min = 0.05,
                    max = 50.0,
                    zeroAllowed = false,
                    decimals = 2,
                )
            }
        }
        CardioEntryField.AVG_SPEED_MPH -> FieldRule(
            field = field,
            unit = FieldUnit.MPH,
            step = 0.1,
            min = 0.5,
            max = 25.0,
            zeroAllowed = false,
            decimals = 1,
        )
        CardioEntryField.AVG_INCLINE_PERCENT -> FieldRule(
            field = field,
            unit = FieldUnit.PERCENT,
            step = 0.5,
            min = -3.0,
            max = 40.0,
            zeroAllowed = true,
            decimals = 1,
        )
        CardioEntryField.CALORIES -> FieldRule(
            field = field,
            unit = FieldUnit.KCAL,
            step = 5.0,
            min = 1.0,
            max = 3000.0,
            zeroAllowed = false,
            decimals = 0,
        )
        CardioEntryField.AVG_HEART_RATE -> FieldRule(
            field = field,
            unit = FieldUnit.BPM,
            step = 1.0,
            min = 40.0,
            max = 220.0,
            zeroAllowed = false,
            decimals = 0,
        )
        CardioEntryField.MAX_HEART_RATE -> FieldRule(
            field = field,
            unit = FieldUnit.BPM,
            step = 1.0,
            min = 40.0,
            max = 220.0,
            zeroAllowed = false,
            decimals = 0,
        )
        CardioEntryField.CADENCE_RPM -> FieldRule(
            field = field,
            unit = FieldUnit.RPM,
            step = 1.0,
            min = 20.0,
            max = 200.0,
            zeroAllowed = false,
            decimals = 0,
        )
        CardioEntryField.RESISTANCE_LEVEL -> FieldRule(
            field = field,
            unit = FieldUnit.LEVEL,
            step = 0.5,
            min = 0.0,
            max = 50.0,
            zeroAllowed = true,
            decimals = 1,
        )
        CardioEntryField.AVG_POWER_WATTS -> FieldRule(
            field = field,
            unit = FieldUnit.WATTS,
            step = 5.0,
            min = 10.0,
            max = 1500.0,
            zeroAllowed = false,
            decimals = 0,
        )
        CardioEntryField.STROKE_RATE_SPM -> FieldRule(
            field = field,
            unit = FieldUnit.SPM,
            step = 1.0,
            min = 10.0,
            max = 60.0,
            zeroAllowed = false,
            decimals = 0,
        )
        CardioEntryField.PACE_500M -> FieldRule(
            field = field,
            unit = FieldUnit.SEC_PER_500,
            step = 1.0,
            min = 60.0,
            max = 360.0,
            zeroAllowed = false,
            decimals = 0,
        )
        CardioEntryField.FLOORS -> FieldRule(
            field = field,
            unit = FieldUnit.FLOORS,
            step = 1.0,
            min = 1.0,
            max = 500.0,
            zeroAllowed = false,
            decimals = 0,
        )
        CardioEntryField.STEPS -> FieldRule(
            field = field,
            unit = FieldUnit.STEPS,
            step = 10.0,
            min = 10.0,
            max = 50000.0,
            zeroAllowed = false,
            decimals = 0,
        )
        CardioEntryField.NOTES -> FieldRule(
            field = field,
            unit = FieldUnit.TEXT,
            step = 0.0,
            min = 0.0,
            max = 0.0,
            zeroAllowed = true,
            decimals = 0,
        )
    }

    fun primaryFields(type: CardioActivityType): List<CardioEntryField> = when (type) {
        CardioActivityType.STAIR_CLIMBER -> listOf(
            CardioEntryField.DURATION,
            CardioEntryField.FLOORS,
            CardioEntryField.AVG_HEART_RATE,
        )
        else -> listOf(
            CardioEntryField.DURATION,
            CardioEntryField.DISTANCE_MILES,
            CardioEntryField.AVG_HEART_RATE,
        )
    }

    fun moreFields(type: CardioActivityType): List<CardioEntryField> {
        val spec = CardioActivitySpecs.specFor(type)
        val primary = primaryFields(type).toSet()
        val withoutPrimaryOrNotes = spec.fields.filter { it !in primary && it != CardioEntryField.NOTES }
        return if (spec.fields.contains(CardioEntryField.NOTES)) {
            withoutPrimaryOrNotes + CardioEntryField.NOTES
        } else {
            withoutPrimaryOrNotes
        }
    }

    fun isPlausible(field: CardioEntryField, type: CardioActivityType, value: Double): Boolean {
        val r = rule(field, type)
        return if (r.zeroAllowed && value == 0.0) true else value in r.min..r.max
    }

    private fun formatThousands(n: Long): String {
        val s = abs(n).toString()
        val sign = if (n < 0) "-" else ""
        val sb = StringBuilder()
        var count = 0
        for (i in s.length - 1 downTo 0) {
            sb.append(s[i])
            count++
            if (count % 3 == 0 && i > 0) {
                sb.append(',')
            }
        }
        return sign + sb.reverse().toString()
    }

    fun display(field: CardioEntryField, type: CardioActivityType, value: Double): String = when (field) {
        CardioEntryField.DURATION -> {
            val totalSec = round(value).toLong()
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            if (h > 0) {
                "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
            } else {
                "$m:${s.toString().padStart(2, '0')}"
            }
        }
        CardioEntryField.PACE_500M -> {
            val totalSec = round(value).toLong()
            val m = totalSec / 60
            val s = totalSec % 60
            "$m:${s.toString().padStart(2, '0')}"
        }
        CardioEntryField.DISTANCE_MILES -> {
            if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) {
                formatThousands(round(value).toLong())
            } else {
                val cents = round(value * 100).toLong()
                val whole = cents / 100
                val frac = (cents % 100).toString().padStart(2, '0')
                "$whole.$frac"
            }
        }
        CardioEntryField.STEPS -> formatThousands(round(value).toLong())
        CardioEntryField.AVG_SPEED_MPH, CardioEntryField.AVG_INCLINE_PERCENT, CardioEntryField.RESISTANCE_LEVEL -> {
            val tenths = round(abs(value) * 10).toLong()
            val whole = tenths / 10
            val frac = tenths % 10
            val sign = if (value < 0 && tenths != 0L) "-" else ""
            "$sign$whole.$frac"
        }
        CardioEntryField.AVG_HEART_RATE,
        CardioEntryField.MAX_HEART_RATE,
        CardioEntryField.CALORIES,
        CardioEntryField.CADENCE_RPM,
        CardioEntryField.AVG_POWER_WATTS,
        CardioEntryField.STROKE_RATE_SPM,
        CardioEntryField.FLOORS -> round(value).toLong().toString()
        CardioEntryField.NOTES -> ""
    }

    fun parse(field: CardioEntryField, type: CardioActivityType, text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        return when (field) {
            CardioEntryField.DURATION, CardioEntryField.PACE_500M -> {
                CardioEntryParser.parseDurationSeconds(trimmed)?.toDouble()
                    ?: trimmed.replace(",", "").toDoubleOrNull()
            }
            else -> {
                trimmed.replace(",", "").toDoubleOrNull()
            }
        }
    }

    fun toStorage(field: CardioEntryField, type: CardioActivityType, value: Double): Double = when (field) {
        CardioEntryField.DISTANCE_MILES -> {
            if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) {
                value
            } else {
                value * 1609.344
            }
        }
        CardioEntryField.AVG_SPEED_MPH -> value * 0.44704
        else -> value
    }

    fun fromStorage(field: CardioEntryField, type: CardioActivityType, stored: Double): Double = when (field) {
        CardioEntryField.DISTANCE_MILES -> {
            if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) {
                stored
            } else {
                stored / 1609.344
            }
        }
        CardioEntryField.AVG_SPEED_MPH -> stored / 0.44704
        else -> stored
    }
}
