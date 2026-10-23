package com.ayman.ecolift.android.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.ayman.ecolift.cardio.ocr.CardioOcrParser
import com.ayman.ecolift.cardio.ocr.CardioOcrResult
import com.ayman.ecolift.cardio.ocr.MlKitCardioOcrEngine
import com.ayman.ecolift.web.VisionService
import com.ayman.ecolift.web.ai.VisionStats
import com.ayman.ecolift.web.scaledPhotoSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Parses console text into the web vision stats. Null when the screen is not a cardio console. */
fun visionStatsFromOcrText(raw: String): VisionStats? = CardioOcrParser.parse(raw).toVisionStats()

fun CardioOcrResult.toVisionStats(): VisionStats? {
    if (!recognizedCardioScreen) return null
    return VisionStats(
        activityType = activityTypeForMachine(machineType),
        durationSec = durationSec,
        distanceM = distanceM,
        calories = calories,
        avgHeartRate = avgHeartRate,
        avgSpeed = avgSpeed,
        machineType = machineType,
        confidence = confidence,
    )
}

/**
 * "rower" is the parser's machine string. The activity enum name is ROW.
 * Putting the parser string in activityType makes CardioActivityType.valueOf fail.
 */
private fun activityTypeForMachine(machineType: String?): String? = when (machineType) {
    "treadmill" -> "TREADMILL"
    "elliptical" -> "ELLIPTICAL"
    "bike" -> "BIKE"
    "rower" -> "ROW"
    "stair_climber" -> "STAIR_CLIMBER"
    else -> null
}

class AndroidVisionService(context: Context) : VisionService {
    private val appContext = context.applicationContext

    override suspend fun scanStats(b64: String, mime: String): VisionStats? = withContext(Dispatchers.IO) {
        val file = writeScanFile(b64, mime) ?: return@withContext null
        try {
            MlKitCardioOcrEngine().analyze(appContext, Uri.fromFile(file)).toVisionStats()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        } finally {
            file.delete()
        }
    }

    override suspend fun scansLeftToday(): Int? = null

    private fun writeScanFile(b64: String, mime: String): File? {
        val bytes = decodeScanPayload(b64) ?: return null
        if (bytes.isEmpty()) return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return writeOriginal(bytes, mime)
        val scaled = try {
            val (width, height) = scaledPhotoSize(bitmap.width, bitmap.height)
            if (width == bitmap.width && height == bitmap.height) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            }
        } catch (error: Exception) {
            bitmap.recycle()
            return writeOriginal(bytes, mime)
        }
        return try {
            val dir = File(appContext.cacheDir, "cardio_ocr").apply { mkdirs() }
            val file = File(dir, "scan-${System.nanoTime()}.jpg")
            val wrote = FileOutputStream(file).use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            }
            if (wrote) file else writeOriginal(bytes, mime).also { file.delete() }
        } catch (error: Exception) {
            writeOriginal(bytes, mime)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }

    private fun writeOriginal(bytes: ByteArray, mime: String): File? {
        val extension = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val dir = File(appContext.cacheDir, "cardio_ocr").apply { mkdirs() }
        val file = File(dir, "scan-${System.nanoTime()}.$extension")
        return try {
            file.writeBytes(bytes)
            file
        } catch (error: Exception) {
            null
        }
    }
}

internal fun decodeScanPayload(b64: String): ByteArray? {
    val payload = b64.substringAfter("base64,", b64).trim()
    if (payload.isEmpty()) return null
    return try {
        Base64.decode(payload, Base64.DEFAULT)
    } catch (error: IllegalArgumentException) {
        null
    }
}
