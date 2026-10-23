package com.ayman.ecolift.web

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PHOTO_MAX_EDGE = 1600
private const val JPEG_QUALITY = 85

/**
 * MainActivity registers this once in onCreate, before setContent.
 * The launcher is the activity result contract. [pickPhoto] only stores the callback and fires it.
 */
object PhotoPickerBridge {
    private var host: ComponentActivity? = null
    private var launchPicker: (() -> Unit)? = null
    private var pending: ((Pair<String, String>?) -> Unit)? = null

    fun register(activity: ComponentActivity) {
        if (host === activity) return
        host = activity
        val launcher = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val callback = pending
            pending = null
            if (callback == null) return@registerForActivityResult
            if (uri == null) {
                callback(null)
                return@registerForActivityResult
            }
            activity.lifecycleScope.launch {
                val encoded = withContext(Dispatchers.IO) { encodePickedPhoto(activity, uri) }
                callback(encoded)
            }
        }
        launchPicker = {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    fun request(onResult: (Pair<String, String>?) -> Unit) {
        val launch = launchPicker
        if (launch == null) {
            onResult(null)
            return
        }
        pending = onResult
        launch()
    }
}

/**
 * Opens the system photo picker. Null means the user backed out or the image could not be read.
 * The pair is mime to base64, the same order the web picker returns.
 */
fun pickPhoto(onResult: (Pair<String, String>?) -> Unit) {
    PhotoPickerBridge.request(onResult)
}

/** Long edge capped at [maxDim]. Smaller photos stay at their own size. Neither edge is zero. */
fun scaledPhotoSize(width: Int, height: Int, maxDim: Int = PHOTO_MAX_EDGE): Pair<Int, Int> {
    val safeWidth = width.coerceAtLeast(1)
    val safeHeight = height.coerceAtLeast(1)
    val scale = min(1.0, maxDim.toDouble() / max(safeWidth, safeHeight))
    val scaledWidth = max(1, (safeWidth * scale).roundToInt())
    val scaledHeight = max(1, (safeHeight * scale).roundToInt())
    return scaledWidth to scaledHeight
}

fun encodePickedPhoto(context: Context, uri: Uri): Pair<String, String>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    val decoded = decodeSampled(context, uri, bounds) ?: return originalBytes(context, uri)
    val (targetWidth, targetHeight) = scaledPhotoSize(decoded.width, decoded.height)
    val scaled = if (targetWidth == decoded.width && targetHeight == decoded.height) {
        decoded
    } else {
        Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
    }
    return try {
        val jpeg = ByteArrayOutputStream()
        if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg)) {
            originalBytes(context, uri)
        } else {
            val encoded = Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP)
            if (encoded.isEmpty()) null else "image/jpeg" to encoded
        }
    } finally {
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
    }
}

private fun decodeSampled(context: Context, uri: Uri, bounds: BitmapFactory.Options): Bitmap? {
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val longEdge = max(bounds.outWidth, bounds.outHeight)
    while (longEdge / sample > PHOTO_MAX_EDGE * 2) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}

private fun originalBytes(context: Context, uri: Uri): Pair<String, String>? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.isEmpty()) return null
    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
    val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return if (encoded.isEmpty()) null else mime to encoded
}
