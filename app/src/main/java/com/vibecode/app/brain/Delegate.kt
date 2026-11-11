package com.vibecode.app.brain

import com.vibecode.app.ai.AiAgent
import com.vibecode.app.ai.ChatMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs one ECC specialist in a fresh context and returns its answer. Specialists
 * get no tools: they reason over the task text and report back, which keeps the
 * outer loop's tool budget and cancellation semantics intact.
 */
object Delegate {

    private const val TIMEOUT_SECONDS = 120L

    fun run(agent: String, task: String, maxChars: Int = 8000): String {
        val entry = BrainPack.exact("agent", agent)
            ?: BrainPack.search(agent, "agent", 1).firstOrNull()
            ?: return "Unknown agent '$agent'. Available agents: " +
                BrainPack.agentNames().take(40).joinToString(", ")

        val persona = BrainPack.read(entry, 9000)
        val system = buildString {
            appendLine(persona)
            appendLine()
            appendLine(BrainPack.core())
            appendLine()
            appendLine(
                "You are a delegated specialist inside DEX. You have no tools in this call. " +
                    "Work only from the task and any context it contains. Return a compact, concrete answer " +
                    "the lead agent can execute: findings, exact steps, code or commands where relevant. " +
                    "No preamble, no restating the task, no tool tags."
            )
        }.trim()

        val convo = listOf(
            ChatMessage("system", system),
            ChatMessage("user", task.take(16000))
        )
        val out = StringBuilder()
        val latch = CountDownLatch(1)
        var error: String? = null
        try {
            AiAgent.chat(
                convo,
                { token -> out.append(token) },
                { latch.countDown() },
                { message -> error = message; latch.countDown() },
                { false }
            )
        } catch (t: Throwable) {
            return "Subagent '${entry.name}' failed: ${t.message ?: t.javaClass.simpleName}"
        }
        if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return "Subagent '${entry.name}' timed out after ${TIMEOUT_SECONDS}s."
        }
        error?.let { return "Subagent '${entry.name}' failed: $it" }
        val text = out.toString().trim()
        return if (text.isBlank()) "(subagent '${entry.name}' returned nothing)" else text.take(maxChars)
    }
}
