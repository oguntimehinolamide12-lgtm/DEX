package com.vibecode.app.browser

/**
 * Ring buffer of JS console messages emitted by the embedded browser.
 * The agent reads this through the browser_console tool, so it can debug
 * pages it generated without leaving the app.
 */
object BrowserConsole {
    private const val MAX = 200
    private val buffer = ArrayDeque<String>()
    private val listeners = mutableListOf<(String) -> Unit>()

    fun log(line: String) {
        synchronized(buffer) {
            buffer.addLast(line)
            while (buffer.size > MAX) buffer.removeFirst()
        }
        val copy = synchronized(listeners) { listeners.toList() }
        copy.forEach { it(line) }
    }

    fun snapshot(): List<String> = synchronized(buffer) { buffer.toList() }

    fun clear() = synchronized(buffer) { buffer.clear() }

    fun addListener(l: (String) -> Unit) = synchronized(listeners) { listeners.add(l) }

    fun removeListener(l: (String) -> Unit) = synchronized(listeners) { listeners.remove(l) }
}
