package com.vibecode.app.cloud

import org.json.JSONArray
import org.json.JSONObject

/** JSON persistence for cloud profiles. */
object CloudStore {

    fun list(json: String): List<CloudProfile> {
        val out = mutableListOf<CloudProfile>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    CloudProfile(
                        name = o.optString("name"),
                        host = o.optString("host"),
                        port = o.optInt("port", 22),
                        user = o.optString("user"),
                        password = o.optString("password"),
                        privateKey = o.optString("privateKey"),
                        passphrase = o.optString("passphrase")
                    )
                )
            }
        } catch (_: Exception) { }
        return out
    }

    fun encode(profiles: List<CloudProfile>): String {
        val arr = JSONArray()
        for (p in profiles) {
            arr.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("host", p.host)
                    put("port", p.port)
                    put("user", p.user)
                    put("password", p.password)
                    put("privateKey", p.privateKey)
                    put("passphrase", p.passphrase)
                }
            )
        }
        return arr.toString()
    }

    fun upsert(profiles: MutableList<CloudProfile>, profile: CloudProfile) {
        val index = profiles.indexOfFirst { it.name == profile.name && profile.name.isNotBlank() }
        if (index >= 0) profiles[index] = profile else profiles.add(profile)
    }
}
