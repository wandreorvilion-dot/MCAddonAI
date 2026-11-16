package com.seunome.mcaddonai

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AddonPackager {

    fun packBuildDirToMcAddon(buildDir: File, outputDir: File): File {
        val bpDir = File(buildDir, "Behavior_Pack").apply { mkdirs() }
        val rpDir = File(buildDir, "Resource_Pack").apply { mkdirs() }

        val bpUuid = UUID.randomUUID().toString()
        val rpUuid = UUID.randomUUID().toString()

        val bpManifest = File(bpDir, "manifest.json")
        if (!bpManifest.exists()) {
            bpManifest.writeText("""
            {
              "format_version": 2,
              "header": {
                "name": "AI Generated Addon BP",
                "description": "Criado por MC Addon Studio AI",
                "uuid": "$bpUuid",
                "version":,
                "min_engine_version":
              },
              "modules": [
                {
                  "type": "script",
                  "language": "javascript",
                  "uuid": "${UUID.randomUUID()}",
                  "version":,
                  "entry": "scripts/main.js"
                }
              ],
              "dependencies": [
                { "module_name": "@minecraft/server", "version": "1.14.0" },
                { "uuid": "$rpUuid", "version": }
              ]
            }
            """.trimIndent())
        }

        val rpManifest = File(rpDir, "manifest.json")
        if (!rpManifest.exists()) {
            rpManifest.writeText("""
            {
              "format_version": 2,
              "header": {
                "name": "AI Generated Addon RP",
                "description": "Texturas e Modelos AI",
                "uuid": "$rpUuid",
                "version":,
                "min_engine_version":
              },
              "modules": [
                {
                  "type": "resources",
                  "uuid": "${UUID.randomUUID()}",
                  "version":
                }
              ]
            }
            """.trimIndent())
        }

        val finalFile = File(outputDir, "Addon_Gerado_${System.currentTimeMillis()}.mcaddon")
        ZipOutputStream(FileOutputStream(finalFile)).use { zipOut ->
            zipFolder(buildDir, buildDir, zipOut)
        }
        return finalFile
    }

    private fun zipFolder(rootDir: File, currentDir: File, zipOut: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return
        val buffer = ByteArray(8192)
        for (file in files) {
            if (file.isDirectory) {
                zipFolder(rootDir, file, zipOut)
            } else {
                val relativePath = file.relativeTo(rootDir).path.replace("\\", "/")
                zipOut.putNextEntry(ZipEntry(relativePath))
                BufferedInputStream(FileInputStream(file), 8192).use { bis ->
                    var count: Int
                    while (bis.read(buffer).also { count = it } != -1) {
                        zipOut.write(buffer, 0, count)
                    }
                }
                zipOut.closeEntry()
            }
        }
    }
}
