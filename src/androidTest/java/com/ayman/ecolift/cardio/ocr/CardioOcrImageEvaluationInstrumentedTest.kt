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
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
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
        val passCount = results.count { it.passed }
        val passRate = passCount.toDouble() / results.size.toDouble()
        val failures = results
            .filterNot { it.passed }
            .joinToString(separator = "\n") { failure ->
                val fields = recognizedFieldCount(failure.result)
                "${failure.case.fileName}: fields=$fields machine=${failure.result.machineType} text=${failure.result.rawText.take(140)}"
            }

        assertTrue(
            "Cardio OCR pass rate ${"%.0f".format(Locale.US, passRate * 100)}% ($passCount/${results.size}) is below 70%.\n$failures",
            passRate >= REQUIRED_PASS_RATE,
        )
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
            OcrImageCase(
                fileName = json.getString("file"),
                requiredFields = json.optJSONArray("requiredFields")?.toStringList().orEmpty(),
                expectedMachineType = json.optString("expectedMachineType").takeIf { it.isNotBlank() },
                minRecognizedFields = json.optInt("minRecognizedFields", 2),
            )
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { index -> getString(index) }

    private fun CardioOcrResult.meets(case: OcrImageCase): Boolean =
        recognizedCardioScreen &&
            recognizedFieldCount(this) >= case.minRecognizedFields &&
            case.requiredFields.all { fieldName -> hasField(fieldName) } &&
            (case.expectedMachineType == null || machineType == case.expectedMachineType)

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

    private data class OcrImageCase(
        val fileName: String,
        val requiredFields: List<String>,
        val expectedMachineType: String?,
        val minRecognizedFields: Int,
    )

    private data class OcrCaseResult(
        val case: OcrImageCase,
        val result: CardioOcrResult,
        val passed: Boolean,
    )

    private companion object {
        const val REQUIRED_PASS_RATE = 0.70
        const val ASSET_DIR = "cardio_ocr_eval"
        const val MANIFEST_ASSET = "$ASSET_DIR/manifest.json"
        const val EXTERNAL_DIR_ARGUMENT = "cardioOcrEvalDir"
    }
}
