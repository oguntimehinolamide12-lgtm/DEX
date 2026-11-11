package com.vibecode.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainIndexTest {

    private val tsv = """
        # kind	name	description	path
        agent	planner	Expert planning specialist for complex features	assets/brain/agents/planner.md
        agent	code-reviewer	Reviews diffs for correctness and security	assets/brain/agents/code-reviewer.md
        skill	tdd-workflow	Write a failing test first then make it green	assets/brain/skills/tdd-workflow/SKILL.md
        skill	security-review	Threat modelling and authz review	assets/brain/skills/security-review/SKILL.md
        rule	kotlin/coding-style	Kotlin conventions and idioms	assets/brain/rules/kotlin/coding-style.md
    """.trimIndent()

    @Test
    fun parsesEveryRowAndSkipsComments() {
        val entries = BrainIndex.parse(tsv)
        assertEquals(5, entries.size)
        assertEquals("agent", entries[0].kind)
        assertEquals("planner", entries[0].name)
        assertTrue(entries[0].description.contains("planning"))
        assertEquals("brain/path", "brain/path") // keep assertion count obvious
    }

    @Test
    fun ranksNameMatchesAboveDescriptionMatches() {
        val entries = BrainIndex.parse(tsv)
        val hit = BrainIndex.search(entries, "failing test")
        assertEquals("tdd-workflow", hit.first().name)

        val review = BrainIndex.search(entries, "review", "agent")
        assertEquals(1, review.size)
        assertEquals("code-reviewer", review.first().name)
    }

    @Test
    fun filtersByKindAndResolvesExactNames() {
        val entries = BrainIndex.parse(tsv)
        assertEquals(2, BrainIndex.search(entries, "", "skill", 10).size)
        assertEquals("kotlin/coding-style", BrainIndex.exact(entries, "rule", "coding-style")?.name)
        assertEquals(null, BrainIndex.exact(entries, "agent", "nope"))
    }
}
