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
        val textExts = setOf("kt","kts","java","py","js","ts","jsx","tsx","html","htm","css","xml","json","md","txt","yml","yaml","toml","gradle","sh","bash","c","cpp","cc","cxx","h","hpp","hh","cs","go","rs","rb","php","sql","properties","ini","cfg","log","swift","dart","scala","hs","lua","pl","pm","r","jl","ex","exs","clj","cljs","m","mm","vb","ps1","psm1","dockerfile","makefile","gradle","groovy","scss","less","sass","vue","svelte")
        return ext in textExts || file.length() < 1_000_000
    }

    fun languageFor(file: File): String {
        val name = file.name.lowercase()
        if (name == "dockerfile") return "bash"
        if (name == "makefile") return "bash"
        return when (file.extension.lowercase()) {
            "kt","kts" -> "kotlin"
            "java" -> "java"
            "py","pyw" -> "python"
            "js","jsx","mjs","cjs" -> "javascript"
            "ts","tsx" -> "typescript"
            "html","htm","xml","xhtml","svg" -> "markup"
            "css","scss","less","sass" -> "css"
            "json" -> "json"
            "md","markdown" -> "markdown"
            "c","h" -> "c"
            "cpp","cc","cxx","hpp","hh","hxx" -> "cpp"
            "cs" -> "csharp"
            "go" -> "go"
            "rs" -> "rust"
            "sh","bash","zsh" -> "bash"
            "sql" -> "sql"
            "gradle","groovy" -> "groovy"
            "rb" -> "ruby"
            "php" -> "php"
            "swift" -> "swift"
            "dart" -> "dart"
            "scala","sc" -> "scala"
            "hs","lhs" -> "haskell"
            "lua" -> "lua"
            "pl","pm" -> "perl"
            "r" -> "r"
            "jl" -> "julia"
            "ex","exs" -> "elixir"
            "clj","cljs","cljc" -> "clojure"
            "m","mm" -> "objc"
            "vb" -> "vb"
            "ps1","psm1","psd1" -> "powershell"
            "yml","yaml" -> "yaml"
            "toml" -> "toml"
            "ini","cfg","conf","properties" -> "ini"
            "vue","svelte" -> "markup"
            else -> "text"
        }
    }
}
