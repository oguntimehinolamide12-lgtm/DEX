package com.vibecode.app.ai

import android.content.Context
import android.os.Environment
import com.vibecode.app.brain.BrainPack
import com.vibecode.app.brain.Delegate
import com.vibecode.app.cloud.CloudBridge
import com.vibecode.app.debug.PythonRunner
import com.vibecode.app.fs.FileManager
import com.vibecode.app.util.Prefs
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Tools the agent can call. A tool call is emitted as
 * `<tool>{"name":"read_file","args":{"path":"src/app.js"}}</tool>` so the whole
 * call (including multi-line file contents) is unambiguous JSON instead of
 * fragile bracket tags.
 */
object AgentTools {

    private val toolRegex = Regex("<tool>([\\s\\S]*?)</tool>", RegexOption.IGNORE_CASE)
    private const val MAX_READ_LINES = 400
    private const val MAX_READ_CHARS = 20000
    private const val MAX_SEARCH_HITS = 60
    private const val MAX_LIST_ENTRIES = 300
    private const val SHELL_TIMEOUT_SECONDS = 20L

    data class Call(val name: String, val args: JSONObject) {
        val displayName: String
            get() = when (name) {
                "read", "read_file" -> "read_file"
                "write", "write_file", "create_file" -> "write_file"
                "edit", "edit_file", "replace_in_file" -> "edit_file"
                "ls", "list_dir" -> "list_dir"
                "grep", "search" -> "search"
                else -> name
            }

        val summary: String
            get() {
                for (key in listOf("path", "url", "query", "language", "code", "name", "agent")) {
                    val v = args.optString(key, "")
                    if (v.isNotBlank()) return v.replace("\n", " ").take(90)
                }
                val content = args.optString("content", "")
                if (content.isNotBlank()) return "${content.length} chars"
                return ""
            }
    }

    data class Result(val ok: Boolean, val output: String)

    fun parse(reply: String): List<Call> {
        val calls = mutableListOf<Call>()
        for (match in toolRegex.findAll(reply)) {
            val raw = match.groupValues[1].trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            try {
                val obj = JSONObject(raw)
                val name = obj.optString("name").lowercase().trim()
                if (name.isBlank()) continue
                val args = obj.optJSONObject("args") ?: obj.optJSONObject("arguments") ?: flatArgs(obj)
                calls.add(Call(name, args))
            } catch (_: Exception) {
                // Malformed call: skip it, the model gets a step to correct itself.
            }
        }
        return calls
    }

    private fun flatArgs(obj: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k == "name") continue
            out.put(k, obj.get(k))
        }
        return out
    }

    fun execute(call: Call, host: AgentHost?, ctx: Context?): Result {
        val exempt = call.name in setOf(
            "browser_open", "browser_console", "remote_run", "cloud_run",
            "skills", "skill", "delegate", "subagent"
        )
        if (!Prefs.fileAccess() && !exempt) {
            return Result(false, "File tools are disabled. Ask the user to enable 'Allow AI agent to access files' in Settings.")
        }
        return try {
            when (call.name) {
                "list_dir", "ls" -> listDir(call.args)
                "read_file", "read" -> readFile(call.args)
                "write_file", "write", "create_file" -> writeFile(call.args)
                "edit_file", "edit", "replace_in_file" -> editFile(call.args)
                "search", "grep" -> search(call.args)
                "run", "shell", "run_code" -> run(call.args)
                "fetch_url", "fetch" -> fetchUrl(call.args)
                "open_file" -> {
                    val f = resolve(call.args.optString("path", ""))
                        ?: return Result(false, "Missing path.")
                    if (!f.exists()) return Result(false, "File does not exist: ${f.absolutePath}")
                    host?.openInEditor(f.absolutePath) ?: return Result(false, "Editor is not available.")
                    Result(true, "Opened ${f.absolutePath} in the editor.")
                }
                "apply_to_editor" -> {
                    val content = call.args.optString("content", "")
                    if (content.isBlank()) return Result(false, "Missing content.")
                    host?.applyToEditor(content) ?: return Result(false, "Editor is not available.")
                    Result(true, "Replaced the editor buffer with ${content.length} chars (not saved to disk).")
                }
                "skills" -> {
                    val query = call.args.optString("query", "")
                    val kind = call.args.optString("kind", "")
                    val hits = BrainPack.search(query, kind.ifBlank { null }, 15)
                    if (hits.isEmpty()) return Result(false, "No brain entry matched \"$query\".")
                    Result(true, hits.joinToString("\n") { "${it.kind}: ${it.name} — ${it.description}" })
                }
                "skill" -> {
                    val name = call.args.optString("name", "")
                    if (name.isBlank()) return Result(false, "Missing name.")
                    val kind = call.args.optString("kind", "skill").ifBlank { "skill" }
                    val entry = BrainPack.exact(kind, name)
                        ?: BrainPack.search(name, kind, 1).firstOrNull()
                    if (entry == null) {
                        return Result(false, "Unknown $kind '$name'. Use skills {query} to find one.")
                    }
                    Result(true, BrainPack.read(entry, 10000).ifBlank { "(${entry.name} is empty)" })
                }
                "delegate", "subagent" -> {
                    if (!BrainPack.isReady()) return Result(false, "Brain pack is not loaded.")
                    val agent = call.args.optString("agent", "")
                    val task = call.args.optString("task", call.args.optString("prompt", ""))
                    if (agent.isBlank() || task.isBlank()) {
                        return Result(false, "delegate needs 'agent' and 'task'.")
                    }
                    Result(true, Delegate.run(agent, task))
                }
                "browser_open" -> {
                    val url = normalizeUrl(call.args.optString("url", ""))
                    if (url.isBlank()) return Result(false, "Missing url.")
                    host?.openBrowser(url) ?: return Result(false, "Browser is not available.")
                    Result(true, "Browser loading $url")
                }
                "browser_console" -> {
                    val log = host?.browserConsoleLog()?.takeIf { it.isNotBlank() } ?: "(browser console is empty)"
                    Result(true, log)
                }
                "remote_run", "cloud_run" -> {
                    if (!CloudBridge.isConnected()) {
                        return Result(false, "No cloud workspace is connected. Open the CL pane and connect an SSH workspace first.")
                    }
                    val command = call.args.optString("code", call.args.optString("command", ""))
                    if (command.isBlank()) return Result(false, "Missing command.")
                    Result(true, CloudBridge.run(command).take(12000))
                }
                else -> Result(false, "Unknown tool '${call.name}'.")
            }
        } catch (e: Exception) {
            Result(false, "${call.displayName} failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ---------------------------------------------------------------- files

    private fun listDir(args: JSONObject): Result {
        val base = resolve(args.optString("path", "")) ?: workspaceRoot()
        if (!base.exists()) return Result(false, "Path does not exist: ${base.absolutePath}")
        if (base.isFile) return Result(false, "${base.absolutePath} is a file, use read_file.")
        val depth = args.optInt("depth", 1).coerceIn(1, 3)
        val root = workspaceRoot()
        val sb = StringBuilder()
        sb.append("Listing ${base.absolutePath} (depth $depth)\n")
        var count = 0
        var truncated = false
        fun walk(dir: File, prefix: String, level: Int) {
            val kids = try {
                dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            } catch (_: Exception) {
                null
            } ?: return
            for (f in kids) {
                if (count >= MAX_LIST_ENTRIES) { truncated = true; return }
                if (f.name.startsWith(".") && f.isDirectory) continue
                count++
                if (f.isDirectory) {
                    sb.append(prefix).append(f.name).append("/\n")
                    if (level < depth) walk(f, "$prefix  ", level + 1)
                } else {
                    sb.append(prefix).append(f.name)
                    sb.append("  (").append(humanSize(f.length())).append(")\n")
                }
            }
        }
        walk(base, "", 1)
        if (truncated) sb.append("… listing truncated at $MAX_LIST_ENTRIES entries\n")
        sb.append("\nProject root: ${root.absolutePath}")
        return Result(true, sb.toString())
    }

    private fun readFile(args: JSONObject): Result {
        val f = resolve(args.optString("path", "")) ?: return Result(false, "Missing path.")
        if (!f.exists()) return Result(false, "File not found: ${f.absolutePath}")
        if (f.isDirectory) return Result(false, "${f.absolutePath} is a directory, use list_dir.")
        if (f.length() > 3_000_000) return Result(false, "File is too large (${humanSize(f.length())}); read a range instead.")
        if (!FileManager.isTextFile(f)) return Result(false, "Binary file, refusing to read ${f.name}.")
        val lines = try { f.readLines() } catch (e: Exception) { return Result(false, "Read failed: ${e.message}") }
        val total = lines.size
        val start = args.optInt("start", 1).coerceAtLeast(1)
        val end = args.optInt("end", start + MAX_READ_LINES - 1).coerceAtLeast(start)
        if (start > total && total > 0) return Result(false, "start=$start is past the end of the file ($total lines).")
        val from = (start - 1).coerceAtMost(maxOf(total - 1, 0))
        val to = end.coerceAtMost(total)
        val sb = StringBuilder()
        sb.append("${f.absolutePath}  (lines $start-$to of $total)\n")
        var chars = 0
        var cut = false
        for (i in from until to) {
            val line = "%5d| %s\n".format(i + 1, lines[i])
            if (chars + line.length > MAX_READ_CHARS) { cut = true; break }
            sb.append(line)
            chars += line.length
        }
        if (cut) sb.append("… truncated at $MAX_READ_CHARS chars; request a smaller range.\n")
        if (to < total) sb.append("… ${total - to} more lines; use start/end to continue.\n")
        return Result(true, sb.toString())
    }

    private fun writeFile(args: JSONObject): Result {
        val f = resolve(args.optString("path", "")) ?: return Result(false, "Missing path.")
        if (f.isDirectory) return Result(false, "${f.absolutePath} is a directory.")
        if (!args.has("content")) return Result(false, "Missing content.")
        val content = args.optString("content", "")
        f.parentFile?.mkdirs()
        return try {
            f.writeText(content, Charsets.UTF_8)
            val lines = content.count { it == '\n' } + 1
            Result(true, "Wrote $lines lines ($content bytes) to ${f.absolutePath}")
        } catch (e: Exception) {
            Result(false, "Write failed: ${e.message}")
        }
    }

    private fun editFile(args: JSONObject): Result {
        val f = resolve(args.optString("path", "")) ?: return Result(false, "Missing path.")
        if (!f.exists() || f.isDirectory) return Result(false, "File not found: ${f.absolutePath}")
        val find = args.optString("find", "")
        if (find.isEmpty()) return Result(false, "Missing 'find' text.")
        val replace = args.optString("replace", "")
        val replaceAll = args.optBoolean("replace_all", false)
        val original = try { f.readText() } catch (e: Exception) { return Result(false, "Read failed: ${e.message}") }
        val occurrences = countOccurrences(original, find)
        if (occurrences == 0) {
            return Result(false, "Exact 'find' text was not found in ${f.name}. read_file and copy the text verbatim (whitespace matters).")
        }
        if (occurrences > 1 && !replaceAll) {
            return Result(false, "The 'find' text occurs $occurrences times in ${f.name}. Add surrounding context or set replace_all=true.")
        }
        val updated = if (replaceAll) original.replace(find, replace) else original.replaceFirst(find, replace)
        return try {
            f.writeText(updated, Charsets.UTF_8)
            Result(true, "Edited ${f.absolutePath}: ${if (replaceAll) occurrences else 1} replacement(s).")
        } catch (e: Exception) {
            Result(false, "Write failed: ${e.message}")
        }
    }

    private fun search(args: JSONObject): Result {
        val query = args.optString("query", "")
        if (query.isBlank()) return Result(false, "Missing query.")
        val base = resolve(args.optString("path", "")) ?: workspaceRoot()
        if (!base.exists()) return Result(false, "Path does not exist: ${base.absolutePath}")
        val useRegex = args.optBoolean("regex", false)
        val regex = if (useRegex) {
            try { Regex(query, RegexOption.IGNORE_CASE) } catch (e: Exception) {
                return Result(false, "Invalid regex: ${e.message}")
            }
        } else null
        val root = workspaceRoot()
        val sb = StringBuilder()
        var hits = 0
        var filesScanned = 0
        val stack = ArrayDeque<File>().apply { add(base) }
        val textExts = setOf(
            "kt","kts","java","py","js","jsx","ts","tsx","html","htm","css","scss","xml","json","md","txt",
            "yml","yaml","toml","gradle","properties","sh","bash","c","cpp","h","hpp","cs","go","rs","rb",
            "php","sql","swift","dart","lua","vue","svelte","gitignore"
        )
        while (stack.isNotEmpty() && hits < MAX_SEARCH_HITS && filesScanned < 2000) {
            val dir = stack.removeLast()
            val kids = try { dir.listFiles() } catch (_: Exception) { null } ?: continue
            for (f in kids) {
                if (hits >= MAX_SEARCH_HITS) break
                if (f.isDirectory) {
                    if (!f.name.startsWith(".") && f.name !in setOf("build", "node_modules", ".git", "__pycache__")) stack.add(f)
                    continue
                }
                if (f.length() > 2_000_000) continue
                if (f.extension.lowercase() !in textExts && f.length() > 200_000) continue
                filesScanned++
                val lines = try { f.readLines() } catch (_: Exception) { continue }
                val rel = f.absolutePath.removePrefix(root.absolutePath).trimStart('/')
                for ((i, line) in lines.withIndex()) {
                    val match = if (regex != null) regex.containsMatchIn(line) else line.contains(query, ignoreCase = true)
                    if (!match) continue
                    sb.append(rel).append(':').append(i + 1).append(": ").append(line.trim().take(200)).append('\n')
                    hits++
                    if (hits >= MAX_SEARCH_HITS) break
                }
            }
        }
        if (hits == 0) return Result(true, "No matches for \"$query\" under ${base.absolutePath} ($filesScanned files scanned).")
        if (hits >= MAX_SEARCH_HITS) sb.append("… stopped at $MAX_SEARCH_HITS matches\n")
        sb.append("\n$hits match(es) in $filesScanned files")
        return Result(true, sb.toString())
    }

    // ------------------------------------------------------------- execution

    private fun run(args: JSONObject): Result {
        val code = args.optString("code", "")
        if (code.isBlank()) return Result(false, "Missing code.")
        val language = args.optString("language", "shell").lowercase()
        return when (language) {
            "python", "py" -> {
                if (!PythonRunner.isAvailable()) Result(false, "Python runtime is not available on this device build.")
                else Result(true, PythonRunner.run(code).take(12000))
            }
            "js", "javascript" -> Result(false, "Use browser_open with an HTML page and browser_console to run JavaScript.")
            else -> runShell(code)
        }
    }

    private fun runShell(cmd: String): Result {
        val process = try {
            ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        } catch (e: Exception) {
            return Result(false, "Could not start shell: ${e.message}")
        }
        val out = StringBuilder()
        val reader = Thread {
            try {
                process.inputStream.bufferedReader().use { br ->
                    br.forEachLine { out.append(it).append('\n') }
                }
            } catch (_: Exception) { }
        }
        reader.start()
        // Process.waitFor(timeout, unit) needs API 26; join() keeps minSdk 24 working.
        val waiter = Thread {
            try { process.waitFor() } catch (_: Exception) { }
        }
        waiter.start()
        waiter.join(TimeUnit.SECONDS.toMillis(SHELL_TIMEOUT_SECONDS))
        if (waiter.isAlive) {
            process.destroyForcibly()
            return Result(false, "Command timed out after ${SHELL_TIMEOUT_SECONDS}s\n${out.toString().take(6000)}")
        }
        reader.join(1000)
        val text = out.toString().take(12000).ifBlank { "(no output)" }
        val code = process.exitValue()
        return Result(code == 0, "exit $code\n$text")
    }

    private fun fetchUrl(args: JSONObject): Result {
        val raw = args.optString("url", "")
        if (raw.isBlank()) return Result(false, "Missing url.")
        val url = normalizeUrl(raw)
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20000
                readTimeout = 25000
                setRequestProperty("User-Agent", "DEX-Android-IDE/1.0")
                setRequestProperty("Accept", "text/html,application/json,text/plain,*/*")
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val type = conn.contentType ?: ""
            val text = if (type.contains("html", ignoreCase = true)) stripHtml(body) else body
            val header = "HTTP $status  $url  (${type.ifBlank { "unknown type" }}, ${body.length} bytes)\n\n"
            Result(status in 200..299, header + text.take(9000))
        } catch (e: Exception) {
            Result(false, "Fetch failed: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }

    // --------------------------------------------------------------- helpers

    fun workspaceRoot(): File {
        val last = Prefs.lastFolder()
        if (last.isNotBlank()) {
            val f = File(last)
            if (f.exists()) return f
        }
        return try { Environment.getExternalStorageDirectory() } catch (_: Exception) { File("/sdcard") }
    }

    private fun resolve(path: String): File? {
        val clean = path.trim().removePrefix("file://")
        if (clean.isBlank()) return null
        val file = if (clean.startsWith("/")) File(clean) else File(workspaceRoot(), clean)
        return file.absoluteFile
    }

    private fun normalizeUrl(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file://") ||
            lower.startsWith("about:") || lower.startsWith("data:")
        ) return text
        if (text.startsWith("/")) return "file://$text"
        return "https://$text"
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            count++
            index = haystack.indexOf(needle, index + needle.length)
        }
        return count
    }

    private fun stripHtml(html: String): String {
        var text = Regex("<script[\\s\\S]*?</script>|<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE).replace(html, " ")
        text = Regex("<[^>]+>").replace(text, " ")
        text = text.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        text = Regex("[ \\t\\x0B\\f\\r]+").replace(text, " ")
        text = Regex("\\n\\s*\\n\\s*\\n+").replace(text, "\n\n")
        return text.trim()
    }

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
