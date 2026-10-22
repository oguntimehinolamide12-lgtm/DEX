package com.vibecode.app.util

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "vibecode_prefs"
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    fun apiKey(provider: String): String = sp.getString("key_$provider", "") ?: ""
    fun setApiKey(provider: String, key: String) = sp.edit().putString("key_$provider", key).apply()

    fun provider(): String = sp.getString("provider", "openai") ?: "openai"
    fun setProvider(p: String) = sp.edit().putString("provider", p).apply()

    fun model(): String = sp.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
    fun setModel(m: String) = sp.edit().putString("model", m).apply()

    fun lastFolder(): String = sp.getString("last_folder", "") ?: ""
    fun setLastFolder(f: String) = sp.edit().putString("last_folder", f).apply()

    fun fontSize(): Int = sp.getInt("font_size", 13)
    fun setFontSize(s: Int) = sp.edit().putInt("font_size", s).apply()

    fun tabSize(): Int = sp.getInt("tab_size", 4)
    fun setTabSize(s: Int) = sp.edit().putInt("tab_size", s).apply()
}
