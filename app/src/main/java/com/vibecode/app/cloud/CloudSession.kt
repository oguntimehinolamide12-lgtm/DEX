package com.vibecode.app.cloud

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * SSH workspace session: a PTY shell for the terminal pane and exec channels for
 * the agent. No Android dependencies — this class is exercised by unit tests
 * against an embedded SSH server.
 */
class CloudSession(private val listener: Listener) {

    interface Listener {
        fun onOutput(text: String)
        fun onConnected(banner: String)
        fun onDisconnected(reason: String?)
    }

    private var jsch: JSch? = null
    private var session: Session? = null
    private var shell: ChannelShell? = null

    @Volatile private var writer: OutputStream? = null

    @Volatile private var connected = false

    @Volatile private var cols = 100

    @Volatile private var rows = 30

    fun isConnected(): Boolean = connected && session?.isConnected == true

    /**
     * Connects and opens an interactive shell with a real pty, so curses apps
     * (vim, top, htop) render properly on the remote host.
     */
    fun connect(profile: CloudProfile, cols: Int, rows: Int) {
        disconnect()
        this.cols = cols
        this.rows = rows
        Thread({
            var reason: String? = null
            try {
                val js = JSch()
                val keyMode = profile.privateKey.isNotBlank()
                if (keyMode) {
                    val key = if (profile.privateKey.endsWith("\n")) profile.privateKey else profile.privateKey + "\n"
                    val passphrase = profile.passphrase.toByteArray(StandardCharsets.UTF_8)
                    js.addIdentity("dex-key", key.toByteArray(StandardCharsets.UTF_8), null, passphrase)
                }
                jsch = js
                val s = js.getSession(profile.user, profile.host, if (profile.port > 0) profile.port else 22)
                // This is a developer tool: host keys are accepted on first use.
                s.setConfig("StrictHostKeyChecking", "no")
                s.setConfig(
                    "PreferredAuthentications",
                    if (keyMode) "publickey,password,keyboard-interactive" else "password,keyboard-interactive"
                )
                if (profile.password.isNotBlank()) s.setPassword(profile.password)
                s.setServerAliveInterval(20000)
                s.setServerAliveCountMax(3)
                s.connect(15000)
                session = s

                val channel = s.openChannel("shell") as ChannelShell
                channel.setPtyType("xterm-256color")
                channel.setPtySize(cols, rows, 0, 0)
                channel.setEnv("LANG", "C.UTF-8")
                channel.setEnv("TERM", "xterm-256color")
                val input = channel.inputStream
                val output = channel.outputStream
                channel.connect(15000)
                shell = channel
                writer = output
                connected = true
                listener.onConnected("${profile.user}@${profile.host}")

                val reader = InputStreamReader(input, StandardCharsets.UTF_8)
                val buffer = CharArray(4096)
                while (true) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    if (n > 0) listener.onOutput(String(buffer, 0, n))
                }
            } catch (t: Throwable) {
                reason = t.message ?: t.javaClass.simpleName
            } finally {
                val wasConnected = connected
                connected = false
                writer = null
                try { shell?.disconnect() } catch (_: Exception) { }
                try { session?.disconnect() } catch (_: Exception) { }
                shell = null
                session = null
                if (wasConnected || reason != null) listener.onDisconnected(reason)
            }
        }, "dex-cloud").start()
    }

    /** Raw keystrokes for the remote pty (it echoes them back). */
    fun send(text: String): Boolean {
        val out = writer ?: return false
        return try {
            out.write(text.toByteArray(StandardCharsets.UTF_8))
            out.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        cols = newCols
        rows = newRows
        try { shell?.setPtySize(newCols, newRows, 0, 0) } catch (_: Exception) { }
    }

    /** Runs one command over a fresh exec channel and returns merged output. */
    fun exec(command: String, timeoutMs: Int = 120000): String {
        val s = session ?: return "(cloud workspace is not connected)"
        if (!s.isConnected) return "(cloud workspace is not connected)"
        var channel: ChannelExec? = null
        return try {
            val ch = s.openChannel("exec") as ChannelExec
            channel = ch
            ch.setCommand(command)
            val err = ByteArrayOutputStream()
            ch.setErrStream(err)
            ch.connect(12000)
            val out = ByteArrayOutputStream()
            val pump = Thread {
                try { ch.inputStream.use { it.copyTo(out) } } catch (_: Exception) { }
            }
            pump.start()
            pump.join(timeoutMs.toLong())
            if (pump.isAlive) {
                ch.disconnect()
                return "remote command timed out after ${timeoutMs}ms\n" + out.toString("UTF-8")
            }
            val text = out.toString("UTF-8") + err.toString("UTF-8")
            text.ifBlank { "(no output)" }
        } catch (t: Throwable) {
            "remote command failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            try { channel?.disconnect() } catch (_: Exception) { }
        }
    }

    fun disconnect() {
        val wasConnected = connected
        connected = false
        writer = null
        try { shell?.disconnect() } catch (_: Exception) { }
        try { session?.disconnect() } catch (_: Exception) { }
        shell = null
        session = null
        jsch = null
        if (wasConnected) listener.onDisconnected(null)
    }
}
