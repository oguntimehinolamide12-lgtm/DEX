package com.vibecode.app.debug

object DebugLog {
    private val buffer = ArrayDeque<String>()
    private const val MAX = 500
    private val listeners = mutableListOf<(String) -> Unit>()

    fun log(tag: String, msg: String) {
        val line = "[${System.currentTimeMillis()}] $tag: $msg"
        synchronized(buffer) {
            buffer.addLast(line)
            while (buffer.size > MAX) buffer.removeFirst()
        }
        synchronized(listeners) { listeners.toList() }.forEach { it(line) }
    }

    fun snapshot(): List<String> = synchronized(buffer) { buffer.toList() }

    fun clear() = synchronized(buffer) { buffer.clear() }

    fun addListener(l: (String) -> Unit) = synchronized(listeners) { listeners.add(l) }
    fun removeListener(l: (String) -> Unit) = synchronized(listeners) { listeners.remove(l) }
}