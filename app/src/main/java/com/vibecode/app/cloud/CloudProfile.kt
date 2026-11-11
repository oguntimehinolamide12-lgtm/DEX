package com.vibecode.app.cloud

/**
 * A remote workspace definition. Deliberately free of Android/JSON types so the
 * SSH engine and its tests stay JVM-friendly.
 */
data class CloudProfile(
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = ""
) {
    val label: String
        get() = when {
            name.isNotBlank() -> name
            host.isNotBlank() -> "$user@$host"
            else -> "new workspace"
        }

    val endpoint: String
        get() = "$user@$host:${if (port > 0) port else 22}"

    fun isComplete(): Boolean =
        host.isNotBlank() && user.isNotBlank() && (password.isNotBlank() || privateKey.isNotBlank())
}
