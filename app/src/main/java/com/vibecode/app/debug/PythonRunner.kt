package com.vibecode.app.debug

object PythonRunner {
    fun run(code: String): String {
        return try {
            val py = com.chaquo.python.Python.getInstance()
            val mod = py.getModule("runner")
            mod.callAttr("run", code).toString()
        } catch (e: Exception) {
            "Python error: ${e.message}"
        }
    }
    fun isAvailable(): Boolean = try {
        com.chaquo.python.Python.getInstance(); true
    } catch (_: Exception) { false }
}