package com.seunome.mcaddonai

import android.os.Environment
import java.io.File

object WorkspaceManager {
    val rootDir = File(Environment.getExternalStorageDirectory(), "MCAddonAI_Workspace")
    val modelsDir = File(rootDir, "1_Modelos_IA")
    val referenceDir = File(rootDir, "2_Addons_Estudados")
    val tempExtractDir = File(rootDir, "3_Extracao_Temp")
    val buildDir = File(rootDir, "4_Projeto_Atual")
    val outputDir = File(rootDir, "5_Addons_Prontos")

    fun initializeWorkspace() {
        listOf(rootDir, modelsDir, referenceDir, tempExtractDir, buildDir, outputDir).forEach { dir ->
            if (!dir.exists()) dir.mkdirs()
        }
    }

    fun clearCurrentBuild() {
        tempExtractDir.deleteRecursively()
        buildDir.deleteRecursively()
        tempExtractDir.mkdirs()
        buildDir.mkdirs()
    }
}
