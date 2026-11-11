package com.vibecode.app.ai

interface AiProvider {
    val name: String

    /**
     * @param isCancelled polled between stream chunks so a long agent run can be
     * stopped from the UI without waiting for the model to finish.
     */
    fun chat(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
        isCancelled: () -> Boolean = { false }
    )
}

data class ChatMessage(val role: String, val content: String)
