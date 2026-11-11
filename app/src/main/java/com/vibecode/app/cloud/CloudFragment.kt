package com.vibecode.app.cloud

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.vibecode.app.MainActivity
import com.vibecode.app.databinding.FragmentCloudBinding
import com.vibecode.app.util.Prefs

/**
 * Cloud pane: manage SSH workspace profiles and drive a remote pty shell.
 * Connecting also publishes an exec bridge so the agent can work on the remote.
 */
class CloudFragment : Fragment() {

    private companion object {
        const val SENTINEL = " "
        const val AUTH_PASSWORD = "PASSWORD"
        const val AUTH_KEY = "PRIVATE KEY"
    }

    private var _binding: FragmentCloudBinding? = null
    private val binding get() = _binding!!

    private val profiles = mutableListOf<CloudProfile>()
    private var session: CloudSession? = null
    private var ctrlPending = false
    private var suppressInput = false
    private var connected = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCloudBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        profiles.clear()
        profiles.addAll(CloudStore.list(Prefs.cloudProfilesJson()))
        if (profiles.isEmpty()) profiles.add(CloudProfile())

        binding.spinAuth.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, arrayOf(AUTH_PASSWORD, AUTH_KEY)
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        refreshProfileSpinner()
        loadProfileIntoForm(profiles.firstOrNull())

        binding.btnSaveProfile.setOnClickListener { saveProfile() }
        binding.btnDeleteProfile.setOnClickListener { deleteProfile() }
        binding.btnConnect.setOnClickListener { connect() }
        binding.btnDisconnect.setOnClickListener { disconnect() }

        binding.spinCloudProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadProfileIntoForm(profiles.getOrNull(position))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }

        binding.cloudTerminal.onResize = { cols, rows -> session?.resize(cols, rows) }
        binding.cloudTerminal.setOnClickListener { focusInput() }
        bindKeys()
        setupInput()
        showDisconnected("OFFLINE")
    }

    // ------------------------------------------------------------------ form

    private fun refreshProfileSpinner() {
        val labels = profiles.map { it.label }
        binding.spinCloudProfile.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, labels
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun loadProfileIntoForm(profile: CloudProfile?) {
        if (profile == null) return
        binding.etCloudName.setText(profile.name)
        binding.etHost.setText(profile.host)
        binding.etPort.setText(if (profile.port > 0) profile.port.toString() else "22")
        binding.etUser.setText(profile.user)
        binding.etPassword.setText(profile.password)
        binding.etPrivateKey.setText(profile.privateKey)
        binding.etPassphrase.setText(profile.passphrase)
        binding.spinAuth.setSelection(if (profile.privateKey.isNotBlank()) 1 else 0)
    }

    private fun formProfile(): CloudProfile {
        val name = binding.etCloudName.text?.toString()?.trim().orEmpty()
        val host = binding.etHost.text?.toString()?.trim().orEmpty()
        val port = binding.etPort.text?.toString()?.trim()?.toIntOrNull() ?: 22
        val user = binding.etUser.text?.toString()?.trim().orEmpty()
        val keyMode = binding.spinAuth.selectedItemPosition == 1
        return CloudProfile(
            name = name,
            host = host,
            port = port,
            user = user,
            password = if (keyMode) "" else binding.etPassword.text?.toString().orEmpty(),
            privateKey = if (keyMode) binding.etPrivateKey.text?.toString().orEmpty().trim() else "",
            passphrase = if (keyMode) binding.etPassphrase.text?.toString().orEmpty() else ""
        )
    }

    private fun saveProfile() {
        val profile = formProfile()
        if (profile.host.isBlank() || profile.user.isBlank()) {
            toast("host and user are required")
            return
        }
        CloudStore.upsert(profiles, profile)
        Prefs.setCloudProfilesJson(CloudStore.encode(profiles))
        Prefs.setLastCloudProfile(profile.label)
        refreshProfileSpinner()
        toast("saved ${profile.label}")
    }

    private fun deleteProfile() {
        val index = binding.spinCloudProfile.selectedItemPosition
        if (index !in profiles.indices) return
        val removed = profiles.removeAt(index)
        if (profiles.isEmpty()) profiles.add(CloudProfile())
        Prefs.setCloudProfilesJson(CloudStore.encode(profiles))
        refreshProfileSpinner()
        loadProfileIntoForm(profiles.firstOrNull())
        toast("deleted ${removed.label}")
    }

    // --------------------------------------------------------------- connect

    private fun connect() {
        val profile = formProfile()
        if (!profile.isComplete()) {
            toast("host, user and a password or key are required")
            return
        }
        saveProfile()
        binding.tvCloudStatus.text = "CONNECTING ${profile.endpoint}"
        binding.btnConnect.isEnabled = false
        // The listener needs a handle on the session it belongs to; the handle is
        // assigned right after construction, before any callback can fire.
        var handle: CloudSession? = null
        val newSession = CloudSession(object : CloudSession.Listener {
            override fun onOutput(text: String) = post { binding.cloudTerminal.feed(text) }

            override fun onConnected(banner: String) = post {
                connected = true
                binding.btnConnect.isEnabled = true
                binding.tvCloudStatus.text = "LINKED $banner"
                binding.tvCloudStatus.setTextColor(themeColor(com.vibecode.app.R.attr.dexAccent, 0xFFFF5A1F.toInt()))
                binding.connectPanel.visibility = View.GONE
                binding.sessionPanel.visibility = View.VISIBLE
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.cloudTerminal.feed("\u001b[?25h")
                CloudBridge.attach(banner) { command ->
                    handle?.exec(command) ?: "(cloud workspace is not connected)"
                }
                (activity as? MainActivity)?.setContextLabel("cloud · $banner")
                focusInput()
            }

            override fun onDisconnected(reason: String?) = post {
                connected = false
                CloudBridge.detach()
                binding.btnConnect.isEnabled = true
                binding.btnDisconnect.visibility = View.GONE
                showDisconnected(reason?.let { "LOST: ${it.take(60)}" } ?: "OFFLINE")
            }
        })
        handle = newSession
        session = newSession
        newSession.connect(profile, 100, 30)
    }

    private fun disconnect() {
        session?.disconnect()
        session = null
        connected = false
        CloudBridge.detach()
        showDisconnected("OFFLINE")
        (activity as? MainActivity)?.setContextLabel("")
    }

    private fun showDisconnected(status: String) {
        binding.sessionPanel.visibility = View.GONE
        binding.connectPanel.visibility = View.VISIBLE
        binding.btnDisconnect.visibility = View.GONE
        binding.tvCloudStatus.text = status
        binding.tvCloudStatus.setTextColor(themeColor(com.vibecode.app.R.attr.dexMuted, 0xFF8B7C92.toInt()))
    }

    // ----------------------------------------------------------------- input

    private fun setupInput() {
        binding.cloudInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(s: Editable?) {
                if (suppressInput) return
                val raw = s?.toString().orEmpty()
                suppressInput = true
                binding.cloudInput.setText(SENTINEL)
                binding.cloudInput.setSelection(SENTINEL.length)
                suppressInput = false
                if (raw.isEmpty()) { typeKey("\u007f"); return }
                val typed = if (raw.startsWith(SENTINEL)) raw.substring(1) else raw
                for (ch in typed) handleChar(ch)
            }
        })
        suppressInput = true
        binding.cloudInput.setText(SENTINEL)
        binding.cloudInput.setSelection(SENTINEL.length)
        suppressInput = false

        binding.cloudInput.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> { typeKey("\r"); true }
                KeyEvent.KEYCODE_DPAD_UP -> { typeKey("\u001b[A"); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { typeKey("\u001b[B"); true }
                else -> false
            }
        }
    }

    private fun handleChar(ch: Char) {
        if (ctrlPending) {
            setCtrl(false)
            val code = when (ch) {
                in 'a'..'z' -> ch - 'a' + 1
                in 'A'..'Z' -> ch - 'A' + 1
                else -> ch.code and 0x1f
            }
            typeKey(code.toChar().toString())
            return
        }
        when (ch) {
            '\n', '\r' -> typeKey("\r")
            '\u007f', '\b' -> typeKey("\u007f")
            '\t' -> typeKey("\t")
            else -> if (ch >= ' ') typeKey(ch.toString())
        }
    }

    private fun typeKey(text: String) {
        if (!connected) return
        session?.send(text)
    }

    private fun bindKeys() {
        binding.ckEsc.setOnClickListener { typeKey("\u001b") }
        binding.ckTab.setOnClickListener { typeKey("\t") }
        binding.ckCtrl.setOnClickListener { setCtrl(!ctrlPending) }
        binding.ckInt.setOnClickListener { typeKey("\u0003") }
        binding.ckUp.setOnClickListener { typeKey("\u001b[A") }
        binding.ckDown.setOnClickListener { typeKey("\u001b[B") }
        binding.ckLeft.setOnClickListener { typeKey("\u001b[D") }
        binding.ckRight.setOnClickListener { typeKey("\u001b[C") }
        binding.ckPipe.setOnClickListener { typeKey("|") }
        binding.ckSlash.setOnClickListener { typeKey("/") }
        binding.ckDash.setOnClickListener { typeKey("-") }
        binding.ckTilde.setOnClickListener { typeKey("~") }
        binding.cloudTerminal.setOnLongClickListener { focusInput(); true }
    }

    private fun setCtrl(on: Boolean) {
        ctrlPending = on
        binding.ckCtrl.setBackgroundResource(
            if (on) com.vibecode.app.R.drawable.bg_key_accent else com.vibecode.app.R.drawable.bg_key
        )
        binding.ckCtrl.setTextColor(
            if (on) themeColor(com.vibecode.app.R.attr.dexBg, 0xFF07050A.toInt())
            else themeColor(com.vibecode.app.R.attr.dexText, 0xFFF3EADF.toInt())
        )
    }

    private fun focusInput() {
        binding.cloudInput.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.cloudInput, InputMethodManager.SHOW_IMPLICIT)
    }

    // --------------------------------------------------------------- helpers

    private fun post(block: () -> Unit) {
        val view = _binding?.root ?: return
        view.post { if (_binding != null) block() }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun themeColor(attr: Int, fallback: Int): Int {
        val ta = requireContext().theme.obtainStyledAttributes(intArrayOf(attr))
        return try { ta.getColor(0, fallback) } finally { ta.recycle() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        session?.disconnect()
        session = null
        CloudBridge.detach()
        _binding = null
    }
}
