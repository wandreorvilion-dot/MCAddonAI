package com.seunome.mcaddonai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

class AiWorkerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var db: MindMapDatabase

    override fun onCreate() {
        super.onCreate()
        db = MindMapDatabase(this)
        acquireWakeLock()
        startForegroundNotification("Iniciando motor da IA...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("MODE") ?: "CREATE_ADDON"
        val userPrompt = intent?.getStringExtra("PROMPT") ?: ""
        val fileUriStr = intent?.getStringExtra("FILE_URI")

        serviceScope.launch {
            try {
                when (mode) {
                    "LEARN_ADDON" -> {
                        if (fileUriStr != null) {
                            updateNotification("Estudando Addon de referência...")
                            WorkspaceManager.clearCurrentBuild()
                            val count = StreamingExtractor.extractAndIndex(
                                this@AiWorkerService, Uri.parse(fileUriStr), WorkspaceManager.tempExtractDir, db, false
                            )
                            val structureSummary = WorkspaceManager.tempExtractDir.walkTopDown()
                                .filter { it.isFile }.take(50).map { it.relativeTo(WorkspaceManager.tempExtractDir).path }.joinToString(", ")
                            db.saveLearnedAddon("Addon_Referencia", "Total arquivos: $count. Estrutura: $structureSummary")
                            sendChatLog("Estudo concluído! $count arquivos analisados e salvos na memória de referência.")
                        }
                    }
                    "TRANSCRIBE_JAR" -> {
                        if (fileUriStr != null) {
                            updateNotification("Extraindo .JAR em streaming (sem limite de tamanho)...")
                            WorkspaceManager.clearCurrentBuild()
                            db.clearCurrentProject()
                            StreamingExtractor.extractAndIndex(
                                this@AiWorkerService, Uri.parse(fileUriStr), WorkspaceManager.tempExtractDir, db, true
                            )
                            runSequentialBuilder("TRANSCRIBE_JAR", "Transcreva e otimize este mod Java para Minecraft Bedrock")
                        }
                    }
                    else -> {
                        updateNotification("Criando Mapa Mental do Addon...")
                        WorkspaceManager.clearCurrentBuild()
                        db.clearCurrentProject()
                        val learned = db.getLearnedPatterns()
                        val plan = LlamaBridge.planMindMap(userPrompt, learned)
                        plan.forEach { (path, type) -> db.registerFileTask(path, type) }
                        runSequentialBuilder("CREATE_ADDON", userPrompt)
                    }
                }
            } catch (e: Exception) {
                sendChatLog("Erro no processo: ${e.message}")
            }
            stopSelf()
        }
        return START_STICKY
    }

    private suspend fun runSequentialBuilder(mode: String, userPrompt: String) {
        var nextTask = db.getNextPendingTask()
        while (nextTask != null) {
            updateNotification("Gerando (${nextTask.id}): ${nextTask.filePath}")

            if (nextTask.fileType == "TEXTURE" || nextTask.fileType == "SOUND") {
                val srcFile = File(WorkspaceManager.tempExtractDir, nextTask.filePath)
                val destFile = File(WorkspaceManager.buildDir, "Resource_Pack/${nextTask.filePath}")
                destFile.parentFile?.mkdirs()
                if (srcFile.exists()) srcFile.copyTo(destFile, overwrite = true)
                db.markAsGenerated(nextTask.id, "Asset ${nextTask.filePath} otimizado e copiado.")
            } else {
                val traveledRoute = db.getTraveledRouteContext()
                val learnedContext = if (mode == "CREATE_ADDON") db.getLearnedPatterns() else ""

                var code = LlamaBridge.generateSingleFile(
                    taskPath = nextTask.filePath,
                    fileType = nextTask.fileType,
                    userRequest = userPrompt,
                    routeHistory = traveledRoute,
                    learnedKnowledge = learnedContext
                )
                LlamaBridge.clearContextMemory()

                code = cleanMarkdownCode(code)

                if (nextTask.filePath.endsWith(".js")) {
                    val syntaxError = JsSyntaxValidator.validateCode(code)
                    if (syntaxError != null) {
                        code = LlamaBridge.generateSingleFile(
                            taskPath = nextTask.filePath,
                            fileType = "CORRECAO_JS",
                            userRequest = "Corrija este erro de sintaxe ($syntaxError) no código:\n$code",
                            routeHistory = traveledRoute,
                            learnedKnowledge = ""
                        )
                        LlamaBridge.clearContextMemory()
                        code = cleanMarkdownCode(code)
                    }
                }

                val targetPath = if (nextTask.filePath.startsWith("Behavior_Pack") || nextTask.filePath.startsWith("Resource_Pack")) {
                    nextTask.filePath
                } else {
                    "Behavior_Pack/scripts/${nextTask.filePath.substringAfterLast("/")}.js"
                }

                val outFile = File(WorkspaceManager.buildDir, targetPath)
                outFile.parentFile?.mkdirs()
                outFile.writeText(code)
                db.markAsGenerated(nextTask.id, "Arquivo $targetPath gerado e revisado.")
            }

            delay(1500)
            nextTask = db.getNextPendingTask()
        }

        updateNotification("Empacotando .mcaddon final...")
        val finalAddon = AddonPackager.packBuildDirToMcAddon(WorkspaceManager.buildDir, WorkspaceManager.outputDir)
        sendChatLog("Addon pronto e revisado!\nSalvo em: ${finalAddon.absolutePath}")
    }

    private fun cleanMarkdownCode(raw: String): String {
        return raw.replace(Regex("^```[a-zA-Z]*\\s*"), "").replace(Regex("\\s*```$"), "").trim()
    }

    private fun sendChatLog(message: String) {
        sendBroadcast(Intent("ADDON_CHAT_UPDATE").putExtra("MSG", message))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MCAddonAI::WorkerWakeLock")
        wakeLock?.acquire(4 * 60 * 60 * 1000L)
    }

    private fun startForegroundNotification(text: String) {
        val channelId = "mcaddon_ai_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Motor IA Addons", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MC Addon Studio AI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
        startForeground(1, notification)
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, "mcaddon_ai_channel")
            .setContentTitle("MC Addon Studio AI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
        getSystemService(NotificationManager::class.java).notify(1, notification)
    }

    override fun onDestroy() {
        wakeLock?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
