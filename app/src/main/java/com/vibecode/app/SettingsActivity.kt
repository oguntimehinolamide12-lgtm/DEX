package com.vibecode.app

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vibecode.app.databinding.ActivitySettingsBinding
import com.vibecode.app.util.Prefs
import com.vibecode.app.util.ProfileManager
import com.vibecode.app.util.ApiProfile


class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private val providers = arrayOf("openai", "anthropic", "gemini", "openai-compatible")
    private val themes = arrayOf("dark", "light", "high_contrast", "monokai")
    private val fonts = arrayOf("monospace", "sans", "serif")

    data class Preset(val name: String, val provider: String, val base: String, val model: String)
    private val presets = listOf(
        Preset("OpenAI (GPT-4o-mini)", "openai", "https://api.openai.com/v1", "gpt-4o-mini"),
        Preset("Anthropic (Claude 3.5 Haiku)", "anthropic", "", "claude-3-5-haiku-latest"),
        Preset("Gemini (1.5 Flash)", "gemini", "", "gemini-1.5-flash"),
        Preset("Ollama (local)", "openai-compatible", "http://10.0.2.2:11434/v1", "llama3.1"),
        Preset("LM Studio (local)", "openai-compatible", "http://10.0.2.2:1234/v1", "local-model"),
        Preset("Groq (Llama 3.3)", "openai-compatible", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        Preset("OpenRouter", "openai-compatible", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
        Preset("Together AI", "openai-compatible", "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
        Preset("Custom endpoint", "openai-compatible", "", "")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val themeRes = when (Prefs.theme()) {
            "light" -> com.vibecode.app.R.style.Theme_VibeCode_Light
            "high_contrast" -> com.vibecode.app.R.style.Theme_VibeCode_HighContrast
            "monokai" -> com.vibecode.app.R.style.Theme_VibeCode_Monokai
            else -> com.vibecode.app.R.style.Theme_VibeCode
        }
        setTheme(themeRes)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.spinProvider.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providers).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinProvider.setSelection(providers.indexOf(Prefs.provider()).coerceAtLeast(0))

        binding.spinTheme.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinTheme.setSelection(themes.indexOf(Prefs.theme()).coerceAtLeast(0))
        binding.spinFont.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fonts).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinFont.setSelection(fonts.indexOf(Prefs.fontFamily()).coerceAtLeast(0))

        refreshProfileSpinner()

        binding.btnProfileLoad.setOnClickListener {
            val name = binding.spinProfile.selectedItem?.toString() ?: return@setOnClickListener
            loadProfile(name)
        }
        binding.btnProfileSave.setOnClickListener {
            val name = binding.etProfileName.text.toString().trim().ifBlank { "Profile ${ProfileManager.list().size + 1}" }
            val prof = ApiProfile(
                name = name,
                provider = providers[binding.spinProvider.selectedItemPosition],
                model = binding.etModel.text.toString().trim(),
                baseUrl = binding.etBaseUrl.text.toString().trim(),
                apiKey = when (providers[binding.spinProvider.selectedItemPosition]) {
                    "anthropic" -> binding.etAnthropic.text.toString().trim()
                    "gemini" -> binding.etGemini.text.toString().trim()
                    "openai-compatible" -> binding.etCustom.text.toString().trim()
                    else -> binding.etOpenAi.text.toString().trim()
                },
                claudeKey = binding.etAnthropic.text.toString().trim(),
                geminiKey = binding.etGemini.text.toString().trim(),
                openaiKey = binding.etOpenAi.text.toString().trim()
            )
            ProfileManager.save(prof)
            Prefs.setActiveProfile(name)
            refreshProfileSpinner()
            Toast.makeText(this, "Profile saved: $name", Toast.LENGTH_SHORT).show()
        }
        binding.btnProfileDelete.setOnClickListener {
            val name = binding.spinProfile.selectedItem?.toString() ?: return@setOnClickListener
            ProfileManager.delete(name)
            refreshProfileSpinner()
            Toast.makeText(this, "Deleted $name", Toast.LENGTH_SHORT).show()
        }

        binding.etModel.setText(Prefs.model())
        binding.etBaseUrl.setText(Prefs.baseUrl())
        binding.etOpenAi.setText(Prefs.apiKey("openai"))
        binding.etAnthropic.setText(Prefs.apiKey("anthropic"))
        binding.etGemini.setText(Prefs.apiKey("gemini"))
        binding.etCustom.setText(Prefs.apiKey("openai-compatible"))
        binding.etFontSize.setText(Prefs.fontSize().toString())
        binding.etTabSize.setText(Prefs.tabSize().toString())
        binding.cbFileAccess.isChecked = Prefs.fileAccess()

        updateBaseUrlVisibility()

        binding.spinProvider.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { updateBaseUrlVisibility() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        binding.spinPreset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, presets.map { it.name }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinPreset.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                val pr = presets[position]
                binding.spinProvider.setSelection(providers.indexOf(pr.provider).coerceAtLeast(0))
                if (pr.base.isNotBlank()) binding.etBaseUrl.setText(pr.base)
                if (pr.model.isNotBlank()) binding.etModel.setText(pr.model)
                updateBaseUrlVisibility()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        binding.btnSave.setOnClickListener {
            val p = providers[binding.spinProvider.selectedItemPosition]
            Prefs.setProvider(p)
            Prefs.setModel(binding.etModel.text.toString().trim())
            Prefs.setBaseUrl(binding.etBaseUrl.text.toString().trim())
            Prefs.setApiKey("openai", binding.etOpenAi.text.toString().trim())
            Prefs.setApiKey("anthropic", binding.etAnthropic.text.toString().trim())
            Prefs.setApiKey("gemini", binding.etGemini.text.toString().trim())
            Prefs.setApiKey("openai-compatible", binding.etCustom.text.toString().trim())
            binding.etFontSize.text.toString().toIntOrNull()?.let { Prefs.setFontSize(it) }
            binding.etTabSize.text.toString().toIntOrNull()?.let { Prefs.setTabSize(it) }
            Prefs.setFileAccess(binding.cbFileAccess.isChecked)
            Prefs.setTheme(themes[binding.spinTheme.selectedItemPosition])
            Prefs.setFontFamily(fonts[binding.spinFont.selectedItemPosition])
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnTest.setOnClickListener { testConnection() }

    private fun refreshProfileSpinner() {
        val names = ProfileManager.list().map { it.name }.ifEmpty { listOf("(no profiles)") }
        binding.spinProfile.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val active = Prefs.activeProfile()
        if (active.isNotBlank()) {
            val idx = names.indexOf(active)
            if (idx >= 0) binding.spinProfile.setSelection(idx)
        }
    }

    private fun loadProfile(name: String) {
        val p = ProfileManager.get(name) ?: return
        Prefs.setActiveProfile(name)
        binding.spinProvider.setSelection(providers.indexOf(p.provider).coerceAtLeast(0))
        binding.etModel.setText(p.model)
        binding.etBaseUrl.setText(p.baseUrl)
        binding.etOpenAi.setText(p.openaiKey)
        binding.etAnthropic.setText(p.claudeKey)
        binding.etGemini.setText(p.geminiKey)
        binding.etCustom.setText(p.apiKey)
        updateBaseUrlVisibility()
    }
    }

    private fun updateBaseUrlVisibility() {
        val custom = binding.spinProvider.selectedItemPosition == providers.indexOf("openai-compatible")
        binding.tilBaseUrl.visibility = View.VISIBLE
        binding.etBaseUrl.hint = if (custom) "http://10.0.2.2:11434/v1 (Ollama)" else "https://api.openai.com/v1 (optional override)"
    }

    private fun testConnection() {
        val p = providers[binding.spinProvider.selectedItemPosition]
        val key = when (p) {
            "anthropic" -> binding.etAnthropic.text.toString().trim()
            "gemini" -> binding.etGemini.text.toString().trim()
            "openai-compatible" -> binding.etCustom.text.toString().trim()
            else -> binding.etOpenAi.text.toString().trim()
        }
        val model = binding.etModel.text.toString().trim().ifBlank {
            when (p) { "anthropic" -> "claude-3-5-haiku-latest"; "gemini" -> "gemini-1.5-flash"; "openai-compatible" -> "llama3.1"; else -> "gpt-4o-mini" }
        }
        val base = binding.etBaseUrl.text.toString().trim()
        Toast.makeText(this, "Testing $p…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val provider: com.vibecode.app.ai.AiProvider = when (p) {
                    "anthropic" -> com.vibecode.app.ai.AnthropicProvider()
                    "gemini" -> com.vibecode.app.ai.GeminiProvider()
                    "openai-compatible" -> com.vibecode.app.ai.OpenAiProvider(base.ifBlank { "http://10.0.2.2:11434/v1" })
                    else -> com.vibecode.app.ai.OpenAiProvider(base.ifBlank { "https://api.openai.com/v1" })
                }
                var ok = false
                var err = ""
                val lock = Object()
                provider.chat(key, model, listOf(com.vibecode.app.ai.ChatMessage("user", "ping")), { ok = true }, { ok = true; synchronized(lock) { lock.notifyAll() } }, { e -> err = e; synchronized(lock) { lock.notifyAll() } })
                synchronized(lock) { lock.wait(20000) }
                runOnUiThread {
                    if (ok || err.isEmpty()) Toast.makeText(this, "Connection OK", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(this, "Failed: ${err.take(120)}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
