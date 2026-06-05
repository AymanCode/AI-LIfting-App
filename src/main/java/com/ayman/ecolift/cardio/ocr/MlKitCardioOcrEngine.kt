package com.ayman.ecolift.cardio.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine

class MlKitCardioOcrEngine {
    suspend fun analyze(context: Context, uri: Uri): CardioOcrResult {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return CardioOcrResult(
            recognizedCardioScreen = false,
            rawText = "",
        )

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            buildImageCandidates(bitmap)
                .map { candidate ->
                    val text = recognizer.process(InputImage.fromBitmap(candidate, 0)).awaitTask()
                    CardioOcrParser.parse(text.text)
                }
                .maxWithOrNull(
                    compareBy<CardioOcrResult>(
                        { it.recognizedCardioScreen },
                        { recognizedFieldCount(it) },
                        { it.confidence },
                    )
                ) ?: CardioOcrParser.parse("")
        } finally {
            recognizer.close()
        }
    }

    private fun buildImageCandidates(bitmap: Bitmap): List<Bitmap> =
        listOfNotNull(
            bitmap,
            bitmap.cropFraction(left = 0.0f, top = 0.0f, right = 1.0f, bottom = 0.58f).scaled(2.0f),
            bitmap.cropFraction(left = 0.08f, top = 0.06f, right = 0.92f, bottom = 0.52f).scaled(2.4f),
            bitmap.cropFraction(left = 0.12f, top = 0.10f, right = 0.88f, bottom = 0.47f).scaled(3.0f),
            bitmap.cropFraction(left = 0.16f, top = 0.10f, right = 0.84f, bottom = 0.46f).scaled(4.0f),
            bitmap.cropFraction(left = 0.16f, top = 0.10f, right = 0.84f, bottom = 0.46f)
                .toGrayscaleContrast(contrast = 3.0f)
                .scaled(4.0f),
        )

    private fun Bitmap.cropFraction(left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val cropLeft = (width * left).roundToInt().coerceIn(0, width - 1)
        val cropTop = (height * top).roundToInt().coerceIn(0, height - 1)
        val cropRight = (width * right).roundToInt().coerceIn(cropLeft + 1, width)
        val cropBottom = (height * bottom).roundToInt().coerceIn(cropTop + 1, height)
        return Bitmap.createBitmap(this, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)
    }

    private fun Bitmap.scaled(factor: Float): Bitmap =
        Bitmap.createScaledBitmap(
            this,
            (width * factor).roundToInt().coerceAtLeast(width),
            (height * factor).roundToInt().coerceAtLeast(height),
            true,
        )

    private fun Bitmap.toGrayscaleContrast(contrast: Float): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        pixels.indices.forEach { index ->
            val color = pixels[index]
            val luminance = (
                Color.red(color) * 0.299f +
                    Color.green(color) * 0.587f +
                    Color.blue(color) * 0.114f
                )
            val adjusted = ((luminance - 128f) * contrast + 128f)
                .roundToInt()
                .coerceIn(0, 255)
            pixels[index] = Color.rgb(adjusted, adjusted, adjusted)
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun recognizedFieldCount(result: CardioOcrResult): Int =
        listOf(result.durationSec, result.distanceM, result.calories, result.avgHeartRate, result.avgSpeed)
            .count { it != null }
}

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { throwable ->
            if (continuation.isActive) continuation.resumeWithException(throwable)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
