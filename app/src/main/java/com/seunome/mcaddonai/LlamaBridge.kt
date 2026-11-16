package com.seunome.mcaddonai

object LlamaBridge {
    init {
        System.loadLibrary("mcaddonai")
    }

    external fun loadModel(modelPath: String): Boolean
    external fun clearContextMemory()
    private external fun runInference(prompt: String): String

    fun generateSingleFile(
        taskPath: String,
        fileType: String,
        userRequest: String,
        routeHistory: String,
        learnedKnowledge: String
    ): String {
        val systemPrompt = """
Você é um engenheiro especialista em Addons para Minecraft Bedrock (Script API @minecraft/server em JavaScript e JSON).
Gere APENAS o conteúdo técnico puro do arquivo solicitado, sem explicações ou conversas.
Conhecimento aprendido de referência:
$learnedKnowledge

Rotas e arquivos já criados neste projeto:
$routeHistory
""".trimIndent()

        val fullPrompt = "<|im_start|>system\n$systemPrompt<|im_end|>\n<|im_start|>user\nCrie o arquivo '$taskPath' ($fileType) para o seguinte objetivo: $userRequest<|im_end|>\n<|im_start|>assistant\n"
        return runInference(fullPrompt)
    }

    fun planMindMap(userRequest: String, learnedKnowledge: String): List<Pair<String, String>> {
        val prompt = """
<|im_start|>system
Você é um arquiteto de Addons para Minecraft Bedrock. Liste apenas os caminhos dos arquivos necessários para criar o addon pedido, um por linha, no formato CAMINHO|TIPO (ex: Behavior_Pack/scripts/main.js|SCRIPT_JS).
Referência aprendida: $learnedKnowledge
<|im_end|>
<|im_start|>user
Planeje os arquivos para: $userRequest
<|im_end|>
<|im_start|>assistant
""".trimIndent()

        val rawPlan = runInference(prompt)
        clearContextMemory()

        val tasks = mutableListOf<Pair<String, String>>()
        rawPlan.lines().forEach { line ->
            val parts = line.trim().split("|")
            if (parts.size == 2 && parts[0].contains("/")) {
                tasks.add(Pair(parts[0].trim(), parts[1].trim()))
            }
        }
        if (tasks.isEmpty()) {
            tasks.add(Pair("Behavior_Pack/scripts/main.js", "SCRIPT_JS"))
        }
        return tasks
    }
}
