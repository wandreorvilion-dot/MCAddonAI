package com.seunome.mcaddonai

import app.cash.quickjs.QuickJs

object JsSyntaxValidator {
    fun validateCode(jsCode: String): String? {
        val testableCode = jsCode.replace(Regex("import\\s+.*?from\\s+['\"].*?['\"];?"), "")
        return try {
            QuickJs.create().use { qjs ->
                qjs.compile(testableCode, "validation_check.js")
            }
            null
        } catch (e: Exception) {
            e.message ?: "Erro de sintaxe no JavaScript"
        }
    }
}
