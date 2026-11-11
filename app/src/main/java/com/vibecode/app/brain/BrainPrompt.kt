package com.vibecode.app.brain

/** Builds the always-on brain section of the system prompt. */
object BrainPrompt {

    fun build(): String {
        if (!BrainPack.isReady()) return ""
        val core = BrainPack.core().trim()
        val skills = BrainPack.search("", "skill", 400).size
        val agents = BrainPack.agentNames().size
        val names = BrainPack.agentNames().take(28).joinToString(", ")
        return buildString {
            appendLine("BRAIN — everything-claude-code playbook (MIT © Affaan Mustafa), vendored into this app.")
            if (core.isNotBlank()) appendLine(core)
            appendLine()
            appendLine("SKILL LIBRARY: $skills skills, $agents specialist agents.")
            appendLine("Before non-trivial work, load the matching playbook instead of improvising:")
            appendLine("  skills {query:\"failing test\"}          find playbooks (kinds: skill, rule, agent)")
            appendLine("  skill  {name:\"tdd-workflow\"}           read one in full (or pass kind:\"rule\"/\"agent\")")
            appendLine("Use rules for conventions (e.g. kotlin/coding-style, common/security) and skills for workflows")
            appendLine("(tdd-workflow, verification-loop, security-review, api-design, backend-patterns, codebase-onboarding).")
            appendLine("DELEGATION: delegate {agent, task} runs a specialist in a fresh context and returns its answer.")
            appendLine("Good agents: $names.")
            appendLine("Delegate planning before large rewrites and code review after risky ones; the lead (you) still owns")
            appendLine("the edits, the tool calls and the final verification.")
        }.trimEnd()
    }
}
