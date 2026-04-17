package com.ayman.ecolift.data

import android.content.Context
import android.net.Uri
import java.io.File

class ModelManager(private val context: Context) {
    
    sealed class ImportResult {
        object Success : ImportResult()
        data class Failure(val message: String) : ImportResult()
    }

    fun importModel(uri: Uri): ImportResult {
        return try {
            val destination = File(context.filesDir, "gemma.bin")
            
            // Check disk space (Need at least 3GB)
            val freeSpace = context.filesDir.usableSpace
            if (freeSpace < 3_000_000_000L) {
                return ImportResult.Failure("Not enough storage space. Need at least 3GB free.")
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024) // 1MB buffer
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            if (destination.exists() && destination.length() > 0) {
                ImportResult.Success
            } else {
                ImportResult.Failure("File copy resulted in 0 bytes.")
            }
        } catch (e: Exception) {
            ImportResult.Failure(e.localizedMessage ?: "Unknown copy error")
        }
    }
}
