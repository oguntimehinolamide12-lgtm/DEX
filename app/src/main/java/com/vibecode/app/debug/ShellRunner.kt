package com.vibecode.app.debug

object ShellRunner {
    fun run(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor()
            (out + if (err.isNotBlank()) "\n[stderr]\n$err" else "").ifBlank { "(no output)" }
        } catch (e: Exception) {
            "Shell error: ${e.message}"
        }
    }
}