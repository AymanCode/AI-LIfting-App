package com.ayman.ecolift.cardio.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardioOcrImageEvaluationInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val engine = MlKitCardioOcrEngine()
    private val cacheDir = File(context.cacheDir, "cardio_ocr_eval")

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun generatedTreadmillScreenImageIsRecognizedEndToEnd() = runTest {
        val file = renderScreenImage(
            listOf(
                "TREADMILL",
                "TIME 32:15",
                "DISTANCE 2.41 MI",
                "CALORIES 318",
                "HR 142 BPM",
            )
        )

        val result = engine.analyze(context, Uri.fromFile(file))

        assertTrue("Expected cardio screen recognition, got raw OCR: ${result.rawText}", result.recognizedCardioScreen)
        assertEquals(1935, result.durationSec)
        assertEquals(318, result.calories)
        assertNotNull(result.distanceM)
        assertEquals("treadmill", result.machineType)
    }

    @Test
    fun treadmillImageGoldenSetMeetsSeventyPercentRecognitionGate() = runTest {
        val externalDir = InstrumentationRegistry.getArguments()
            .getString(EXTERNAL_DIR_ARGUMENT)
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        val manifestText = externalDir
            ?.let { File(it, "manifest.json").takeIf(File::exists)?.readText() }
            ?: readAssetTextOrNull(MANIFEST_ASSET)
        assumeTrue(
            "Add treadmill evaluation images and manifest under src/androidTest/assets/$ASSET_DIR, " +
                "or pass -Pandroid.testInstrumentationRunnerArguments.$EXTERNAL_DIR_ARGUMENT=/device/path, to run the 70% OCR gate.",
            manifestText != null,
        )

        val cases = parseCases(manifestText.orEmpty())
        assertTrue("OCR evaluation manifest must contain at least one image case.", cases.isNotEmpty())

        val results = cases.map { case ->
            val imageFile = externalDir
                ?.let { File(it, case.fileName).also { file -> assertTrue("Missing OCR image ${file.absolutePath}", file.exists()) } }
                ?: copyAssetToCache("$ASSET_DIR/${case.fileName}", case.fileName)
            val result = engine.analyze(context, Uri.fromFile(imageFile))
            OcrCaseResult(case = case, result = result, passed = result.meets(case))
        }
        val metrics = OcrEvalMetrics.from(results) { caseResult ->
            caseResult.result.expectedValuesCorrect(caseResult.case)
        }
        val report = buildReport(results, metrics)
        File(context.filesDir, REPORT_FILE_NAME).writeText(report)

        assertTrue(
            "Cardio OCR usable-session conversion ${metrics.usableSessionPercent()} " +
                "(${metrics.usablePositiveCount}/${metrics.positiveCount}) is below 70%.\n$report",
            metrics.usableSessionRate >= REQUIRED_PASS_RATE,
        )
        if (metrics.expectedValueCount > 0) {
            assertTrue(
                "Cardio OCR field-value accuracy ${metrics.fieldValueAccuracyPercent()} " +
                    "(${metrics.correctValueCount}/${metrics.expectedValueCount}) is below 85%.\n$report",
                metrics.fieldValueAccuracy >= REQUIRED_FIELD_VALUE_ACCURACY,
            )
        }
        if (metrics.negativeCount > 0) {
            assertTrue(
                "Cardio OCR false accept rate ${metrics.falseAcceptPercent()} " +
                    "(${metrics.falseAcceptCount}/${metrics.negativeCount}) must be 0%.\n$report",
                metrics.falseAcceptCount == 0,
            )
        }
    }

    private fun renderScreenImage(lines: List<String>): File {
        cacheDir.mkdirs()
        val bitmap = Bitmap.createBitmap(900, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(8, 12, 16))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, 72f, 118f + index * 96f, paint)
        }
        return File(cacheDir, "generated-${UUID.randomUUID()}.png").also { file ->
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }

    private fun readAssetTextOrNull(assetPath: String): String? =
        try {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            null
        }

    private fun copyAssetToCache(assetPath: String, fileName: String): File {
        cacheDir.mkdirs()
        val file = File(cacheDir, "${UUID.randomUUID()}-$fileName")
        context.assets.open(assetPath).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    private fun parseCases(manifestText: String): List<OcrImageCase> {
        val array = JSONArray(manifestText)
        return List(array.length()) { index ->
            val json = array.getJSONObject(index)
            val expected = json.optJSONObject("expected")
            OcrImageCase(
                fileName = json.getString("file"),
                positive = json.optBoolean("positive", true),
                sourceUrl = json.optString("sourceUrl").takeIf { it.isNotBlank() },
                requiredFields = json.optJSONArray("requiredFields")?.toStringList().orEmpty(),
                expectedMachineType = json.optString("expectedMachineType").takeIf { it.isNotBlank() },
                expectedDurationSec = expected?.optNullableInt("durationSec"),
                expectedDistanceMiles = expected?.optNullableDouble("distanceMiles"),
                expectedCalories = expected?.optNullableInt("calories"),
                expectedHeartRate = expected?.optNullableInt("heartRate"),
                expectedSpeedMph = expected?.optNullableDouble("speedMph"),
                minRecognizedFields = json.optInt("minRecognizedFields", 2),
            )
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { index -> getString(index) }

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name) else null

    private fun CardioOcrResult.meets(case: OcrImageCase): Boolean {
        if (!case.positive) return !recognizedCardioScreen
        return recognizedCardioScreen &&
            recognizedFieldCount(this) >= case.minRecognizedFields &&
            case.requiredFields.all { fieldName -> hasField(fieldName) } &&
            (case.expectedMachineType == null || machineType == case.expectedMachineType) &&
            expectedValuesCorrect(case)
                .filter { it.field in case.requiredFields }
                .all { it.correct }
    }

    private fun CardioOcrResult.expectedValuesCorrect(case: OcrImageCase): List<OcrValueCheck> =
        listOfNotNull(
            case.expectedDurationSec?.let { expected ->
                OcrValueCheck("duration", durationSec != null && abs(durationSec - expected) <= DURATION_TOLERANCE_SEC)
            },
            case.expectedDistanceMiles?.let { expected ->
                val actualMiles = distanceM?.let { it / METERS_PER_MILE }
                OcrValueCheck(
                    "distance",
                    actualMiles != null && abs(actualMiles - expected) <= max(DISTANCE_TOLERANCE_MILES, expected * 0.01),
                )
            },
            case.expectedCalories?.let { expected ->
                OcrValueCheck("calories", calories != null && abs(calories - expected) <= INTEGER_TOLERANCE)
            },
            case.expectedHeartRate?.let { expected ->
                OcrValueCheck("heartRate", avgHeartRate != null && abs(avgHeartRate - expected) <= INTEGER_TOLERANCE)
            },
            case.expectedSpeedMph?.let { expected ->
                val actualMph = avgSpeed?.let { it / METERS_PER_SECOND_PER_MPH }
                OcrValueCheck(
                    "speed",
                    actualMph != null && abs(actualMph - expected) <= max(SPEED_TOLERANCE_MPH, expected * 0.02),
                )
            },
        )

    private fun CardioOcrResult.hasField(fieldName: String): Boolean =
        when (fieldName) {
            "duration" -> durationSec != null
            "distance" -> distanceM != null
            "calories" -> calories != null
            "heartRate" -> avgHeartRate != null
            "speed" -> avgSpeed != null
            "machineType" -> machineType != null
            else -> false
        }

    private fun recognizedFieldCount(result: CardioOcrResult): Int =
        listOf(result.durationSec, result.distanceM, result.calories, result.avgHeartRate, result.avgSpeed)
            .count { it != null }

    private fun buildReport(results: List<OcrCaseResult>, metrics: OcrEvalMetrics): String =
        buildString {
            appendLine("Cardio OCR compatibility evaluation")
            appendLine("Metric boundary: evaluates the app OCR pipeline, not ML Kit model ownership.")
            appendLine("cases=${results.size} positives=${metrics.positiveCount} negatives=${metrics.negativeCount}")
            appendLine("usableSessionConversion=${metrics.usableSessionPercent()} (${metrics.usablePositiveCount}/${metrics.positiveCount})")
            appendLine("fieldValueAccuracy=${metrics.fieldValueAccuracyPercent()} (${metrics.correctValueCount}/${metrics.expectedValueCount})")
            appendLine("falseAcceptRate=${metrics.falseAcceptPercent()} (${metrics.falseAcceptCount}/${metrics.negativeCount})")
            appendLine("positiveFallbackRate=${metrics.positiveFallbackPercent()} (${metrics.positiveFallbackCount}/${metrics.positiveCount})")
            appendLine()
            results.forEach { caseResult ->
                val valueChecks = caseResult.result.expectedValuesCorrect(caseResult.case)
                val valueSummary = if (valueChecks.isEmpty()) {
                    "no expected values"
                } else {
                    valueChecks.joinToString { "${it.field}=${if (it.correct) "ok" else "miss"}" }
                }
                appendLine(
                    "${caseResult.case.fileName}: positive=${caseResult.case.positive} pass=${caseResult.passed} " +
                        "recognized=${caseResult.result.recognizedCardioScreen} fields=${recognizedFieldCount(caseResult.result)} " +
                        "confidence=${"%.2f".format(Locale.US, caseResult.result.confidence)} machine=${caseResult.result.machineType} " +
                        "values=[$valueSummary]",
                )
                caseResult.case.sourceUrl?.let { appendLine("  source=$it") }
                appendLine("  parsed=${caseResult.result.toParsedSummary()}")
                appendLine("  raw=${caseResult.result.rawText.replace('\n', ' ').take(240)}")
            }
        }

    private fun CardioOcrResult.toParsedSummary(): String =
        "durationSec=$durationSec distanceMiles=${distanceM?.let { "%.2f".format(Locale.US, it / METERS_PER_MILE) }} " +
            "calories=$calories heartRate=$avgHeartRate speedMph=${avgSpeed?.let { "%.1f".format(Locale.US, it / METERS_PER_SECOND_PER_MPH) }}"

    private data class OcrImageCase(
        val fileName: String,
        val positive: Boolean,
        val sourceUrl: String?,
        val requiredFields: List<String>,
        val expectedMachineType: String?,
        val expectedDurationSec: Int?,
        val expectedDistanceMiles: Double?,
        val expectedCalories: Int?,
        val expectedHeartRate: Int?,
        val expectedSpeedMph: Double?,
        val minRecognizedFields: Int,
    )

    private data class OcrCaseResult(
        val case: OcrImageCase,
        val result: CardioOcrResult,
        val passed: Boolean,
    )

    private data class OcrValueCheck(
        val field: String,
        val correct: Boolean,
    )

    private data class OcrEvalMetrics(
        val positiveCount: Int,
        val negativeCount: Int,
        val usablePositiveCount: Int,
        val correctValueCount: Int,
        val expectedValueCount: Int,
        val falseAcceptCount: Int,
        val positiveFallbackCount: Int,
    ) {
        val usableSessionRate: Double = ratio(usablePositiveCount, positiveCount)
        val fieldValueAccuracy: Double = ratio(correctValueCount, expectedValueCount)

        fun usableSessionPercent(): String = percent(usableSessionRate)
        fun fieldValueAccuracyPercent(): String = percent(fieldValueAccuracy)
        fun falseAcceptPercent(): String = percent(ratio(falseAcceptCount, negativeCount))
        fun positiveFallbackPercent(): String = percent(ratio(positiveFallbackCount, positiveCount))

        companion object {
            fun from(
                results: List<OcrCaseResult>,
                valueChecksFor: (OcrCaseResult) -> List<OcrValueCheck>,
            ): OcrEvalMetrics {
                val positiveResults = results.filter { it.case.positive }
                val negativeResults = results.filterNot { it.case.positive }
                val valueChecks = positiveResults.flatMap(valueChecksFor)
                return OcrEvalMetrics(
                    positiveCount = positiveResults.size,
                    negativeCount = negativeResults.size,
                    usablePositiveCount = positiveResults.count { it.passed },
                    correctValueCount = valueChecks.count { it.correct },
                    expectedValueCount = valueChecks.size,
                    falseAcceptCount = negativeResults.count { it.result.recognizedCardioScreen },
                    positiveFallbackCount = positiveResults.count { !it.result.recognizedCardioScreen },
                )
            }
        }

        private fun ratio(numerator: Int, denominator: Int): Double =
            if (denominator == 0) 0.0 else numerator.toDouble() / denominator.toDouble()

        private fun percent(value: Double): String =
            "${"%.1f".format(Locale.US, value * 100)}%"
    }

    private companion object {
        const val REQUIRED_PASS_RATE = 0.70
        const val REQUIRED_FIELD_VALUE_ACCURACY = 0.85
        const val ASSET_DIR = "cardio_ocr_eval"
        const val MANIFEST_ASSET = "$ASSET_DIR/manifest.json"
        const val EXTERNAL_DIR_ARGUMENT = "cardioOcrEvalDir"
        const val REPORT_FILE_NAME = "cardio_ocr_eval_report.txt"
        const val METERS_PER_MILE = 1609.344
        const val METERS_PER_SECOND_PER_MPH = 0.44704
        const val DURATION_TOLERANCE_SEC = 5
        const val DISTANCE_TOLERANCE_MILES = 0.03
        const val SPEED_TOLERANCE_MPH = 0.1
        const val INTEGER_TOLERANCE = 1
    }
}
