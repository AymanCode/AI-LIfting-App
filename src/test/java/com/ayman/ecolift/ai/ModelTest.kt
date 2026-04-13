package com.ayman.ecolift.ai

import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class ModelTest {
    @Test
    fun verifyModelFile() {
        val path = "C:\\Users\\ayman\\Documents\\Lifting app\\gemma-4-E2B-it.litertlm"
        val file = File(path)
        println("Checking path: $path")
        
        if (!file.exists()) {
            error("FAILURE: Model file not found at $path")
        }

        println("SUCCESS: Model file found.")
        println("Size: ${file.length() / 1024 / 1024} MB")

        // Verify it's a valid LiteRT/TFLite file
        val raf = RandomAccessFile(file, "r")
        val header = ByteArray(8)
        raf.seek(0)
        raf.read(header)
        raf.close()

        val magic = String(header.sliceArray(4..7))
        println("Magic Bytes: $magic")

        if (magic == "RTLM" || magic == "TFL3") {
            println("VERIFIED: File is a valid LiteRT/Gemma model.")
        } else {
            error("FAILURE: File header '$magic' does not match LiteRT (RTLM) or TFLite (TFL3).")
        }
    }
}
