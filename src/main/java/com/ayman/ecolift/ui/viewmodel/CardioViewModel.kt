package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.cardio.CardioActivitySpecs
import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.cardio.CardioEntryParser
import com.ayman.ecolift.cardio.health.CardioHealthConnectReader
import com.ayman.ecolift.cardio.ocr.CardioOcrResult
import com.ayman.ecolift.cardio.ocr.MlKitCardioOcrEngine
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioRepository
import com.ayman.ecolift.data.CardioSession
import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardioUiState(
    val sessions: List<CardioSession> = emptyList(),
    val todayLoggedCalories: Int = 0,
    val healthConnectAvailable: Boolean = false,
    val healthConnectPermissionGranted: Boolean = false,
    val healthConnectCaloriesToday: Int? = null,
    val healthConnectMessage: String? = null,
    val selectedActivityType: CardioActivityType = CardioActivityType.RUN,
    val durationText: String = "",
    val distanceText: String = "",
    val avgSpeedText: String = "",
    val inclineText: String = "",
    val caloriesText: String = "",
    val heartRateText: String = "",
    val maxHeartRateText: String = "",
    val cadenceText: String = "",
    val resistanceText: String = "",
    val powerText: String = "",
    val strokeRateText: String = "",
    val pace500Text: String = "",
    val floorsText: String = "",
    val stepsText: String = "",
    val notesText: String = "",
    val entryError: String? = null,
    val entrySource: String = "manual",
    val selectedFilter: CardioActivityType? = null,
    val selectedSessionId: Long? = null,
    val editingSessionId: Long? = null,
    val lastOcrResult: CardioOcrResult? = null,
    val ocrMessage: String? = null,
    val isOcrRunning: Boolean = false,
    val showEntrySheet: Boolean = false,
) {
    val canSaveEntry: Boolean
        get() {
            val fields = CardioActivitySpecs.specFor(selectedActivityType).fields.toSet()
            return CardioEntryParser.hasSaveableMetric(
                durationSec = CardioEntryParser.parseDurationSeconds(durationText).takeIf { CardioEntryField.DURATION in fields },
                distanceM = CardioEntryParser.parseMilesToMeters(distanceText).takeIf { CardioEntryField.DISTANCE_MILES in fields },
                calories = CardioEntryParser.parseInt(caloriesText).takeIf { CardioEntryField.CALORIES in fields },
                avgHeartRate = CardioEntryParser.parseInt(heartRateText).takeIf { CardioEntryField.AVG_HEART_RATE in fields },
                maxHeartRate = CardioEntryParser.parseInt(maxHeartRateText).takeIf { CardioEntryField.MAX_HEART_RATE in fields },
                avgSpeed = CardioEntryParser.parseMphToMetersPerSecond(avgSpeedText).takeIf { CardioEntryField.AVG_SPEED_MPH in fields },
                avgInclinePercent = CardioEntryParser.parseDouble(inclineText).takeIf { CardioEntryField.AVG_INCLINE_PERCENT in fields },
                cadenceRpm = CardioEntryParser.parseInt(cadenceText).takeIf { CardioEntryField.CADENCE_RPM in fields },
                resistanceLevel = CardioEntryParser.parseDouble(resistanceText).takeIf { CardioEntryField.RESISTANCE_LEVEL in fields },
                avgPowerWatts = CardioEntryParser.parseInt(powerText).takeIf { CardioEntryField.AVG_POWER_WATTS in fields },
                strokeRateSpm = CardioEntryParser.parseInt(strokeRateText).takeIf { CardioEntryField.STROKE_RATE_SPM in fields },
                paceSecPer500m = CardioEntryParser.parsePaceSeconds(pace500Text).takeIf { CardioEntryField.PACE_500M in fields },
                floors = CardioEntryParser.parseInt(floorsText).takeIf { CardioEntryField.FLOORS in fields },
                steps = CardioEntryParser.parseInt(stepsText).takeIf { CardioEntryField.STEPS in fields },
                notes = notesText.takeIf { CardioEntryField.NOTES in fields }.orEmpty(),
            )
        }
}

class CardioViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val repository = CardioRepository(db)
    private val ocrEngine = MlKitCardioOcrEngine()
    private val healthReader = CardioHealthConnectReader(appContext)

    private var pendingOcrFile: File? = null
    private var lastManualActivity: CardioActivityType = CardioActivityType.TREADMILL

    private val _uiState = MutableStateFlow(CardioUiState())
    val uiState: StateFlow<CardioUiState> = _uiState.asStateFlow()

    val healthConnectPermissions: Set<String> = healthReader.permissions

    init {
        cleanupStaleOcrFiles()
        _uiState.update { it.copy(healthConnectAvailable = healthReader.isAvailable()) }
        viewModelScope.launch {
            repository.observeSessions().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
        viewModelScope.launch {
            repository.observeCaloriesForDate(LocalDate.now().toString()).collect { calories ->
                _uiState.update { it.copy(todayLoggedCalories = calories) }
            }
        }
        refreshHealthConnectCalories()
    }

    fun openManualEntry() {
        _uiState.update {
            it.copy(
                showEntrySheet = true,
                entrySource = "manual",
                editingSessionId = null,
                selectedActivityType = lastManualActivity,
                lastOcrResult = null,
                ocrMessage = null,
                entryError = null,
            )
        }
    }

    fun openEditEntry(id: Long) {
        viewModelScope.launch {
            val session = repository.getById(id) ?: return@launch
            val type = CardioActivitySpecs.fromStoredType(session.activityType)
            _uiState.update {
                it.clearEntryFields().copy(
                    showEntrySheet = true,
                    selectedSessionId = null,
                    editingSessionId = session.id,
                    entrySource = "manual",
                    selectedActivityType = type,
                    durationText = session.durationSec?.let(::formatDuration).orEmpty(),
                    distanceText = session.distanceM?.let(::formatMiles).orEmpty(),
                    avgSpeedText = session.avgSpeed?.let(::formatMph).orEmpty(),
                    inclineText = session.avgInclinePercent?.let(::formatDecimal).orEmpty(),
                    caloriesText = session.calories?.toString().orEmpty(),
                    heartRateText = session.avgHeartRate?.toString().orEmpty(),
                    maxHeartRateText = session.maxHeartRate?.toString().orEmpty(),
                    cadenceText = session.cadenceRpm?.toString().orEmpty(),
                    resistanceText = session.resistanceLevel?.let(::formatDecimal).orEmpty(),
                    powerText = session.avgPowerWatts?.toString().orEmpty(),
                    strokeRateText = session.strokeRateSpm?.toString().orEmpty(),
                    pace500Text = session.paceSecPer500m?.let(::formatDuration).orEmpty(),
                    floorsText = session.floors?.toString().orEmpty(),
                    stepsText = session.steps?.toString().orEmpty(),
                    notesText = session.notes,
                    lastOcrResult = null,
                    ocrMessage = null,
                    entryError = null,
                )
            }
        }
    }

    fun closeEntrySheet() {
        _uiState.update { it.copy(showEntrySheet = false, editingSessionId = null).clearEntryFields() }
    }

    fun updateActivityType(type: CardioActivityType) {
        _uiState.update { it.copy(selectedActivityType = type) }
    }

    fun updateDuration(value: String) {
        _uiState.update { it.copy(durationText = value) }
    }

    fun updateDistance(value: String) {
        _uiState.update { it.copy(distanceText = value) }
    }

    fun updateCalories(value: String) {
        _uiState.update { it.copy(caloriesText = value) }
    }

    fun updateHeartRate(value: String) {
        _uiState.update { it.copy(heartRateText = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notesText = value) }
    }

    fun updateEntryField(field: CardioEntryField, value: String) {
        _uiState.update {
            when (field) {
                CardioEntryField.DURATION -> it.copy(durationText = value, entryError = null)
                CardioEntryField.DISTANCE_MILES -> it.copy(distanceText = value, entryError = null)
                CardioEntryField.AVG_SPEED_MPH -> it.copy(avgSpeedText = value, entryError = null)
                CardioEntryField.AVG_INCLINE_PERCENT -> it.copy(inclineText = value, entryError = null)
                CardioEntryField.CALORIES -> it.copy(caloriesText = value, entryError = null)
                CardioEntryField.AVG_HEART_RATE -> it.copy(heartRateText = value, entryError = null)
                CardioEntryField.MAX_HEART_RATE -> it.copy(maxHeartRateText = value, entryError = null)
                CardioEntryField.CADENCE_RPM -> it.copy(cadenceText = value, entryError = null)
                CardioEntryField.RESISTANCE_LEVEL -> it.copy(resistanceText = value, entryError = null)
                CardioEntryField.AVG_POWER_WATTS -> it.copy(powerText = value, entryError = null)
                CardioEntryField.STROKE_RATE_SPM -> it.copy(strokeRateText = value, entryError = null)
                CardioEntryField.PACE_500M -> it.copy(pace500Text = value, entryError = null)
                CardioEntryField.FLOORS -> it.copy(floorsText = value, entryError = null)
                CardioEntryField.STEPS -> it.copy(stepsText = value, entryError = null)
                CardioEntryField.NOTES -> it.copy(notesText = value, entryError = null)
            }
        }
    }

    fun selectFilter(type: CardioActivityType?) {
        _uiState.update { it.copy(selectedFilter = type) }
    }

    fun selectSession(id: Long) {
        _uiState.update { it.copy(selectedSessionId = id) }
    }

    fun closeSessionDetail() {
        _uiState.update { it.copy(selectedSessionId = null) }
    }

    fun createOcrCaptureUri(): Uri {
        val directory = File(appContext.cacheDir, OCR_CACHE_DIR).apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        pendingOcrFile = file
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
    }

    fun handleOcrCaptureResult(success: Boolean) {
        val file = pendingOcrFile
        pendingOcrFile = null
        if (!success || file == null || !file.exists()) {
            file?.delete()
            _uiState.update {
                it.copy(
                    isOcrRunning = false,
                    ocrMessage = "No image captured.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isOcrRunning = true, ocrMessage = "Reading machine screen...") }
            try {
                val uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file,
                )
                val result = ocrEngine.analyze(appContext, uri)
                applyOcrResult(result)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isOcrRunning = false,
                        ocrMessage = error.message ?: "OCR failed.",
                        showEntrySheet = true,
                    )
                }
            } finally {
                file.delete()
            }
        }
    }

    fun saveEntry() {
        val state = _uiState.value
        val fields = CardioActivitySpecs.specFor(state.selectedActivityType).fields.toSet()
        val duration = CardioEntryParser.parseDurationSeconds(state.durationText).takeIf { CardioEntryField.DURATION in fields }
        val distanceM = CardioEntryParser.parseMilesToMeters(state.distanceText).takeIf { CardioEntryField.DISTANCE_MILES in fields }
        val calories = CardioEntryParser.parseInt(state.caloriesText).takeIf { CardioEntryField.CALORIES in fields }
        val heartRate = CardioEntryParser.parseInt(state.heartRateText).takeIf { CardioEntryField.AVG_HEART_RATE in fields }
        val maxHeartRate = CardioEntryParser.parseInt(state.maxHeartRateText).takeIf { CardioEntryField.MAX_HEART_RATE in fields }
        val avgSpeed = CardioEntryParser.parseMphToMetersPerSecond(state.avgSpeedText).takeIf { CardioEntryField.AVG_SPEED_MPH in fields }
        val avgInclinePercent = CardioEntryParser.parseDouble(state.inclineText).takeIf { CardioEntryField.AVG_INCLINE_PERCENT in fields }
        val cadenceRpm = CardioEntryParser.parseInt(state.cadenceText).takeIf { CardioEntryField.CADENCE_RPM in fields }
        val resistanceLevel = CardioEntryParser.parseDouble(state.resistanceText).takeIf { CardioEntryField.RESISTANCE_LEVEL in fields }
        val avgPowerWatts = CardioEntryParser.parseInt(state.powerText).takeIf { CardioEntryField.AVG_POWER_WATTS in fields }
        val strokeRateSpm = CardioEntryParser.parseInt(state.strokeRateText).takeIf { CardioEntryField.STROKE_RATE_SPM in fields }
        val paceSecPer500m = CardioEntryParser.parsePaceSeconds(state.pace500Text).takeIf { CardioEntryField.PACE_500M in fields }
        val floors = CardioEntryParser.parseInt(state.floorsText).takeIf { CardioEntryField.FLOORS in fields }
        val steps = CardioEntryParser.parseInt(state.stepsText).takeIf { CardioEntryField.STEPS in fields }
        val notes = state.notesText.trim().takeIf { CardioEntryField.NOTES in fields }.orEmpty()

        if (!CardioEntryParser.hasSaveableMetric(
                durationSec = duration,
                distanceM = distanceM,
                calories = calories,
                avgHeartRate = heartRate,
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
                notes = notes,
            )
        ) {
            _uiState.update { it.copy(entryError = "Add at least one metric before saving.") }
            return
        }

        viewModelScope.launch {
            val editingId = state.editingSessionId
            if (editingId != null) {
                repository.getById(editingId)?.let { existing ->
                    repository.update(
                        existing.copy(
                            activityType = state.selectedActivityType.name,
                            durationSec = duration,
                            distanceM = distanceM,
                            calories = calories,
                            avgHeartRate = heartRate,
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
                            notes = notes,
                        ),
                    )
                }
            } else if (state.entrySource == "ocr") {
                repository.saveOcrConfirmed(
                    activityType = state.selectedActivityType,
                    durationSec = duration,
                    distanceM = distanceM,
                    calories = calories,
                    avgHeartRate = heartRate,
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
                    machineType = state.lastOcrResult?.machineType,
                    ocrConfidence = state.lastOcrResult?.confidence,
                    ocrEngineVersion = "mlkit-text-v1",
                    notes = notes,
                )
            } else {
                repository.saveManual(
                    activityType = state.selectedActivityType,
                    durationSec = duration,
                    distanceM = distanceM,
                    calories = calories,
                    avgHeartRate = heartRate,
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
                    notes = notes,
                )
                lastManualActivity = state.selectedActivityType
            }
            _uiState.update {
                it.copy(
                    showEntrySheet = false,
                    notesText = "",
                    entrySource = "manual",
                    editingSessionId = null,
                    lastOcrResult = null,
                    ocrMessage = null,
                    entryError = null,
                ).clearEntryFields()
            }
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            _uiState.update { state ->
                if (state.selectedSessionId == id) state.copy(selectedSessionId = null) else state
            }
        }
    }

    fun refreshHealthConnectCalories() {
        viewModelScope.launch {
            val result = healthReader.readTodayCalories()
            _uiState.update {
                it.copy(
                    healthConnectAvailable = result.available,
                    healthConnectPermissionGranted = result.permissionGranted,
                    healthConnectCaloriesToday = result.calories,
                    healthConnectMessage = result.message,
                )
            }
        }
    }

    fun onHealthPermissionsResult(granted: Set<String>) {
        _uiState.update {
            it.copy(healthConnectPermissionGranted = granted.containsAll(healthConnectPermissions))
        }
        refreshHealthConnectCalories()
    }

    private fun applyOcrResult(result: CardioOcrResult) {
        val type = when (result.machineType) {
            "treadmill" -> CardioActivityType.TREADMILL
            "bike" -> CardioActivityType.BIKE
            "rower" -> CardioActivityType.ROW
            "elliptical" -> CardioActivityType.ELLIPTICAL
            "stair_climber" -> CardioActivityType.STAIR_CLIMBER
            else -> CardioActivityType.RUN
        }
        _uiState.update {
            it.copy(
                isOcrRunning = false,
                showEntrySheet = true,
                entrySource = if (result.recognizedCardioScreen) "ocr" else "manual",
                selectedActivityType = type,
                durationText = result.durationSec?.let(::formatDuration) ?: it.durationText,
                distanceText = result.distanceM?.let(::formatMiles) ?: it.distanceText,
                avgSpeedText = result.avgSpeed?.let(::formatMph) ?: it.avgSpeedText,
                caloriesText = result.calories?.toString() ?: it.caloriesText,
                heartRateText = result.avgHeartRate?.toString() ?: it.heartRateText,
                lastOcrResult = result,
                ocrMessage = if (result.recognizedCardioScreen) {
                    "OCR filled ${recognizedFieldCount(result)} fields."
                } else {
                    "Could not confidently read the screen."
                },
            )
        }
    }

    private fun cleanupStaleOcrFiles() {
        val cutoff = System.currentTimeMillis() - OCR_FILE_TTL_MS
        File(appContext.cacheDir, OCR_CACHE_DIR)
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.lastModified() < cutoff }
            .forEach { it.delete() }
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    private fun formatMiles(meters: Double): String =
        "%.2f".format(Locale.US, meters / METERS_PER_MILE)

    private fun formatMph(metersPerSecond: Double): String =
        "%.1f".format(Locale.US, metersPerSecond / METERS_PER_SECOND_PER_MPH)

    private fun formatDecimal(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.US, value)

    private fun recognizedFieldCount(result: CardioOcrResult): Int =
        listOf(result.durationSec, result.distanceM, result.calories, result.avgHeartRate, result.avgSpeed)
            .count { it != null }

    private fun CardioUiState.clearEntryFields(): CardioUiState = copy(
        durationText = "",
        distanceText = "",
        avgSpeedText = "",
        inclineText = "",
        caloriesText = "",
        heartRateText = "",
        maxHeartRateText = "",
        cadenceText = "",
        resistanceText = "",
        powerText = "",
        strokeRateText = "",
        pace500Text = "",
        floorsText = "",
        stepsText = "",
        notesText = "",
        entryError = null,
    )

    private companion object {
        const val OCR_CACHE_DIR = "cardio_ocr"
        const val OCR_FILE_TTL_MS = 24L * 60L * 60L * 1000L
        const val METERS_PER_MILE = 1609.344
        const val METERS_PER_SECOND_PER_MPH = 0.44704
    }
}
