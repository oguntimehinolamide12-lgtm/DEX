package com.vibecode.app.ai

import android.content.Context
import com.vibecode.app.util.Prefs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs the agent loop: stream a reply, execute any tool calls it contains, feed
 * the results back, repeat until the model stops asking for tools (or the step
 * budget / user cancellation ends the run).
 */
object AgentSession {

    interface Listener {
        /** Full accumulated text of the assistant message currently streaming. */
        fun onDelta(text: String)
        fun onAssistantDone(text: String)
        fun onToolStart(name: String, summary: String)
        fun onToolResult(name: String, summary: String, ok: Boolean, output: String)
        fun onError(message: String)
        fun onFinished(stopped: Boolean)
    }

    private val lock = Any()
    private var running = false
    private var cancelled = false
    private var generation = 0

    fun isRunning(): Boolean = synchronized(lock) { running }

    fun cancel() {
        synchronized(lock) { cancelled = true }
    }

    fun start(
        history: MutableList<ChatMessage>,
        system: String,
        host: AgentHost?,
        ctx: Context?,
        listener: Listener
    ) {
        val gen: Int
        synchronized(lock) {
            if (running) return
            running = true
            cancelled = false
            gen = ++generation
        }

        Thread({
            var fatal: String? = null
            var stopped = false
            try {
                val convo = ArrayList<ChatMessage>(history.size + 4)
                if (system.isNotBlank()) convo.add(ChatMessage("system", system))
                convo.addAll(history)

                val maxSteps = Prefs.agentSteps()
                var step = 0
                while (true) {
                    if (isCancelled(gen)) { stopped = true; break }

                    val result = streamOnce(convo, gen, listener)
                    if (isCancelled(gen)) { stopped = true; break }
                    if (result.error != null) { fatal = result.error; break }
                    val reply = result.text.orEmpty()
                    if (reply.isBlank()) {
                        fatal = "The model returned an empty response."
                        break
                    }

                    // Providers stream with the whole conversation as-is; keep the
                    // assistant turn in context so tool results make sense.
                    history.add(ChatMessage("assistant", reply))
                    convo.add(ChatMessage("assistant", reply))
                    listener.onAssistantDone(reply)

                    val calls = AgentTools.parse(reply)
                    if (calls.isEmpty()) break

                    if (step >= maxSteps) {
                        listener.onToolResult("limit", "step limit", false, "Stopped after $maxSteps steps.")
                        break
                    }
                    step++

                    for (call in calls) {
                        if (isCancelled(gen)) { stopped = true; break }
                        listener.onToolStart(call.displayName, call.summary)
                        val result = AgentTools.execute(call, host, ctx)
                        listener.onToolResult(call.displayName, call.summary, result.ok, result.output)
                        val toolMessage = "TOOL RESULT ${call.displayName} [${if (result.ok) "OK" else "ERROR"}]\n" +
                            result.output.take(14000)
                        convo.add(ChatMessage("user", toolMessage))
                        history.add(ChatMessage("user", toolMessage))
                    }
                }
            } catch (t: Throwable) {
                fatal = t.message ?: t.javaClass.simpleName
            } finally {
                synchronized(lock) { running = false }
                if (fatal != null) listener.onError(fatal!!) else listener.onFinished(stopped)
            }
        }, "dex-agent").start()
    }

    private fun isCancelled(gen: Int): Boolean = synchronized(lock) { cancelled || gen != generation }

    private class Step(val text: String?, val error: String?)

    /** Streams a single assistant turn and returns its text or the error. */
    private fun streamOnce(convo: List<ChatMessage>, gen: Int, listener: Listener): Step {
        val sb = StringBuilder()
        val latch = CountDownLatch(1)
        var error: String? = null
        AiAgent.chat(
            convo,
            { token ->
                if (!isCancelled(gen)) {
                    sb.append(token)
                    listener.onDelta(sb.toString())
                }
            },
            { latch.countDown() },
            { message ->
                error = message
                latch.countDown()
            },
            { isCancelled(gen) }
        )
        if (!latch.await(180, TimeUnit.SECONDS)) {
            return Step(null, "Request timed out after 3 minutes.")
        }
        return Step(sb.toString(), error)
    }
}
