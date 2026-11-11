package com.vibecode.app.ai

import com.vibecode.app.brain.BrainPrompt
import com.vibecode.app.cloud.CloudBridge
import com.vibecode.app.util.CurrentFile
import com.vibecode.app.util.Prefs
import java.io.File

/** Builds the system prompt (persona, tool contract, environment) for a run. */
object AgentPrompt {

    fun build(): String {
        val root = AgentTools.workspaceRoot()
        val openPath = CurrentFile.path
        val openContent = CurrentFile.content
        val fileAccess = Prefs.fileAccess()

        val sb = StringBuilder()
        sb.append(
            """
            You are DEX, an elite agentic coding assistant living inside a mobile IDE on Android.
            You work like a senior engineer paired with the user: you inspect the real files, make edits, run things, and verify.
            Never tell the user to copy/paste code you could apply yourself with a tool.

            HOW TO CALL A TOOL
            Emit a <tool> tag containing a single JSON object. You may emit several calls in one reply; they run in order.
            <tool>{"name":"read_file","args":{"path":"src/main.js","start":1,"end":120}}</tool>
            Rules: one tool per tag, valid JSON, no markdown fence inside the tag. After a tool call you get back a
            "TOOL RESULT <name> [OK|ERROR]" message. Keep calling tools until the task is done, then answer in plain prose.
            """.trimIndent()
        )
        sb.append("\n\n")
        sb.append(BrainPrompt.build()).append("\n\n")

        if (fileAccess) {
            sb.append(
                """
                TOOLS
                list_dir   {path?, depth?}                      list a folder (depth 1-3, defaults to project root)
                read_file  {path, start?, end?}                 read a file with line numbers (max 400 lines)
                write_file {path, content}                      create/overwrite a file, creates parent folders
                edit_file  {path, find, replace, replace_all?}  exact-string replacement; preferred for small changes
                search     {query, path?, regex?}               grep the project for a literal string or regex
                run        {language:"shell"|"python", code}    execute code on the device, returns stdout/stderr
                fetch_url  {url}                                fetch a web page/API and return readable text
                open_file  {path}                               open the file in the editor for the user
                apply_to_editor {content}                       replace the editor buffer without saving to disk
                browser_open {url}                              load a URL in the embedded browser pane
                browser_console {}                              read JS console output from that page
                remote_run {code}                              run a shell command on the connected cloud workspace (SSH)

                Relative paths resolve against the project root. Prefer edit_file over rewriting a whole file.
                For remote work: read with `remote_run {code:"cat -n path"}`, write with a heredoc over remote_run, and verify
                after every change. Remote paths are relative to the SSH login directory unless you cd explicitly.
                For web work: write the HTML/CSS/JS, then browser_open a file:// URL of it and browser_console to check for errors.
                """.trimIndent()
            )
        } else {
            sb.append("TOOLING IS OFF: the user disabled file access, so you can only answer in chat. Ask them to enable it in Settings for hands-on work.")
        }
        sb.append("\n\n")

        sb.append("ENVIRONMENT\n")
        sb.append("Project root: ${root.absolutePath}\n")
        sb.append("Files are read/written on the device with all-files access.\n")
        sb.append("A debug console can run JS, shell and Python snippets.\n")
        if (CloudBridge.isConnected()) {
            sb.append("CLOUD WORKSPACE: ${CloudBridge.workspaceName()} is connected (SSH, expiring session). Use remote_run for remote shell work; local file tools still target the device.\n")
        }

        val listing = topLevel(root)
        if (listing.isNotBlank()) sb.append("Top level: $listing\n")

        if (openPath.isNotBlank()) {
            sb.append("\nOPEN FILE: $openPath\n")
            if (openContent.isNotBlank()) {
                val snippet = openContent.take(6000)
                sb.append("```\n").append(snippet)
                if (openContent.length > snippet.length) sb.append("\n… (truncated, use read_file for the rest)")
                sb.append("\n```\n")
            }
        } else {
            sb.append("\nNo file is open in the editor yet.\n")
        }

        sb.append(
            """

            WORKING STYLE
            - Start by reading the relevant files; never invent APIs or file contents.
            - Plan briefly, then execute. Make the smallest correct change.
            - After editing, verify: read the file back, run it, or check the browser console.
            - Only claim something works if a tool result or your own reasoning supports it. Say what failed.
            - Finish with a short summary of what changed and anything the user must do. No giant code dumps.
            - Keep the user's language and match the project's existing conventions.
            """.trimIndent()
        )
        return sb.toString()
    }

    private fun topLevel(root: File): String = try {
        root.listFiles()
            ?.filterNot { it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.take(40)
            ?.joinToString(", ") { if (it.isDirectory) "${it.name}/" else it.name }
            ?: ""
    } catch (_: Exception) {
        ""
    }
}
