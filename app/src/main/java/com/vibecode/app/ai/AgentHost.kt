package com.vibecode.app.ai

/**
 * The UI surface the agent can drive. Implemented by MainActivity so tools never
 * touch fragments directly.
 */
interface AgentHost {
    fun openInEditor(path: String)
    fun applyToEditor(text: String)
    fun openBrowser(url: String)
    fun browserConsoleLog(): String
}
