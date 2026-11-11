package com.vibecode.app.linux

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import com.vibecode.app.databinding.FragmentTerminalBinding
import com.vibecode.app.util.Prefs

/**
 * Terminal pane: owns the proot session, line editing, history and the mobile key row.
 */
class TerminalFragment : Fragment() {

    private companion object { const val SENTINEL = " " }

    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!

    private var session: TerminalSession? = null
    private var installed = false
    private var busy = false
    private var ctrlPending = false
    private var suppressInput = false
    private val line = StringBuilder()
    private val history = mutableListOf<String>()
    private var historyIndex = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.terminalView.onResize = { _, _ -> }
        binding.terminalView.setOnClickListener { focusInput() }

        binding.hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(s: Editable?) {
                if (suppressInput) return
                val raw = s?.toString().orEmpty()
                // Keep a sentinel character in the field so soft keyboards report
                // backspace as a deletion instead of swallowing the key.
                suppressInput = true
                binding.hiddenInput.setText(SENTINEL)
                binding.hiddenInput.setSelection(SENTINEL.length)
                suppressInput = false
                if (raw.isEmpty()) { backspace(); return }
                val typed = if (raw.startsWith(SENTINEL)) raw.substring(1) else raw
                for (ch in typed) handleChar(ch)
            }
        })

        suppressInput = true
        binding.hiddenInput.setText(SENTINEL)
        binding.hiddenInput.setSelection(SENTINEL.length)
        suppressInput = false

        binding.hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> { submit(); true }
                KeyEvent.KEYCODE_DPAD_UP -> { historyUp(); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { historyDown(); true }
                KeyEvent.KEYCODE_TAB -> { insertText("\t"); true }
                else -> false
            }
        }

        bindKeys()
        binding.btnInstall.setOnClickListener { install() }
        binding.btnNewShell.setOnClickListener { restartShell() }
        binding.btnClearTerm.setOnClickListener { binding.terminalView.clear(); showPrompt() }

        installed = LinuxEnv.isInstalled(requireContext())
        if (installed) startSession() else showInstallPanel()
    }

    // ------------------------------------------------------------- lifecycle

    private fun startSession() {
        val ctx = context?.applicationContext ?: return
        installed = true
        binding.installPanel.visibility = View.GONE
        binding.terminalView.visibility = View.VISIBLE
        val newSession = TerminalSession(
            ctx,
            onOutput = { text -> post { binding.terminalView.feed(text) } },
            onCommandDone = { cwd ->
                post {
                    busy = false
                    binding.tvCwd.text = cwd
                    binding.terminalView.feed("\u001b[?25h")
                    showPrompt()
                }
            },
            onExit = { code ->
                post {
                    busy = false
                    binding.terminalView.feed("\r\n\u001b[38;5;203msession closed ($code)\u001b[0m\r\n")
                    showPrompt()
                }
            }
        )
        session = newSession
        if (!newSession.start(80, 24)) {
            binding.terminalView.feed("\u001b[38;5;203m${newSession.lastError ?: "failed to start"}\u001b[0m\r\n")
            return
        }
        banner()
    }

    private fun restartSession() {
        session?.stop()
        session = null
        binding.terminalView.clear()
        line.setLength(0)
        busy = false
        startSession()
    }

    private fun banner() {
        val target = LinuxEnv.target()
        val arch = target?.alpineArch ?: "unknown"
        binding.terminalView.feed("\u001b[38;5;209mDEX LINUX\u001b[0m  alpine 3.21 · $arch · proot\n")
        binding.terminalView.feed("\u001b[38;5;244mno root, no Termux. apk add / git / gcc / python work here.\u001b[0m\n")
        binding.terminalView.feed("\u001b[38;5;244mline-oriented shell: full-screen TUIs need a tty and are not supported.\u001b[0m\n\n")
        binding.tvCwd.text = "/root"
        showPrompt()
    }

    // ---------------------------------------------------------------- prompt

    private fun showPrompt() {
        binding.terminalView.feed(promptText())
    }

    private fun promptText(): String {
        val cwd = binding.tvCwd.text?.toString().orEmpty().ifBlank { "/root" }
        val short = cwd.replace("/root", "~")
        return "\u001b[38;5;209m❯\u001b[0m \u001b[38;5;245m$short\u001b[0m \u001b[38;5;215m$\u001b[0m "
    }

    private fun redrawInput() {
        binding.terminalView.feed("\r\u001b[K")
        binding.terminalView.feed(promptText())
        binding.terminalView.feed(line.toString())
    }

    // ----------------------------------------------------------------- input

    private fun handleChar(ch: Char) {
        if (ctrlPending) {
            setCtrl(false)
            val code = when (ch) {
                in 'a'..'z' -> ch - 'a' + 1
                in 'A'..'Z' -> ch - 'A' + 1
                else -> ch.code and 0x1f
            }
            if (code == 3) { interrupt(); return }
            session?.sendRaw(code.toChar().toString())
            return
        }
        if (ch == '\u0003') { interrupt(); return }
        if (ch == '\n' || ch == '\r') { submit(); return }
        if (ch == '\u007f' || ch == '\b') { backspace(); return }
        if (ch < ' ') { sendRaw(ch.toString()); return }
        if (busy) return
        line.append(ch)
        binding.terminalView.feed(ch.toString())
    }

    private fun insertText(text: String) {
        if (busy) { session?.sendRaw(text); return }
        if (text == "\t") {
            // basic path completion against the guest filesystem
            complete()
            return
        }
        line.append(text)
        binding.terminalView.feed(text)
    }

    private fun complete() {
        val current = line.toString()
        val token = current.substringAfterLast(' ')
        val slash = token.lastIndexOf('/')
        val dirPart = if (slash >= 0) token.substring(0, slash + 1) else ""
        val prefix = token.substring(slash + 1)
        val baseDir = binding.tvCwd.text?.toString().orEmpty().ifBlank { "/root" }
        val listing = try {
            val guestPath = if (dirPart.startsWith("/")) dirPart else "$baseDir/$dirPart"
            java.io.File(LinuxEnv.rootfsDir(requireContext()), guestPath).listFiles()?.map { it.name } ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val matches = listing.filter { it.startsWith(prefix) }.sorted()
        when {
            matches.isEmpty() -> binding.terminalView.feed("\u0007")
            matches.size == 1 -> {
                val completed = dirPart + matches[0]
                val replacement = completed + if (completed.endsWith("/")) "" else ""
                line.setLength(0)
                line.append(current.substring(0, current.length - token.length)).append(replacement)
                redrawInput()
            }
        }
    }

    private fun backspace() {
        if (busy || line.isEmpty()) return
        line.setLength(line.length - 1)
        binding.terminalView.feed("\b \b")
    }

    private fun submit() {
        if (busy) return
        val command = line.toString()
        line.setLength(0)
        binding.terminalView.feed("\r\n")
        if (command.isBlank()) { showPrompt(); return }
        history.add(command)
        historyIndex = history.size
        busy = true
        val ok = session?.send(command) ?: false
        if (!ok) {
            busy = false
            binding.terminalView.feed("\u001b[38;5;203mshell is not running — press NEW\u001b[0m\r\n")
            showPrompt()
        }
    }

    private fun sendRaw(text: String) {
        if (session?.sendRaw(text) != true) binding.terminalView.feed("\u0007")
    }

    private fun interrupt() {
        val killed = session?.interrupt() ?: false
        binding.terminalView.feed("\u001b[?25h")
        binding.terminalView.feed("\r\n^C\r\n")
        busy = false
        if (!killed) session?.sendRaw("\u0004")
        showPrompt()
    }

    private fun historyUp() {
        if (history.isEmpty() || historyIndex == 0) return
        historyIndex--
        line.setLength(0)
        line.append(history[historyIndex])
        redrawInput()
    }

    private fun historyDown() {
        if (history.isEmpty()) return
        if (historyIndex < history.size - 1) {
            historyIndex++
            line.setLength(0)
            line.append(history[historyIndex])
        } else {
            historyIndex = history.size
            line.setLength(0)
        }
        redrawInput()
    }

    // ------------------------------------------------------------------ keys

    private fun bindKeys() {
        binding.keyEsc.setOnClickListener { sendRaw("\u001b") }
        binding.keyTab.setOnClickListener { insertText("\t") }
        binding.keyCtrl.setOnClickListener { toggleCtrl() }
        binding.keyInterrupt.setOnClickListener { interrupt() }

        binding.keyUp.setOnClickListener { historyUp() }
        binding.keyDown.setOnClickListener { historyDown() }
        binding.keyLeft.setOnClickListener { sendRaw("\u001b[D") }
        binding.keyRight.setOnClickListener { sendRaw("\u001b[C") }

        binding.keyPipe.setOnClickListener { insertText("|") }
        binding.keySlash.setOnClickListener { insertText("/") }
        binding.keyDash.setOnClickListener { insertText("-") }
        binding.keyTilde.setOnClickListener { insertText("~") }

        binding.terminalView.setOnLongClickListener {
            focusInput()
            true
        }
    }

    private fun toggleCtrl() = setCtrl(!ctrlPending)

    private fun setCtrl(on: Boolean) {
        ctrlPending = on
        binding.keyCtrl.setBackgroundResource(
            if (on) com.vibecode.app.R.drawable.bg_key_accent else com.vibecode.app.R.drawable.bg_key
        )
        binding.keyCtrl.setTextColor(
            if (on) themeColor(com.vibecode.app.R.attr.dexBg, 0xFF07050A.toInt())
            else themeColor(com.vibecode.app.R.attr.dexText, 0xFFF3EADF.toInt())
        )
    }

    private fun themeColor(attr: Int, fallback: Int): Int {
        val ta = requireContext().theme.obtainStyledAttributes(intArrayOf(attr))
        return try { ta.getColor(0, fallback) } finally { ta.recycle() }
    }

    /** Called from the toolbar menu. */
    fun restartShell() { restartSession() }

    private fun focusInput() {
        binding.hiddenInput.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.hiddenInput, InputMethodManager.SHOW_IMPLICIT)
    }

    // --------------------------------------------------------------- install

    private fun showInstallPanel() {
        binding.installPanel.visibility = View.VISIBLE
        binding.terminalView.visibility = View.GONE
        binding.tvCwd.text = "not installed"
        val target = LinuxEnv.target()
        binding.tvInstallBody.text = if (target == null) {
            "This device ABI is not supported by the bundled proot builds."
        } else {
            "Downloads a statically linked proot (${target.prootArch}) and an Alpine ${target.alpineArch} minirootfs into this app, then opens a real Linux shell.\n\nNo root. No Termux. No system install."
        }
    }

    private fun install() {
        val ctx = context?.applicationContext ?: return
        binding.btnInstall.isEnabled = false
        binding.installProgress.visibility = View.VISIBLE
        binding.tvInstallTitle.text = "provisioning…"
        binding.tvInstallBody.text = "Fetching proot and the Alpine rootfs."
        Thread {
            try {
                LinuxEnv.install(ctx) { status, pct ->
                    post {
                        binding.tvInstallTitle.text = status
                        binding.installProgress.progress = pct
                    }
                }
                post {
                    binding.installProgress.visibility = View.GONE
                    startSession()
                }
            } catch (e: Exception) {
                post {
                    binding.btnInstall.isEnabled = true
                    binding.installProgress.visibility = View.GONE
                    binding.tvInstallTitle.text = "install failed"
                    binding.tvInstallBody.text = (e.message ?: "unknown error") +
                        "\n\nCheck the connection and try again. Rootfs and proot are fetched from official mirrors."
                }
            }
        }.start()
    }

    private fun post(block: () -> Unit) {
        val view = _binding?.root ?: return
        view.post { if (_binding != null) block() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        session?.stop()
        session = null
        _binding = null
    }
}
