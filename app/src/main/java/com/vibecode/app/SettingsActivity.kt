package com.vibecode.app

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vibecode.app.databinding.ActivitySettingsBinding
import com.vibecode.app.util.Prefs

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Prefs.init(this)

        val providers = arrayOf("openai", "anthropic", "gemini")
        binding.spinProvider.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providers).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinProvider.setSelection(providers.indexOf(Prefs.provider()).coerceAtLeast(0))
        binding.etModel.setText(Prefs.model())
        binding.etOpenAi.setText(Prefs.apiKey("openai"))
        binding.etAnthropic.setText(Prefs.apiKey("anthropic"))
        binding.etGemini.setText(Prefs.apiKey("gemini"))
        binding.etFontSize.setText(Prefs.fontSize().toString())

        binding.btnSave.setOnClickListener {
            val p = providers[binding.spinProvider.selectedItemPosition]
            Prefs.setProvider(p)
            Prefs.setModel(binding.etModel.text.toString().trim())
            Prefs.setApiKey("openai", binding.etOpenAi.text.toString().trim())
            Prefs.setApiKey("anthropic", binding.etAnthropic.text.toString().trim())
            Prefs.setApiKey("gemini", binding.etGemini.text.toString().trim())
            binding.etFontSize.text.toString().toIntOrNull()?.let { Prefs.setFontSize(it) }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
