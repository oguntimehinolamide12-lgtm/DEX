package com.vibecode.app.cloud

/**
 * Singleton hand-off between the connected cloud workspace and the agent tools.
 * Kept free of Android types so tools can call it from a worker thread.
 */
object CloudBridge {

    @Volatile
    private var executor: ((String) -> String)? = null

    @Volatile
    private var label: String = ""

    fun attach(name: String, exec: (String) -> String) {
        label = name
        executor = exec
    }

    fun detach() {
        executor = null
        label = ""
    }

    fun isConnected(): Boolean = executor != null

    fun workspaceName(): String = label

    fun run(command: String): String {
        val exec = executor ?: return "Cloud workspace is not connected. Open the CL pane and connect first."
        return try {
            exec(command)
        } catch (t: Throwable) {
            "remote run failed: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}
