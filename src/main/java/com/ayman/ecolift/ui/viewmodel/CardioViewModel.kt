package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.cardio.health.CardioHealthConnectReader
import com.ayman.ecolift.cardio.ocr.CardioOcrResult
import com.ayman.ecolift.cardio.ocr.MlKitCardioOcrEngine
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioRepository
import com.ayman.ecolift.data.CardioSession
import java.io.File
import java.time.LocalDate
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
    val caloriesText: String = "",
    val heartRateText: String = "",
    val notesText: String = "",
    val entrySource: String = "manual",
    val lastOcrResult: CardioOcrResult? = null,
    val ocrMessage: String? = null,
    val isOcrRunning: Boolean = false,
    val showEntrySheet: Boolean = false,
)

class CardioViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val repository = CardioRepository(db)
    private val ocrEngine = MlKitCardioOcrEngine()
    private val healthReader = CardioHealthConnectReader(appContext)

    private var pendingOcrFile: File? = null

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
                lastOcrResult = null,
                ocrMessage = null,
            )
        }
    }

    fun closeEntrySheet() {
        _uiState.update { it.copy(showEntrySheet = false) }
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
        viewModelScope.launch {
            val duration = parseDurationInput(state.durationText)
            val distanceM = state.distanceText.toDoubleOrNull()?.let { it * METERS_PER_MILE }
            val calories = state.caloriesText.toIntOrNull()
            val heartRate = state.heartRateText.toIntOrNull()

            if (state.entrySource == "ocr") {
                repository.saveOcrConfirmed(
                    activityType = state.selectedActivityType,
                    durationSec = duration,
                    distanceM = distanceM,
                    calories = calories,
                    avgHeartRate = heartRate,
                    machineType = state.lastOcrResult?.machineType,
                    ocrConfidence = state.lastOcrResult?.confidence,
                    ocrEngineVersion = "mlkit-text-v1",
                    notes = state.notesText.trim(),
                )
            } else {
                repository.saveManual(
                    activityType = state.selectedActivityType,
                    durationSec = duration,
                    distanceM = distanceM,
                    calories = calories,
                    avgHeartRate = heartRate,
                    notes = state.notesText.trim(),
                )
            }
            _uiState.update {
                it.copy(
                    showEntrySheet = false,
                    durationText = "",
                    distanceText = "",
                    caloriesText = "",
                    heartRateText = "",
                    notesText = "",
                    entrySource = "manual",
                    lastOcrResult = null,
                    ocrMessage = null,
                )
            }
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
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
                distanceText = result.distanceM?.let { meters -> "%.2f".format(meters / METERS_PER_MILE) } ?: it.distanceText,
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

    private fun parseDurationInput(value: String): Int? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val parts = trimmed.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            1 -> parts[0] * 60
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
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

    private fun recognizedFieldCount(result: CardioOcrResult): Int =
        listOf(result.durationSec, result.distanceM, result.calories, result.avgHeartRate, result.avgSpeed)
            .count { it != null }

    private companion object {
        const val OCR_CACHE_DIR = "cardio_ocr"
        const val OCR_FILE_TTL_MS = 24L * 60L * 60L * 1000L
        const val METERS_PER_MILE = 1609.344
    }
}
