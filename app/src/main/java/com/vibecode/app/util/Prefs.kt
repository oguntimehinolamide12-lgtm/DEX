package com.vibecode.app.util

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) { sp = ctx.getSharedPreferences("vibecode", Context.MODE_PRIVATE) }

    fun provider() = sp.getString("provider", "openai") ?: "openai"
    fun setProvider(v: String) = sp.edit().putString("provider", v).apply()
    fun model() = sp.getString("model", "") ?: ""
    fun setModel(v: String) = sp.edit().putString("model", v).apply()
    fun baseUrl() = sp.getString("baseUrl", "") ?: ""
    fun setBaseUrl(v: String) = sp.edit().putString("baseUrl", v).apply()
    fun apiKey(p: String) = sp.getString("key_$p", "") ?: ""
    fun setApiKey(p: String, v: String) = sp.edit().putString("key_$p", v).apply()
    fun fontSize() = sp.getInt("fontSize", 14)
    fun setFontSize(v: Int) = sp.edit().putInt("fontSize", v).apply()
    fun tabSize() = sp.getInt("tabSize", 4).coerceIn(1, 8)
    fun setTabSize(v: Int) = sp.edit().putInt("tabSize", v).apply()
    fun lastFolder() = sp.getString("lastFolder", "") ?: ""
    fun setLastFolder(v: String) = sp.edit().putString("lastFolder", v).apply()
    fun recentFiles(): List<String> = (sp.getString("recentFiles", "") ?: "").split("|").filter { it.isNotBlank() }

    fun addRecentFile(path: String) {
        val list = recentFiles().toMutableList()
        list.remove(path)
        list.add(0, path)
        sp.edit().putString("recentFiles", list.take(10).joinToString("|")).apply()
    }

    /** Skins: "ember" (default), "acid", "paper". Legacy values are mapped. */
    fun theme(): String {
        val stored = sp.getString("theme", "ember") ?: "ember"
        return when (stored) {
            "dark", "monokai" -> if (stored == "monokai") "acid" else "ember"
            "light" -> "paper"
            "high_contrast" -> "acid"
            "ember", "acid", "paper" -> stored
            else -> "ember"
        }
    }

    fun setTheme(v: String) = sp.edit().putString("theme", v).apply()

    fun fontFamily() = sp.getString("fontFamily", "monospace") ?: "monospace"
    fun setFontFamily(v: String) = sp.edit().putString("fontFamily", v).apply()
    fun fileAccess() = sp.getBoolean("fileAccess", true)
    fun setFileAccess(v: Boolean) = sp.edit().putBoolean("fileAccess", v).apply()
    fun agentVisible() = sp.getBoolean("agentVisible", true)
    fun setAgentVisible(v: Boolean) = sp.edit().putBoolean("agentVisible", v).apply()
    fun profilesJson() = sp.getString("profilesJson", "[]") ?: "[]"
    fun setProfilesJson(v: String) = sp.edit().putString("profilesJson", v).apply()
    fun activeProfile() = sp.getString("activeProfile", "") ?: ""
    fun setActiveProfile(v: String) = sp.edit().putString("activeProfile", v).apply()

    /** Home page of the embedded browser. */
    fun browserHome() = sp.getString("browserHome", "https://developer.mozilla.org") ?: "https://developer.mozilla.org"
    fun setBrowserHome(v: String) = sp.edit().putString("browserHome", v).apply()

    /** How many tool round-trips a single agent request may take. */
    fun agentSteps() = sp.getInt("agentSteps", 12).coerceIn(1, 40)
    fun setAgentSteps(v: Int) = sp.edit().putInt("agentSteps", v.coerceIn(1, 40)).apply()

    /** Cloud (SSH) workspace profiles, serialized as JSON. */
    fun cloudProfilesJson() = sp.getString("cloudProfilesJson", "[]") ?: "[]"
    fun setCloudProfilesJson(v: String) = sp.edit().putString("cloudProfilesJson", v).apply()
    fun lastCloudProfile() = sp.getString("lastCloudProfile", "") ?: ""
    fun setLastCloudProfile(v: String) = sp.edit().putString("lastCloudProfile", v).apply()

    /** Which pane was visible last: "editor", "browser", "terminal" or "cloud". */
    fun lastPane() = sp.getString("lastPane", "editor") ?: "editor"
    fun setLastPane(v: String) = sp.edit().putString("lastPane", v).apply()
}
