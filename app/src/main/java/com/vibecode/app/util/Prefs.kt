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


    fun theme() = sp.getString("theme", "dark") ?: "dark"
    fun setTheme(v: String) = sp.edit().putString("theme", v).apply()
    fun fontFamily() = sp.getString("fontFamily", "monospace") ?: "monospace"
    fun setFontFamily(v: String) = sp.edit().putString("fontFamily", v).apply()
    fun fileAccess() = sp.getBoolean("fileAccess", true)
    fun setFileAccess(v: Boolean) = sp.edit().putBoolean("fileAccess", v).apply()


}
