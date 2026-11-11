package com.vibecode.app.brain

/** One entry in the vendored ECC brain pack (agent, rule or skill). */
data class BrainEntry(
    val kind: String,
    val name: String,
    val description: String,
    val path: String
)

/**
 * Parses and ranks the brain index. Deliberately free of Android types so it is
 * covered by plain JVM unit tests.
 */
object BrainIndex {

    fun parse(tsv: String): List<BrainEntry> = tsv.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            val kind = parts[0].trim().lowercase()
            val name = parts[1].trim()
            val path = parts[3].trim()
            if (kind.isBlank() || name.isBlank() || path.isBlank()) return@mapNotNull null
            BrainEntry(kind, name, parts[2].trim(), path)
        }
        .toList()

    fun search(
        entries: List<BrainEntry>,
        query: String,
        kind: String? = null,
        limit: Int = 12
    ): List<BrainEntry> {
        val pool = if (kind.isNullOrBlank()) entries else entries.filter { it.kind == kind.lowercase() }
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return pool.take(limit)
        return pool.mapNotNull { entry ->
            val name = entry.name.lowercase()
            val description = entry.description.lowercase()
            var score = 0
            for (term in terms) {
                if (name == term) score += 12
                if (name.contains(term)) score += 6
                if (name.startsWith(term)) score += 3
                if (description.contains(term)) score += 2
            }
            if (score > 0) entry to score else null
        }
            .sortedWith(compareByDescending<Pair<BrainEntry, Int>> { it.second }.thenBy { it.first.name })
            .take(limit)
            .map { it.first }
    }

    fun exact(entries: List<BrainEntry>, kind: String, name: String): BrainEntry? =
        entries.firstOrNull { it.kind == kind && it.name.equals(name, ignoreCase = true) }
            ?: entries.firstOrNull { it.kind == kind && it.name.substringAfter('/').equals(name, ignoreCase = true) }
}
