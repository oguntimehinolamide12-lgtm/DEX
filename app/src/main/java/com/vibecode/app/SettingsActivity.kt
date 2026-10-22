package com.vibecode.app

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vibecode.app.databinding.ActivitySettingsBinding
import com.vibecode.app.util.Prefs

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private val providers = arrayOf("openai", "anthropic", "gemini", "openai-compatible")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Prefs.init(this)

        binding.spinProvider.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providers).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinProvider.setSelection(providers.indexOf(Prefs.provider()).coerceAtLeast(0))

        binding.etModel.setText(Prefs.model())
        binding.etBaseUrl.setText(Prefs.baseUrl())
        binding.etOpenAi.setText(Prefs.apiKey("openai"))
        binding.etAnthropic.setText(Prefs.apiKey("anthropic"))
        binding.etGemini.setText(Prefs.apiKey("gemini"))
        binding.etCustom.setText(Prefs.apiKey("openai-compatible"))
        binding.etFontSize.setText(Prefs.fontSize().toString())
        binding.etTabSize.setText(Prefs.tabSize().toString())

        updateBaseUrlVisibility()

        binding.spinProvider.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { updateBaseUrlVisibility() }
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
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnTest.setOnClickListener { testConnection() }
    }

    private fun updateBaseUrlVisibility() {
        val custom = binding.spinProvider.selectedItemPosition == providers.indexOf("openai-compatible")
        binding.tilBaseUrl.visibility = if (custom) View.VISIBLE else View.VISIBLE
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
