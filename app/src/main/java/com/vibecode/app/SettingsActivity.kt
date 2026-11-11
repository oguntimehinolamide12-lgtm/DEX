package com.vibecode.app

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vibecode.app.ai.AiProvider
import com.vibecode.app.ai.AnthropicProvider
import com.vibecode.app.ai.ChatMessage
import com.vibecode.app.ai.GeminiProvider
import com.vibecode.app.ai.OpenAiProvider
import com.vibecode.app.browser.BrowserFragment
import com.vibecode.app.databinding.ActivitySettingsBinding
import com.vibecode.app.util.ApiProfile
import com.vibecode.app.util.Prefs
import com.vibecode.app.util.ProfileManager

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private val providers = arrayOf("openai", "anthropic", "gemini", "openai-compatible")
    private val themeKeys = arrayOf("ember", "acid", "paper")
    private val themeLabels = arrayOf("EMBER · charred obsidian", "ACID · high contrast", "PAPER · ink on fibre")
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
        setTheme(themeRes())
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindSpinners()
        refreshProfileSpinner()
        loadValues()
        wireActions()
    }

    private fun themeRes(): Int = when (Prefs.theme()) {
        "acid" -> R.style.Theme_Dex_Acid
        "paper" -> R.style.Theme_Dex_Paper
        else -> R.style.Theme_Dex
    }

    private fun bindSpinners() {
        binding.spinProvider.adapter = spinnerAdapter(providers.toList())
        binding.spinProvider.setSelection(providers.indexOf(Prefs.provider()).coerceAtLeast(0))
        binding.spinTheme.adapter = spinnerAdapter(themeLabels.toList())
        binding.spinTheme.setSelection(themeKeys.indexOf(Prefs.theme()).coerceAtLeast(0))
        binding.spinFont.adapter = spinnerAdapter(fonts.toList())
        binding.spinFont.setSelection(fonts.indexOf(Prefs.fontFamily()).coerceAtLeast(0))
        binding.spinPreset.adapter = spinnerAdapter(presets.map { it.name })
    }

    private fun spinnerAdapter(items: List<String>) =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun loadValues() {
        binding.etModel.setText(Prefs.model())
        binding.etBaseUrl.setText(Prefs.baseUrl())
        binding.etOpenAi.setText(Prefs.apiKey("openai"))
        binding.etAnthropic.setText(Prefs.apiKey("anthropic"))
        binding.etGemini.setText(Prefs.apiKey("gemini"))
        binding.etCustom.setText(Prefs.apiKey("openai-compatible"))
        binding.etFontSize.setText(Prefs.fontSize().toString())
        binding.etTabSize.setText(Prefs.tabSize().toString())
        binding.etBrowserHome.setText(Prefs.browserHome())
        binding.etAgentSteps.setText(Prefs.agentSteps().toString())
        binding.cbFileAccess.isChecked = Prefs.fileAccess()
        updateBaseUrlVisibility()
    }

    private fun wireActions() {
        binding.spinProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateBaseUrlVisibility()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }

        binding.spinPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preset = presets.getOrNull(position) ?: return
                binding.spinProvider.setSelection(providers.indexOf(preset.provider).coerceAtLeast(0))
                if (preset.base.isNotBlank()) binding.etBaseUrl.setText(preset.base)
                if (preset.model.isNotBlank()) binding.etModel.setText(preset.model)
                updateBaseUrlVisibility()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }

        binding.btnProfileLoad.setOnClickListener {
            val name = binding.spinProfile.selectedItem?.toString() ?: return@setOnClickListener
            loadProfile(name)
        }

        binding.btnProfileSave.setOnClickListener {
            val name = binding.etProfileName.text?.toString()?.trim().orEmpty()
                .ifBlank { "Profile ${ProfileManager.list().size + 1}" }
            val provider = providers[binding.spinProvider.selectedItemPosition]
            val profile = ApiProfile(
                name = name,
                provider = provider,
                model = binding.etModel.text?.toString()?.trim().orEmpty(),
                baseUrl = binding.etBaseUrl.text?.toString()?.trim().orEmpty(),
                apiKey = keyFor(provider),
                claudeKey = binding.etAnthropic.text?.toString()?.trim().orEmpty(),
                geminiKey = binding.etGemini.text?.toString()?.trim().orEmpty(),
                openaiKey = binding.etOpenAi.text?.toString()?.trim().orEmpty()
            )
            ProfileManager.save(profile)
            Prefs.setActiveProfile(name)
            persist(profile)
            refreshProfileSpinner()
            Toast.makeText(this, "saved $name", Toast.LENGTH_SHORT).show()
        }

        binding.btnProfileDelete.setOnClickListener {
            val name = binding.spinProfile.selectedItem?.toString() ?: return@setOnClickListener
            ProfileManager.delete(name)
            refreshProfileSpinner()
            Toast.makeText(this, "deleted $name", Toast.LENGTH_SHORT).show()
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnTest.setOnClickListener { testConnection() }
    }

    private fun keyFor(provider: String): String = when (provider) {
        "anthropic" -> binding.etAnthropic.text?.toString()?.trim().orEmpty()
        "gemini" -> binding.etGemini.text?.toString()?.trim().orEmpty()
        "openai-compatible" -> binding.etCustom.text?.toString()?.trim().orEmpty()
        else -> binding.etOpenAi.text?.toString()?.trim().orEmpty()
    }

    private fun persist(profile: ApiProfile) {
        Prefs.setProvider(profile.provider)
        Prefs.setModel(profile.model)
        Prefs.setBaseUrl(profile.baseUrl)
        Prefs.setApiKey("openai", profile.openaiKey)
        Prefs.setApiKey("anthropic", profile.claudeKey)
        Prefs.setApiKey("gemini", profile.geminiKey)
        Prefs.setApiKey("openai-compatible", profile.apiKey)
    }

    private fun save() {
        val provider = providers[binding.spinProvider.selectedItemPosition]
        Prefs.setProvider(provider)
        Prefs.setModel(binding.etModel.text?.toString()?.trim().orEmpty())
        Prefs.setBaseUrl(binding.etBaseUrl.text?.toString()?.trim().orEmpty())
        Prefs.setApiKey("openai", binding.etOpenAi.text?.toString()?.trim().orEmpty())
        Prefs.setApiKey("anthropic", binding.etAnthropic.text?.toString()?.trim().orEmpty())
        Prefs.setApiKey("gemini", binding.etGemini.text?.toString()?.trim().orEmpty())
        Prefs.setApiKey("openai-compatible", binding.etCustom.text?.toString()?.trim().orEmpty())
        binding.etFontSize.text?.toString()?.toIntOrNull()?.let { Prefs.setFontSize(it.coerceIn(8, 32)) }
        binding.etTabSize.text?.toString()?.toIntOrNull()?.let { Prefs.setTabSize(it) }
        Prefs.setFileAccess(binding.cbFileAccess.isChecked)
        Prefs.setTheme(themeKeys[binding.spinTheme.selectedItemPosition])
        Prefs.setFontFamily(fonts[binding.spinFont.selectedItemPosition])
        Prefs.setBrowserHome(
            binding.etBrowserHome.text?.toString()?.trim().orEmpty()
                .ifBlank { BrowserFragment.DEFAULT_HOME }
        )
        binding.etAgentSteps.text?.toString()?.toIntOrNull()?.let { Prefs.setAgentSteps(it) }
        Toast.makeText(this, "saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun refreshProfileSpinner() {
        val names = ProfileManager.list().map { it.name }.ifEmpty { listOf("(no profiles)") }
        binding.spinProfile.adapter = spinnerAdapter(names)
        val active = Prefs.activeProfile()
        if (active.isNotBlank()) {
            val index = names.indexOf(active)
            if (index >= 0) binding.spinProfile.setSelection(index)
        }
    }

    private fun loadProfile(name: String) {
        val profile = ProfileManager.get(name) ?: return
        Prefs.setActiveProfile(name)
        binding.spinProvider.setSelection(providers.indexOf(profile.provider).coerceAtLeast(0))
        binding.etModel.setText(profile.model)
        binding.etBaseUrl.setText(profile.baseUrl)
        binding.etOpenAi.setText(profile.openaiKey)
        binding.etAnthropic.setText(profile.claudeKey)
        binding.etGemini.setText(profile.geminiKey)
        binding.etCustom.setText(profile.apiKey)
        persist(profile)
        updateBaseUrlVisibility()
    }

    private fun updateBaseUrlVisibility() {
        val provider = providers.getOrNull(binding.spinProvider.selectedItemPosition) ?: providers[0]
        binding.tilBaseUrl.visibility = View.VISIBLE
        binding.etBaseUrl.hint = if (provider == "openai-compatible") {
            "http://10.0.2.2:11434/v1"
        } else {
            "https://api.openai.com/v1"
        }
    }

    private fun testConnection() {
        val provider = providers[binding.spinProvider.selectedItemPosition]
        val key = keyFor(provider)
        val model = binding.etModel.text?.toString()?.trim().orEmpty().ifBlank {
            when (provider) {
                "anthropic" -> "claude-3-5-haiku-latest"
                "gemini" -> "gemini-1.5-flash"
                "openai-compatible" -> "llama3.1"
                else -> "gpt-4o-mini"
            }
        }
        val base = binding.etBaseUrl.text?.toString()?.trim().orEmpty()
        Toast.makeText(this, "testing $provider…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val impl: AiProvider = when (provider) {
                    "anthropic" -> AnthropicProvider()
                    "gemini" -> GeminiProvider()
                    "openai-compatible" -> OpenAiProvider(base.ifBlank { "http://10.0.2.2:11434/v1" })
                    else -> OpenAiProvider(base.ifBlank { "https://api.openai.com/v1" })
                }
                var ok = false
                var err = ""
                val lock = Object()
                impl.chat(
                    key, model, listOf(ChatMessage("user", "ping")),
                    { ok = true },
                    { ok = true; synchronized(lock) { lock.notifyAll() } },
                    { e -> err = e; synchronized(lock) { lock.notifyAll() } }
                )
                synchronized(lock) { lock.wait(20000) }
                runOnUiThread {
                    if (ok) Toast.makeText(this, "connection ok", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(this, "failed: ${err.take(120)}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
