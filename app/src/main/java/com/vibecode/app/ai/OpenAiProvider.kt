package com.vibecode.app.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class OpenAiProvider : AiProvider {
    override val name = "openai"

    override fun chat(apiKey: String, model: String, messages: List<ChatMessage>, onToken: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("https://api.openai.com/v1/chat/completions")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 120000
                }
                val body = JSONObject().apply {
                    put("model", model.ifBlank { "gpt-4o-mini" })
                    put("stream", true)
                    val arr = JSONArray()
                    messages.forEach { m ->
                        arr.put(JSONObject().apply { put("role", m.role); put("content", m.content) })
                    }
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
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices") ?: continue
                        if (choices.length() == 0) continue
                        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                        val content = delta.optString("content", "")
                        if (content.isNotEmpty()) onToken(content)
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
