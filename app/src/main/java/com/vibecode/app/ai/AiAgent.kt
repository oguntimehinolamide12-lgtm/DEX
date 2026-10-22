package com.vibecode.app.ai

import com.vibecode.app.util.Prefs

object AiAgent {
    fun provider(): AiProvider = when (Prefs.provider()) {
        "anthropic" -> AnthropicProvider()
        "gemini" -> GeminiProvider()
        "openai-compatible", "custom", "ollama" -> OpenAiProvider(Prefs.baseUrl().ifBlank { "http://10.0.2.2:11434/v1" })
        else -> OpenAiProvider()
    }

    fun defaultModel(): String = when (Prefs.provider()) {
        "anthropic" -> "claude-3-5-haiku-latest"
        "gemini" -> "gemini-1.5-flash"
        "openai-compatible", "custom", "ollama" -> "llama3.1"
        else -> "gpt-4o-mini"
    }

    fun chat(messages: List<ChatMessage>, onToken: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val key = Prefs.apiKey(Prefs.provider())
        // custom endpoints may not require a key (e.g. local Ollama)
        if (key.isBlank() && Prefs.provider() in setOf("openai", "anthropic", "gemini")) {
            onError("No API key set. Open Settings to add one.")
            return
        }
        val model = Prefs.model().ifBlank { defaultModel() }
        provider().chat(key, model, messages, onToken, onDone, onError)
    }
}
