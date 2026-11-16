package com.seunome.mcaddonai

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object StreamingExtractor {

    fun extractAndIndex(context: Context, fileUri: Uri, targetFolder: File, db: MindMapDatabase, indexForTasks: Boolean): Int {
        var totalFiles = 0
        val inputStream = context.contentResolver.openInputStream(fileUri) ?: return 0
        ZipInputStream(BufferedInputStream(inputStream, 8192)).use { zipIn ->
            var entry = zipIn.nextEntry
            val buffer = ByteArray(8192)

            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(targetFolder, entry.name)
                    outFile.parentFile?.mkdirs()

                    FileOutputStream(outFile).use { fos ->
                        var count: Int
                        while (zipIn.read(buffer).also { count = it } != -1) {
                            fos.write(buffer, 0, count)
                        }
                    }

                    totalFiles++
                    if (indexForTasks) {
                        val type = when {
                            entry.name.endsWith(".class") || entry.name.endsWith(".java") -> "JAVA_CODE"
                            entry.name.endsWith(".png") -> "TEXTURE"
                            entry.name.endsWith(".ogg") -> "SOUND"
                            entry.name.endsWith(".js") -> "SCRIPT_JS"
                            entry.name.endsWith(".json") -> "JSON_DATA"
                            else -> "ASSET"
                        }
                        db.registerFileTask(entry.name, type)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        return totalFiles
    }
}
