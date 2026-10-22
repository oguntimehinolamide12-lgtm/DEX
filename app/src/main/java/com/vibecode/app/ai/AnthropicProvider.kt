package com.vibecode.app.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AnthropicProvider : AiProvider {
    override val name = "anthropic"

    override fun chat(apiKey: String, model: String, messages: List<ChatMessage>, onToken: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("https://api.anthropic.com/v1/messages")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("anthropic-version", "2023-06-01")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 120000
                }
                // Separate system message
                val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
                val arr = JSONArray()
                messages.filter { it.role != "system" }.forEach { m ->
                    arr.put(JSONObject().apply { put("role", m.role); put("content", m.content) })
                }
                val body = JSONObject().apply {
                    put("model", model.ifBlank { "claude-3-5-haiku-latest" })
                    put("max_tokens", 4096)
                    put("stream", true)
                    if (system.isNotBlank()) put("system", system)
                    put("messages", arr)
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
                        val type = json.optString("type", "")
                        if (type == "content_block_delta") {
                            val delta = json.optJSONObject("delta")
                            val text = delta?.optString("text", "") ?: ""
                            if (text.isNotEmpty()) onToken(text)
                        } else if (type == "message_stop") {
                            break
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
