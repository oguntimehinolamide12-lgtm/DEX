package com.vibecode.app.ai

interface AiProvider {
    val name: String
    fun chat(apiKey: String, model: String, messages: List<ChatMessage>, onToken: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit)
}

data class ChatMessage(val role: String, val content: String)
