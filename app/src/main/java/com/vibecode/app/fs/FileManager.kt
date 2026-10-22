package com.vibecode.app.fs

import java.io.File

object FileManager {
    fun listDir(dir: File): List<File> {
        val files = dir.listFiles() ?: return emptyList()
        return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun readText(file: File): String = try {
        file.readText(Charsets.UTF_8)
    } catch (e: Exception) {
        ""
    }

    fun writeText(file: File, content: String): Boolean = try {
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        true
    } catch (e: Exception) {
        false
    }

    fun isTextFile(file: File): Boolean {
        if (file.isDirectory) return false
        val ext = file.extension.lowercase()
        val textExts = setOf("kt","java","py","js","ts","jsx","tsx","html","css","xml","json","md","txt","yml","yaml","toml","gradle","kts","sh","c","cpp","h","hpp","cs","go","rs","rb","php","sql","properties","ini","cfg","log")
        return ext in textExts || file.length() < 1_000_000
    }

    fun languageFor(file: File): String = when (file.extension.lowercase()) {
        "kt","kts" -> "kotlin"
        "java" -> "java"
        "py" -> "python"
        "js","jsx" -> "javascript"
        "ts","tsx" -> "typescript"
        "html","xml" -> "markup"
        "css" -> "css"
        "json" -> "json"
        "md" -> "markdown"
        "c","cpp","h","hpp" -> "cpp"
        "cs" -> "csharp"
        "go" -> "go"
        "rs" -> "rust"
        "sh" -> "bash"
        "sql" -> "sql"
        "yml","yaml" -> "yaml"
        "gradle" -> "groovy"
        else -> "text"
    }
}
