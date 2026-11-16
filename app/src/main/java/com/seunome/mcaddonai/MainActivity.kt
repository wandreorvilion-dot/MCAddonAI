package com.seunome.mcaddonai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var currentMode = "CREATE_ADDON"
    private var isModelLoaded = false

    private lateinit var tvStatusBanner: TextView
    private lateinit var tvChatHistory: TextView
    private lateinit var etUserMessage: EditText
    private lateinit var btnSend: Button

    private val chatReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val msg = intent?.getStringExtra("MSG") ?: return
            appendChat("\n[IA]: $msg")
            tvStatusBanner.text = "Concluído! Pronto para o próximo pedido."
        }
    }

    private val pickModelLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { copyAndLoadModel(it) }
    }

    private val pickJarLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { startWorkerService("TRANSCRIBE_JAR", "", it) }
    }

    private val pickLearnAddonLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { startWorkerService("LEARN_ADDON", "", it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_clean)

        tvStatusBanner = findViewById(R.id.tvStatusBanner)
        tvChatHistory = findViewById(R.id.tvChatHistory)
        etUserMessage = findViewById(R.id.etUserMessage)
        btnSend = findViewById(R.id.btnSend)

        checkStoragePermissions()
        WorkspaceManager.initializeWorkspace()

        btnSend.setOnClickListener {
            val prompt = etUserMessage.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener
            if (!isModelLoaded) {
                Toast.makeText(this, "Selecione primeiro um modelo .gguf nos 3 pontos!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            etUserMessage.text.clear()
            appendChat("\n[Você]: $prompt")
            startWorkerService(currentMode, prompt, null)
        }

        val filter = IntentFilter("ADDON_CHAT_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(chatReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(chatReceiver, filter)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_select_model -> {
                pickModelLauncher.launch(arrayOf("*/*"))
                true
            }
            R.id.menu_mode_create -> {
                currentMode = "CREATE_ADDON"
                tvStatusBanner.text = "Modo: Criar Addon (Usa Aprendizado)"
                true
            }
            R.id.menu_mode_transcribe -> {
                if (!isModelLoaded) {
                    Toast.makeText(this, "Carregue o modelo IA (.gguf) antes!", Toast.LENGTH_SHORT).show()
                } else {
                    currentMode = "TRANSCRIBE_JAR"
                    tvStatusBanner.text = "Selecione o arquivo .JAR para transcrever..."
                    pickJarLauncher.launch(arrayOf("*/*"))
                }
                true
            }
            R.id.menu_mode_learn -> {
                currentMode = "LEARN_ADDON"
                tvStatusBanner.text = "Selecione o .MCADDON para a IA estudar..."
                pickLearnAddonLauncher.launch(arrayOf("*/*"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun copyAndLoadModel(uri: Uri) {
        tvStatusBanner.text = "Preparando modelo IA na pasta dedicada..."
        Thread {
            try {
                val destFile = File(WorkspaceManager.modelsDir, "modelo_ativo.gguf")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, 8192)
                    }
                }
                val loaded = LlamaBridge.loadModel(destFile.absolutePath)
                runOnUiThread {
                    isModelLoaded = loaded
                    if (loaded) {
                        tvStatusBanner.text = "IA Carregada! Modo: Criar Addon"
                        appendChat("\n[Sistema]: Modelo IA carregado com sucesso.")
                    } else {
                        tvStatusBanner.text = "Falha ao carregar modelo .gguf"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun startWorkerService(mode: String, prompt: String, fileUri: Uri?) {
        tvStatusBanner.text = "Trabalhando em segundo plano (acompanhe na notificação)..."
        val intent = Intent(this, AiWorkerService::class.java).apply {
            putExtra("MODE", mode)
            putExtra("PROMPT", prompt)
            if (fileUri != null) putExtra("FILE_URI", fileUri.toString())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun appendChat(text: String) {
        tvChatHistory.append("\n$text")
    }

    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(chatReceiver)
        super.onDestroy()
    }
}
