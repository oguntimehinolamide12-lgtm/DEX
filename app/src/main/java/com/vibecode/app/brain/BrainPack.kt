package com.vibecode.app.brain

import android.content.Context
import android.content.res.AssetManager

/**
 * The vendored ECC (everything-claude-code) playbook: agents, rules and skills
 * shipped as markdown assets and loaded on demand, never all at once.
 */
object BrainPack {

    private const val INDEX = "brain/index.tsv"
    private const val CORE = "brain/core.md"
    private const val MAX_CACHED = 12

    @Volatile private var assets: AssetManager? = null

    @Volatile private var entries: List<BrainEntry> = emptyList()

    private val cache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_CACHED
    }

    @Volatile private var coreText: String? = null

    fun init(context: Context) {
        if (assets != null) return
        synchronized(this) {
            if (assets != null) return
            val am = context.applicationContext.assets
            assets = am
            entries = BrainIndex.parse(readText(am, INDEX) ?: "")
        }
    }

    fun isReady(): Boolean = assets != null && entries.isNotEmpty()

    fun size(): Int = entries.size

    fun search(query: String, kind: String? = null, limit: Int = 12): List<BrainEntry> =
        BrainIndex.search(entries, query, kind, limit)

    fun exact(kind: String, name: String): BrainEntry? = BrainIndex.exact(entries, kind, name)

    fun agentNames(): List<String> = entries.filter { it.kind == "agent" }.map { it.name }

    fun read(entry: BrainEntry, maxChars: Int = 9000): String {
        val am = assets ?: return ""
        val cached = synchronized(cache) { cache[entry.path] }
        val text = cached ?: run {
            val loaded = readText(am, entry.path) ?: ""
            if (loaded.isNotBlank()) synchronized(cache) { cache[entry.path] = loaded }
            loaded
        }
        return text.take(maxChars)
    }

    /** Compact operating playbook injected into every run. */
    fun core(): String {
        coreText?.let { return it }
        val am = assets ?: return ""
        val text = readText(am, CORE) ?: ""
        coreText = text
        return text
    }

    private fun readText(am: AssetManager, path: String): String? = try {
        am.open(path).bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }
}
