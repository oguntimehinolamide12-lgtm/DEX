package com.vibecode.app.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiProvider : AiProvider {
    override val name = "gemini"

    override fun chat(apiKey: String, model: String, messages: List<ChatMessage>, onToken: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val m = model.ifBlank { "gemini-1.5-flash" }
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:streamGenerateContent?alt=sse&key=$apiKey")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 120000
                }
                // Build contents; system -> first user? Gemini supports systemInstruction
                val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
                val contents = JSONArray()
                messages.filter { it.role != "system" }.forEach { msg ->
                    val role = if (msg.role == "assistant") "model" else "user"
                    contents.put(JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().put(JSONObject().apply { put("text", msg.content) }))
                    })
                }
                val body = JSONObject().apply {
                    put("contents", contents)
                    if (system.isNotBlank()) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().apply { put("text", system) }))
                        })
                    }
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                if (conn.responseCode != 200) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
                    onError(err)
                    return@Thread
                }
                val reader = BufferedReader(conn.inputStream.reader())
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!.trim()
                    if (!l.startsWith("data:")) continue
                    val data = l.removePrefix("data:").trim()
                    try {
                        val json = JSONObject(data)
                        val cands = json.optJSONArray("candidates") ?: continue
                        if (cands.length() == 0) continue
                        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val t = parts.getJSONObject(i).optString("text", "")
                                if (t.isNotEmpty()) onToken(t)
                            }
                        }
                    } catch (_: Exception) { }
                }
                reader.close()
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            } finally {
                conn?.disconnect()
            }
        }.start()
    }
}
