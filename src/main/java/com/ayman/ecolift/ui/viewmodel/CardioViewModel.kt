package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.cardio.CardioActivitySpecs
import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.cardio.CardioEntryParser
import com.ayman.ecolift.cardio.CardioFieldRules
import com.ayman.ecolift.cardio.CardioInsights
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.data.CardioSessionSource
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.VisionService
import com.ayman.ecolift.web.ai.VisionStats
import com.ayman.ecolift.web.demo.DemoGuard
import com.ayman.ecolift.web.pickPhoto
import com.ayman.ecolift.web.util.nowMillis
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.pow
import kotlin.math.round

enum class ReadState { READ, CHECK, UNREAD }
enum class SaveState { IDLE, SAVING, FAILED }

data class CardioDraft(
    val type: CardioActivityType,
    val values: Map<CardioEntryField, Double>,
    val notes: String,
    val readStates: Map<CardioEntryField, ReadState> = emptyMap(),
    val fromPhoto: Boolean = false,
    val scanConfidence: Double? = null,
    val machineType: String? = null,
    val editingId: Long? = null,
    val editingSessionOriginal: CardioSession? = null,
    val invalid: Set<CardioEntryField> = emptySet(),
)

data class CardioToast(
    val text: String,
    val emphasis: String? = null,
    val action: String? = null,
)

data class CardioUiState(
    val sessions: List<CardioSession> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val draft: CardioDraft? = null,
    val expandedField: CardioEntryField? = null,
    val typingField: CardioEntryField? = null,
    val saveState: SaveState = SaveState.IDLE,
    val saveError: String? = null,
    val selectedSessionId: Long? = null,
    val daySheetDate: LocalDate? = null,
    val scanning: Boolean = false,
    val scanPhotoBase64: String? = null,
    val scanPhotoMime: String? = null,
    val scanReadCount: Int = 0,
    val scanFieldCount: Int = 0,
    val scanLowConfidence: Boolean = false,
    val scansLeft: Int? = null,
    val toast: CardioToast? = null,
    val lastSavedId: Long? = null,
    val undoDeadlineMillis: Long? = null,
    val sessionsFilter: CardioActivityType? = null,
)

data class CardioWrite(
    val date: String,
    val activityType: String,
    val activityLabel: String? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val calories: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val avgSpeed: Double? = null,
    val avgInclinePercent: Double? = null,
    val cadenceRpm: Int? = null,
    val resistanceLevel: Double? = null,
    val avgPowerWatts: Int? = null,
    val strokeRateSpm: Int? = null,
    val paceSecPer500m: Int? = null,
    val floors: Int? = null,
    val steps: Int? = null,
    val notes: String = "",
    val machineType: String? = null,
    val ocrConfidence: Double? = null,
    val source: String = CardioSessionSource.MANUAL,
    val editingId: Long? = null,
)

internal object CardioDraftLogic {
    fun newDraft(sessions: List<CardioSession>, today: LocalDate): CardioDraft {
        val lastOverall = sessions.firstOrNull()
        val type = lastOverall?.let { CardioActivitySpecs.fromStoredType(it.activityType) } ?: CardioActivityType.TREADMILL
        val last = CardioInsights.lastOnMachine(sessions, type)
        val values = mutableMapOf<CardioEntryField, Double>()
        if (last != null) {
            values[CardioEntryField.DURATION] = last.durationSec?.toDouble() ?: 1800.0
            if (type == CardioActivityType.STAIR_CLIMBER) {
                last.floors?.let { values[CardioEntryField.FLOORS] = it.toDouble() }
            } else {
                last.distanceM?.let {
                    values[CardioEntryField.DISTANCE_MILES] = CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, type, it)
                }
            }
        } else {
            values[CardioEntryField.DURATION] = 1800.0
        }
        return CardioDraft(
            type = type,
            values = values,
            notes = "",
            readStates = emptyMap(),
            fromPhoto = false,
            invalid = emptySet(),
        )
    }

    fun editDraft(session: CardioSession): CardioDraft {
        val type = CardioActivitySpecs.fromStoredType(session.activityType)
        val values = mutableMapOf<CardioEntryField, Double>()
        session.durationSec?.let { values[CardioEntryField.DURATION] = it.toDouble() }
        session.distanceM?.let { values[CardioEntryField.DISTANCE_MILES] = CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, type, it) }
        session.avgSpeed?.let { values[CardioEntryField.AVG_SPEED_MPH] = CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, type, it) }
        session.avgInclinePercent?.let { values[CardioEntryField.AVG_INCLINE_PERCENT] = it }
        session.calories?.let { values[CardioEntryField.CALORIES] = it.toDouble() }
        session.avgHeartRate?.let { values[CardioEntryField.AVG_HEART_RATE] = it.toDouble() }
        session.maxHeartRate?.let { values[CardioEntryField.MAX_HEART_RATE] = it.toDouble() }
        session.cadenceRpm?.let { values[CardioEntryField.CADENCE_RPM] = it.toDouble() }
        session.resistanceLevel?.let { values[CardioEntryField.RESISTANCE_LEVEL] = it }
        session.avgPowerWatts?.let { values[CardioEntryField.AVG_POWER_WATTS] = it.toDouble() }
        session.strokeRateSpm?.let { values[CardioEntryField.STROKE_RATE_SPM] = it.toDouble() }
        session.paceSecPer500m?.let { values[CardioEntryField.PACE_500M] = it.toDouble() }
        session.floors?.let { values[CardioEntryField.FLOORS] = it.toDouble() }
        session.steps?.let { values[CardioEntryField.STEPS] = it.toDouble() }

        return CardioDraft(
            type = type,
            values = values,
            notes = session.notes,
            fromPhoto = (session.source == CardioSessionSource.OCR),
            machineType = session.machineType,
            scanConfidence = session.ocrConfidence,
            editingId = session.id,
            editingSessionOriginal = session,
            invalid = emptySet(),
        )
    }

    fun applyScrub(draft: CardioDraft, field: CardioEntryField, ticks: Int): CardioDraft {
        val rule = CardioFieldRules.rule(field, draft.type)
        val cur = draft.values[field] ?: rule.min
        var newVal = (cur + ticks * rule.step).coerceIn(rule.min, rule.max)
        newVal = if (rule.decimals == 0) {
            round(newVal)
        } else {
            val factor = 10.0.pow(rule.decimals)
            round(newVal * factor) / factor
        }
        val newReadStates = if (draft.readStates.containsKey(field)) {
            draft.readStates + (field to ReadState.READ)
        } else {
            draft.readStates
        }
        return draft.copy(
            values = draft.values + (field to newVal),
            readStates = newReadStates,
            invalid = draft.invalid - field,
        )
    }

    fun applyTyped(draft: CardioDraft, field: CardioEntryField, text: String): CardioDraft {
        val trimmed = text.trim()
        val parsed = if (field == CardioEntryField.DURATION) {
            CardioEntryParser.parseDurationSeconds(trimmed)?.toDouble()
        } else {
            CardioFieldRules.parse(field, draft.type, trimmed)
        }
        return if (parsed == null || !CardioFieldRules.isPlausible(field, draft.type, parsed)) {
            draft.copy(invalid = draft.invalid + field)
        } else {
            val newReadStates = if (draft.readStates.containsKey(field)) {
                draft.readStates + (field to ReadState.READ)
            } else {
                draft.readStates
            }
            draft.copy(
                values = draft.values + (field to parsed),
                readStates = newReadStates,
                invalid = draft.invalid - field,
            )
        }
    }

    fun reuseField(draft: CardioDraft, field: CardioEntryField, last: CardioSession): CardioDraft {
        val type = draft.type
        val value: Double? = when (field) {
            CardioEntryField.DURATION -> last.durationSec?.toDouble()
            CardioEntryField.DISTANCE_MILES -> last.distanceM?.let { CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, type, it) }
            CardioEntryField.AVG_SPEED_MPH -> last.avgSpeed?.let { CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, type, it) }
            CardioEntryField.AVG_INCLINE_PERCENT -> last.avgInclinePercent
            CardioEntryField.CALORIES -> last.calories?.toDouble()
            CardioEntryField.AVG_HEART_RATE -> last.avgHeartRate?.toDouble()
            CardioEntryField.MAX_HEART_RATE -> last.maxHeartRate?.toDouble()
            CardioEntryField.CADENCE_RPM -> last.cadenceRpm?.toDouble()
            CardioEntryField.RESISTANCE_LEVEL -> last.resistanceLevel
            CardioEntryField.AVG_POWER_WATTS -> last.avgPowerWatts?.toDouble()
            CardioEntryField.STROKE_RATE_SPM -> last.strokeRateSpm?.toDouble()
            CardioEntryField.PACE_500M -> last.paceSecPer500m?.toDouble()
            CardioEntryField.FLOORS -> last.floors?.toDouble()
            CardioEntryField.STEPS -> last.steps?.toDouble()
            CardioEntryField.NOTES -> null
        }
        return if (value != null) {
            val newReadStates = if (draft.readStates.containsKey(field)) draft.readStates + (field to ReadState.READ) else draft.readStates
            draft.copy(
                values = draft.values + (field to value),
                readStates = newReadStates,
                invalid = draft.invalid - field,
            )
        } else {
            draft
        }
    }

    fun fromScan(stats: VisionStats, sessions: List<CardioSession>, today: LocalDate): CardioDraft {
        val type = if (!stats.activityType.isNullOrBlank()) {
            runCatching { CardioActivityType.valueOf(stats.activityType.uppercase()) }.getOrNull()
                ?: machineTypeToActivityType(stats.machineType)
        } else {
            machineTypeToActivityType(stats.machineType)
        }

        val spec = CardioActivitySpecs.specFor(type)
        val last = CardioInsights.lastOnMachine(sessions, type)
        val values = mutableMapOf<CardioEntryField, Double>()
        val readStates = mutableMapOf<CardioEntryField, ReadState>()

        for (field in spec.fields) {
            if (field == CardioEntryField.NOTES) continue

            val statValue: Double? = when (field) {
                CardioEntryField.DURATION -> stats.durationSec?.toDouble()
                CardioEntryField.DISTANCE_MILES -> stats.distanceM?.let { CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, type, it) }
                CardioEntryField.AVG_SPEED_MPH -> stats.avgSpeed?.let { CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, type, it) }
                CardioEntryField.AVG_INCLINE_PERCENT -> stats.avgInclinePercent
                CardioEntryField.CALORIES -> stats.calories?.toDouble()
                CardioEntryField.AVG_HEART_RATE -> stats.avgHeartRate?.toDouble()
                CardioEntryField.MAX_HEART_RATE -> stats.maxHeartRate?.toDouble()
                CardioEntryField.CADENCE_RPM -> stats.cadenceRpm?.toDouble()
                CardioEntryField.RESISTANCE_LEVEL -> stats.resistanceLevel
                CardioEntryField.AVG_POWER_WATTS -> stats.avgPowerWatts?.toDouble()
                CardioEntryField.STROKE_RATE_SPM -> stats.strokeRateSpm?.toDouble()
                CardioEntryField.PACE_500M -> stats.paceSecPer500m?.toDouble()
                CardioEntryField.FLOORS -> stats.floors?.toDouble()
                CardioEntryField.STEPS -> stats.steps?.toDouble()
                CardioEntryField.NOTES -> null
            }

            if (statValue != null) {
                val plausible = CardioFieldRules.isPlausible(field, type, statValue)
                readStates[field] = if (plausible) ReadState.READ else ReadState.CHECK
                values[field] = statValue
            } else {
                readStates[field] = ReadState.UNREAD
                // HR stays absent
                if (field != CardioEntryField.AVG_HEART_RATE && field != CardioEntryField.MAX_HEART_RATE && last != null) {
                    val prefill: Double? = when (field) {
                        CardioEntryField.DURATION -> last.durationSec?.toDouble()
                        CardioEntryField.DISTANCE_MILES -> last.distanceM?.let { CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, type, it) }
                        CardioEntryField.AVG_SPEED_MPH -> last.avgSpeed?.let { CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, type, it) }
                        CardioEntryField.AVG_INCLINE_PERCENT -> last.avgInclinePercent
                        CardioEntryField.CALORIES -> last.calories?.toDouble()
                        CardioEntryField.CADENCE_RPM -> last.cadenceRpm?.toDouble()
                        CardioEntryField.RESISTANCE_LEVEL -> last.resistanceLevel
                        CardioEntryField.AVG_POWER_WATTS -> last.avgPowerWatts?.toDouble()
                        CardioEntryField.STROKE_RATE_SPM -> last.strokeRateSpm?.toDouble()
                        CardioEntryField.PACE_500M -> last.paceSecPer500m?.toDouble()
                        CardioEntryField.FLOORS -> last.floors?.toDouble()
                        CardioEntryField.STEPS -> last.steps?.toDouble()
                        else -> null
                    }
                    if (prefill != null) {
                        values[field] = prefill
                    }
                }
            }
        }

        return CardioDraft(
            type = type,
            values = values,
            notes = "",
            readStates = readStates,
            fromPhoto = true,
            scanConfidence = stats.confidence,
            machineType = stats.machineType,
            invalid = emptySet(),
        )
    }

    fun canSave(draft: CardioDraft): Boolean {
        if (draft.invalid.isNotEmpty()) return false
        return draft.values.isNotEmpty() || draft.notes.isNotBlank()
    }

    fun toWrite(draft: CardioDraft, today: LocalDate = todayLocalDate()): CardioWrite {
        val orig = draft.editingSessionOriginal
        fun Double.roundToIntOrNull() = round(this).toInt()

        val durationSec = if (orig != null && draft.values[CardioEntryField.DURATION] == orig.durationSec?.toDouble()) {
            orig.durationSec
        } else {
            draft.values[CardioEntryField.DURATION]?.roundToIntOrNull()
        }

        val distanceM = if (orig != null && draft.values[CardioEntryField.DISTANCE_MILES] == orig.distanceM?.let { CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, draft.type, it) }) {
            orig.distanceM
        } else {
            draft.values[CardioEntryField.DISTANCE_MILES]?.let { CardioFieldRules.toStorage(CardioEntryField.DISTANCE_MILES, draft.type, it) }
        }

        val avgSpeed = if (orig != null && draft.values[CardioEntryField.AVG_SPEED_MPH] == orig.avgSpeed?.let { CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, draft.type, it) }) {
            orig.avgSpeed
        } else {
            draft.values[CardioEntryField.AVG_SPEED_MPH]?.let { CardioFieldRules.toStorage(CardioEntryField.AVG_SPEED_MPH, draft.type, it) }
        }

        val avgInclinePercent = if (orig != null && draft.values[CardioEntryField.AVG_INCLINE_PERCENT] == orig.avgInclinePercent) {
            orig.avgInclinePercent
        } else {
            draft.values[CardioEntryField.AVG_INCLINE_PERCENT]
        }

        val calories = if (orig != null && draft.values[CardioEntryField.CALORIES] == orig.calories?.toDouble()) {
            orig.calories
        } else {
            draft.values[CardioEntryField.CALORIES]?.roundToIntOrNull()
        }

        val avgHeartRate = if (orig != null && draft.values[CardioEntryField.AVG_HEART_RATE] == orig.avgHeartRate?.toDouble()) {
            orig.avgHeartRate
        } else {
            draft.values[CardioEntryField.AVG_HEART_RATE]?.roundToIntOrNull()
        }

        val maxHeartRate = if (orig != null && draft.values[CardioEntryField.MAX_HEART_RATE] == orig.maxHeartRate?.toDouble()) {
            orig.maxHeartRate
        } else {
            draft.values[CardioEntryField.MAX_HEART_RATE]?.roundToIntOrNull()
        }

        val cadenceRpm = if (orig != null && draft.values[CardioEntryField.CADENCE_RPM] == orig.cadenceRpm?.toDouble()) {
            orig.cadenceRpm
        } else {
            draft.values[CardioEntryField.CADENCE_RPM]?.roundToIntOrNull()
        }

        val resistanceLevel = if (orig != null && draft.values[CardioEntryField.RESISTANCE_LEVEL] == orig.resistanceLevel) {
            orig.resistanceLevel
        } else {
            draft.values[CardioEntryField.RESISTANCE_LEVEL]
        }

        val avgPowerWatts = if (orig != null && draft.values[CardioEntryField.AVG_POWER_WATTS] == orig.avgPowerWatts?.toDouble()) {
            orig.avgPowerWatts
        } else {
            draft.values[CardioEntryField.AVG_POWER_WATTS]?.roundToIntOrNull()
        }

        val strokeRateSpm = if (orig != null && draft.values[CardioEntryField.STROKE_RATE_SPM] == orig.strokeRateSpm?.toDouble()) {
            orig.strokeRateSpm
        } else {
            draft.values[CardioEntryField.STROKE_RATE_SPM]?.roundToIntOrNull()
        }

        val paceSecPer500m = if (orig != null && draft.values[CardioEntryField.PACE_500M] == orig.paceSecPer500m?.toDouble()) {
            orig.paceSecPer500m
        } else {
            draft.values[CardioEntryField.PACE_500M]?.roundToIntOrNull()
        }

        val floors = if (orig != null && draft.values[CardioEntryField.FLOORS] == orig.floors?.toDouble()) {
            orig.floors
        } else {
            draft.values[CardioEntryField.FLOORS]?.roundToIntOrNull()
        }

        val steps = if (orig != null && draft.values[CardioEntryField.STEPS] == orig.steps?.toDouble()) {
            orig.steps
        } else {
            draft.values[CardioEntryField.STEPS]?.roundToIntOrNull()
        }

        val date = orig?.date ?: today.toString()
        val source = if (draft.fromPhoto) CardioSessionSource.OCR else (orig?.source ?: CardioSessionSource.MANUAL)

        return CardioWrite(
            date = date,
            activityType = draft.type.name,
            activityLabel = orig?.activityLabel,
            durationSec = durationSec,
            distanceM = distanceM,
            calories = calories,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            avgSpeed = avgSpeed,
            avgInclinePercent = avgInclinePercent,
            cadenceRpm = cadenceRpm,
            resistanceLevel = resistanceLevel,
            avgPowerWatts = avgPowerWatts,
            strokeRateSpm = strokeRateSpm,
            paceSecPer500m = paceSecPer500m,
            floors = floors,
            steps = steps,
            notes = draft.notes,
            machineType = draft.machineType ?: orig?.machineType,
            ocrConfidence = draft.scanConfidence ?: orig?.ocrConfidence,
            source = source,
            editingId = draft.editingId,
        )
    }

    private fun machineTypeToActivityType(machineType: String?): CardioActivityType = when (machineType?.lowercase()) {
        "treadmill" -> CardioActivityType.TREADMILL
        "bike" -> CardioActivityType.BIKE
        "rower", "row" -> CardioActivityType.ROW
        "elliptical" -> CardioActivityType.ELLIPTICAL
        "stair_climber", "stairs" -> CardioActivityType.STAIR_CLIMBER
        "run" -> CardioActivityType.RUN
        "walk" -> CardioActivityType.WALK
        "swim" -> CardioActivityType.SWIM
        else -> CardioActivityType.TREADMILL
    }
}

class CardioViewModel(
    private val repos: AppRepos,
    private val vision: VisionService,
    private val photoPicker: (((Pair<String, String>?) -> Unit) -> Unit) = ::pickPhoto,
) : ViewModel() {
    private val repository = repos.cardio
    private val draftsByMachine = mutableMapOf<CardioActivityType, CardioDraft>()
    private class ScanRequest
    private var currentScanToken: ScanRequest? = null

    private val _uiState = MutableStateFlow(CardioUiState())
    val uiState: StateFlow<CardioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.observeSessions().collect { sessions ->
                    _uiState.update { it.copy(sessions = sessions, loading = false, loadFailed = false) }
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(loading = false, loadFailed = true) }
            }
        }
        refreshScansLeft()
    }

    fun startNewDraft() {
        currentScanToken = null
        draftsByMachine.clear()
        val draft = CardioDraftLogic.newDraft(repository.sessions.value, todayLocalDate())
        _uiState.update {
            it.copy(
                draft = draft,
                scanning = false,
                scanPhotoBase64 = null,
                scanPhotoMime = null,
                toast = null,
                saveState = SaveState.IDLE,
                saveError = null,
                expandedField = null,
                typingField = null,
            )
        }
    }

    fun startEditDraft(id: Long) {
        currentScanToken = null
        viewModelScope.launch {
            val session = repository.getById(id) ?: return@launch
            draftsByMachine.clear()
            val draft = CardioDraftLogic.editDraft(session)
            _uiState.update {
                it.copy(
                    draft = draft,
                    scanning = false,
                    scanPhotoBase64 = null,
                    scanPhotoMime = null,
                    toast = null,
                    saveState = SaveState.IDLE,
                    saveError = null,
                    expandedField = null,
                    typingField = null,
                )
            }
        }
    }

    fun cancelDraft() {
        currentScanToken = null
        draftsByMachine.clear()
        _uiState.update {
            it.copy(
                draft = null,
                scanning = false,
                scanPhotoBase64 = null,
                scanPhotoMime = null,
                expandedField = null,
                typingField = null,
                saveState = SaveState.IDLE,
                saveError = null,
            )
        }
    }

    fun selectMachine(type: CardioActivityType) {
        val cur = _uiState.value.draft ?: return
        if (cur.type == type) return
        if (cur.fromPhoto) {
            val spec = CardioActivitySpecs.specFor(type)
            val last = CardioInsights.lastOnMachine(repository.sessions.value, type)
            val newValues = mutableMapOf<CardioEntryField, Double>()
            val newReadStates = mutableMapOf<CardioEntryField, ReadState>()
            for (field in spec.fields) {
                if (field == CardioEntryField.NOTES) continue
                if (cur.values.containsKey(field)) {
                    newValues[field] = cur.values[field]!!
                    newReadStates[field] = cur.readStates[field] ?: ReadState.READ
                } else {
                    newReadStates[field] = ReadState.UNREAD
                    if (field != CardioEntryField.AVG_HEART_RATE && field != CardioEntryField.MAX_HEART_RATE && last != null) {
                        val prefill: Double? = when (field) {
                            CardioEntryField.DURATION -> last.durationSec?.toDouble()
                            CardioEntryField.DISTANCE_MILES -> last.distanceM?.let { CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, type, it) }
                            CardioEntryField.AVG_SPEED_MPH -> last.avgSpeed?.let { CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, type, it) }
                            CardioEntryField.AVG_INCLINE_PERCENT -> last.avgInclinePercent
                            CardioEntryField.CALORIES -> last.calories?.toDouble()
                            CardioEntryField.CADENCE_RPM -> last.cadenceRpm?.toDouble()
                            CardioEntryField.RESISTANCE_LEVEL -> last.resistanceLevel
                            CardioEntryField.AVG_POWER_WATTS -> last.avgPowerWatts?.toDouble()
                            CardioEntryField.STROKE_RATE_SPM -> last.strokeRateSpm?.toDouble()
                            CardioEntryField.PACE_500M -> last.paceSecPer500m?.toDouble()
                            CardioEntryField.FLOORS -> last.floors?.toDouble()
                            CardioEntryField.STEPS -> last.steps?.toDouble()
                            else -> null
                        }
                        if (prefill != null) newValues[field] = prefill
                    }
                }
            }
            val nextDraft = cur.copy(
                type = type,
                values = newValues,
                readStates = newReadStates,
                invalid = cur.invalid.filter { spec.fields.contains(it) }.toSet(),
            )
            val readCount = nextDraft.readStates.count { it.value == ReadState.READ || it.value == ReadState.CHECK }
            val fieldCount = spec.fields.count { it != CardioEntryField.NOTES }
            _uiState.update {
                it.copy(
                    draft = nextDraft,
                    scanReadCount = readCount,
                    scanFieldCount = fieldCount,
                    expandedField = null,
                    typingField = null,
                )
            }
            return
        }
        draftsByMachine[cur.type] = cur
        val existing = draftsByMachine[type]
        val nextDraft = existing ?: CardioDraftLogic.newDraft(repository.sessions.value, todayLocalDate()).copy(type = type)
        _uiState.update { it.copy(draft = nextDraft, expandedField = null, typingField = null) }
    }

    fun scrub(field: CardioEntryField, ticks: Int) {
        val cur = _uiState.value.draft ?: return
        val updated = CardioDraftLogic.applyScrub(cur, field, ticks)
        _uiState.update { it.copy(draft = updated) }
    }

    fun reuseLast(field: CardioEntryField) {
        val cur = _uiState.value.draft ?: return
        val last = CardioInsights.lastOnMachine(repository.sessions.value, cur.type) ?: return
        val updated = CardioDraftLogic.reuseField(cur, field, last)
        _uiState.update { it.copy(draft = updated) }
    }

    fun beginTyping(field: CardioEntryField) {
        _uiState.update { it.copy(typingField = field) }
    }

    fun commitTyped(field: CardioEntryField, text: String) {
        val cur = _uiState.value.draft ?: return
        val updated = CardioDraftLogic.applyTyped(cur, field, text)
        _uiState.update { it.copy(draft = updated, typingField = null) }
    }

    fun clearField(field: CardioEntryField) {
        val cur = _uiState.value.draft ?: return
        val updated = cur.copy(
            values = cur.values - field,
            invalid = cur.invalid - field,
        )
        _uiState.update { it.copy(draft = updated) }
    }

    fun setNotes(text: String) {
        val cur = _uiState.value.draft ?: return
        val updated = cur.copy(notes = text)
        _uiState.update { it.copy(draft = updated) }
    }

    fun expandField(field: CardioEntryField?) {
        _uiState.update { it.copy(expandedField = field) }
    }

    fun save() {
        if (DemoGuard.blockIfDemo()) return
        val draft = _uiState.value.draft ?: return
        if (!CardioDraftLogic.canSave(draft)) return

        _uiState.update { it.copy(saveState = SaveState.SAVING, saveError = null) }
        viewModelScope.launch {
            try {
                val write = CardioDraftLogic.toWrite(draft, todayLocalDate())
                val savedSession = if (draft.editingId != null) {
                    val orig = draft.editingSessionOriginal ?: repository.getById(draft.editingId)
                    if (orig != null) {
                        val updated = orig.copy(
                            activityType = write.activityType,
                            activityLabel = write.activityLabel,
                            durationSec = write.durationSec,
                            distanceM = write.distanceM,
                            calories = write.calories,
                            avgHeartRate = write.avgHeartRate,
                            maxHeartRate = write.maxHeartRate,
                            avgSpeed = write.avgSpeed,
                            avgInclinePercent = write.avgInclinePercent,
                            cadenceRpm = write.cadenceRpm,
                            resistanceLevel = write.resistanceLevel,
                            avgPowerWatts = write.avgPowerWatts,
                            strokeRateSpm = write.strokeRateSpm,
                            paceSecPer500m = write.paceSecPer500m,
                            floors = write.floors,
                            steps = write.steps,
                            notes = write.notes,
                        )
                        repository.update(updated)
                    } else {
                        error("Session not found")
                    }
                } else if (draft.fromPhoto) {
                    repository.saveOcrConfirmed(
                        date = write.date,
                        activityType = write.activityType,
                        activityLabel = write.activityLabel,
                        durationSec = write.durationSec,
                        distanceM = write.distanceM,
                        calories = write.calories,
                        avgHeartRate = write.avgHeartRate,
                        maxHeartRate = write.maxHeartRate,
                        avgSpeed = write.avgSpeed,
                        avgInclinePercent = write.avgInclinePercent,
                        cadenceRpm = write.cadenceRpm,
                        resistanceLevel = write.resistanceLevel,
                        avgPowerWatts = write.avgPowerWatts,
                        strokeRateSpm = write.strokeRateSpm,
                        paceSecPer500m = write.paceSecPer500m,
                        floors = write.floors,
                        steps = write.steps,
                        machineType = write.machineType,
                        ocrConfidence = write.ocrConfidence,
                        notes = write.notes,
                    )
                } else {
                    repository.saveManual(
                        date = write.date,
                        activityType = write.activityType,
                        activityLabel = write.activityLabel,
                        durationSec = write.durationSec,
                        distanceM = write.distanceM,
                        calories = write.calories,
                        avgHeartRate = write.avgHeartRate,
                        maxHeartRate = write.maxHeartRate,
                        avgSpeed = write.avgSpeed,
                        avgInclinePercent = write.avgInclinePercent,
                        cadenceRpm = write.cadenceRpm,
                        resistanceLevel = write.resistanceLevel,
                        avgPowerWatts = write.avgPowerWatts,
                        strokeRateSpm = write.strokeRateSpm,
                        paceSecPer500m = write.paceSecPer500m,
                        floors = write.floors,
                        steps = write.steps,
                        notes = write.notes,
                    )
                }

                val isEdit = draft.editingId != null
                val dayName = todayLocalDate().dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                val toast = if (isEdit) {
                    CardioToast(text = "Session updated", emphasis = null, action = null)
                } else {
                    CardioToast(
                        text = "Saved.",
                        emphasis = if (CardioInsights.countsForActivity(savedSession)) "$dayName filled in." else null,
                        action = "Undo",
                    )
                }
                val now = nowMillis()
                _uiState.update {
                    it.copy(
                        draft = null,
                        scanPhotoBase64 = null,
                        scanPhotoMime = null,
                        saveState = SaveState.IDLE,
                        saveError = null,
                        toast = toast,
                        lastSavedId = if (isEdit) null else savedSession.id,
                        undoDeadlineMillis = if (isEdit) null else now + 8000,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        saveState = SaveState.FAILED,
                        saveError = "Couldn't save.",
                    )
                }
            }
        }
    }

    fun undoLastSave() {
        if (DemoGuard.blockIfDemo()) return
        val id = _uiState.value.lastSavedId ?: return
        viewModelScope.launch {
            try {
                repository.delete(id)
                _uiState.update {
                    it.copy(
                        lastSavedId = null,
                        undoDeadlineMillis = null,
                        toast = CardioToast(text = "Removed.", emphasis = null, action = null),
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        toast = CardioToast(text = "Couldn't undo.", emphasis = null, action = null),
                    )
                }
            }
        }
    }

    fun startScan() {
        if (DemoGuard.blockIfDemo()) return
        val token = ScanRequest()
        currentScanToken = token
        draftsByMachine.clear()
        _uiState.update {
            it.copy(
                scanning = true,
                draft = null,
                toast = null,
                scanPhotoBase64 = null,
                scanPhotoMime = null,
                scanReadCount = 0,
                scanFieldCount = 0,
                scanLowConfidence = false,
                expandedField = null,
                typingField = null,
                saveState = SaveState.IDLE,
                saveError = null,
            )
        }
        photoPicker onPhoto@{ photo ->
            if (token !== currentScanToken) return@onPhoto
            if (photo == null) {
                cancelScan()
                return@onPhoto
            }
            _uiState.update {
                it.copy(
                    scanning = true,
                    scanPhotoBase64 = photo.second,
                    scanPhotoMime = photo.first,
                )
            }
            viewModelScope.launch {
                try {
                    val stats = vision.scanStats(photo.second, photo.first)
                    if (token !== currentScanToken) return@launch
                    if (stats == null) {
                        handleScanFailure()
                        return@launch
                    }
                    val draft = CardioDraftLogic.fromScan(stats, repository.sessions.value, todayLocalDate())
                    val lowConf = (stats.confidence ?: 1.0) < 0.6
                    val readCount = draft.readStates.count { it.value == ReadState.READ || it.value == ReadState.CHECK }
                    val fieldCount = CardioActivitySpecs.specFor(draft.type).fields.count { it != CardioEntryField.NOTES }
                    _uiState.update {
                        it.copy(
                            scanning = false,
                            draft = draft,
                            scanLowConfidence = lowConf,
                            scanReadCount = readCount,
                            scanFieldCount = fieldCount,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (token !== currentScanToken) return@launch
                    handleScanFailure()
                }
            }
        }
    }

    private fun handleScanFailure() {
        val sessions = repository.sessions.value
        val today = todayLocalDate()
        val draft = CardioDraftLogic.newDraft(sessions, today)
        val last = CardioInsights.lastOnMachine(sessions, draft.type)
        val toast = CardioToast(
            text = "Couldn't read the photo.",
            emphasis = if (last != null) "Filled from last time." else null,
            action = null,
        )
        _uiState.update {
            it.copy(
                scanning = false,
                draft = draft,
                scanPhotoBase64 = null,
                scanPhotoMime = null,
                toast = toast,
            )
        }
    }

    fun cancelScan() {
        if (currentScanToken == null) return
        currentScanToken = null
        _uiState.update {
            it.copy(
                scanning = false,
                scanPhotoBase64 = null,
                scanPhotoMime = null,
                draft = null,
                expandedField = null,
                typingField = null,
            )
        }
    }

    fun selectSession(id: Long?) {
        _uiState.update { it.copy(selectedSessionId = id) }
    }

    fun openDay(date: LocalDate?) {
        _uiState.update { it.copy(daySheetDate = date) }
    }

    fun deleteSession(id: Long) {
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            repository.delete(id)
            _uiState.update {
                it.copy(
                    selectedSessionId = if (it.selectedSessionId == id) null else it.selectedSessionId,
                )
            }
        }
    }

    fun setSessionsFilter(type: CardioActivityType?) {
        _uiState.update { it.copy(sessionsFilter = type) }
    }

    fun refreshScansLeft() {
        viewModelScope.launch {
            val left = vision.scansLeftToday()
            _uiState.update { it.copy(scansLeft = left) }
        }
    }
}
