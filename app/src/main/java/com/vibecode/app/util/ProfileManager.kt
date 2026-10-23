package com.vibecode.app.util

import org.json.JSONArray
import org.json.JSONObject

data class ApiProfile(
    val name: String,
    val provider: String,
    val model: String,
    val baseUrl: String,
    val apiKey: String,
    val claudeKey: String,
    val geminiKey: String,
    val openaiKey: String
)

object ProfileManager {
    fun list(): List<ApiProfile> {
        val arr = JSONArray(Prefs.profilesJson())
        val out = mutableListOf<ApiProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(ApiProfile(
                name = o.optString("name"),
                provider = o.optString("provider", "gemini"),
                model = o.optString("model", ""),
                baseUrl = o.optString("baseUrl", ""),
                apiKey = o.optString("apiKey", ""),
                claudeKey = o.optString("claudeKey", ""),
                geminiKey = o.optString("geminiKey", ""),
                openaiKey = o.optString("openaiKey", "")
            ))
        }
        return out
    }

    fun save(profile: ApiProfile) {
        val arr = JSONArray(Prefs.profilesJson())
        // replace if same name
        var found = false
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("name") == profile.name) {
                arr.put(i, toJson(profile)); found = true; break
            }
        }
        if (!found) arr.put(toJson(profile))
        Prefs.setProfilesJson(arr.toString())
    }

    fun delete(name: String) {
        val arr = JSONArray(Prefs.profilesJson())
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("name") != name) out.put(arr.getJSONObject(i))
        }
        Prefs.setProfilesJson(out.toString())
        if (Prefs.activeProfile() == name) Prefs.setActiveProfile("")
    }

    fun get(name: String): ApiProfile? = list().firstOrNull { it.name == name }

    private fun toJson(p: ApiProfile): JSONObject = JSONObject().apply {
        put("name", p.name); put("provider", p.provider); put("model", p.model)
        put("baseUrl", p.baseUrl); put("apiKey", p.apiKey)
        put("claudeKey", p.claudeKey); put("geminiKey", p.geminiKey); put("openaiKey", p.openaiKey)
    }
}