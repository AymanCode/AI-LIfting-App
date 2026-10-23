package com.ayman.ecolift.data

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.util.UUID

internal class IsolatedBackupContext private constructor(
    base: Context,
    private val root: File,
) : ContextWrapper(base) {
    override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }

    // AppDatabase.getInstance forwards applicationContext, which would skip getFilesDir.
    override fun getApplicationContext(): Context = this

    fun deleteIsolatedFiles() {
        root.deleteRecursively()
    }

    companion object {
        fun open(base: Context): IsolatedBackupContext {
            val root = File(base.cacheDir, "backup-test-${UUID.randomUUID()}")
            root.mkdirs()
            return IsolatedBackupContext(base, root)
        }
    }
}
