package com.vibecode.app.linux

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter

/**
 * A long-lived proot shell. Commands are written to the guest shell's stdin; after
 * each command a marker is emitted so the UI knows the command finished and where
 * the working directory ended up.
 *
 * Android's JVM APIs expose no pty, so this session is line-oriented: pipes carry
 * stdin/stdout but there is no tty. Line tools, package managers, compilers and
 * scripting languages work; full-screen curses apps (vim, top) cannot draw.
 */
class TerminalSession(
    private val ctx: Context,
    private val onOutput: (String) -> Unit,
    private val onCommandDone: (cwd: String) -> Unit,
    private val onExit: (Int) -> Unit
) {
    companion object {
        private const val MARK_START = '\u0001'
        private const val MARK_END = '\u0002'
    }

    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var readerThread: Thread? = null

    @Volatile private var alive = false
    @Volatile var cwd: String = "/root"
        private set
    var lastError: String? = null
        private set

    fun isAlive(): Boolean = alive && process?.isAlive == true

    fun start(cols: Int, rows: Int): Boolean {
        stop()
        val proot = LinuxEnv.prootFile(ctx)
        val rootfs = LinuxEnv.rootfsDir(ctx)
        val shellOk = File(rootfs, "bin/sh").exists() ||
            File(rootfs, "bin/busybox").exists() ||
            File(rootfs, "bin/ash").exists() ||
            File(rootfs, "usr/bin/env").exists()
        val rootfsLooksPresent = shellOk || (File(rootfs, "etc").isDirectory && File(rootfs, "usr").isDirectory)
        val bundled = File(ctx.applicationInfo.nativeLibraryDir, "libproot.so")
        val prootBin = when {
            proot.isFile -> proot
            bundled.isFile -> bundled
            else -> null
        }
        if (prootBin == null || !rootfsLooksPresent) {
            lastError = "Linux environment is not installed."
            return false
        }
        val command = mutableListOf(prootBin.absolutePath, "--link2symlink", "-0", "-r", rootfs.absolutePath)
        for (host in listOf("/dev", "/proc", "/sys", "/system")) {
            if (File(host).isDirectory) {
                command.add("-b")
                command.add(host)
            }
        }
        try {
            val shared = android.os.Environment.getExternalStorageDirectory()
            if (shared.isDirectory) {
                command.add("-b")
                command.add(shared.absolutePath + ":/root/sdcard")
            }
        } catch (_: Exception) { }
        command.addAll(listOf(
            "-w", "/root",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            "COLUMNS=$cols",
            "LINES=$rows",
            "/bin/sh"
        ))
        return try {
            val builder = ProcessBuilder(command).redirectErrorStream(true)
            builder.environment().apply {
                this["PROOT_TMP_DIR"] = ctx.cacheDir.absolutePath
                this["PROOT_NO_SECCOMP"] = "1"
                this["HOME"] = ctx.filesDir.absolutePath
                this["LD_LIBRARY_PATH"] = LinuxEnv.libDir(ctx).absolutePath
            }
            val proc = builder.start()
            process = proc
            stdin = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8))
            alive = true
            lastError = null
            readerThread = Thread({ pump(proc) }, "dex-shell").apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            false
        }
    }

    private fun pump(proc: Process) {
        val reader = proc.inputStream.bufferedReader(Charsets.UTF_8)
        val chunk = CharArray(4096)
        val text = StringBuilder()
        val marker = StringBuilder()
        var inMarker = false
        try {
            while (true) {
                val n = reader.read(chunk)
                if (n < 0) break
                for (i in 0 until n) {
                    val c = chunk[i]
                    if (inMarker) {
                        if (c == MARK_END) {
                            inMarker = false
                            val dir = marker.toString().trim()
                            marker.setLength(0)
                            if (text.isNotEmpty()) { onOutput(text.toString()); text.setLength(0) }
                            if (dir.isNotEmpty()) {
                                cwd = dir
                                onCommandDone(dir)
                            }
                        } else {
                            marker.append(c)
                        }
                    } else if (c == MARK_START) {
                        inMarker = true
                    } else {
                        text.append(c)
                    }
                }
                if (text.isNotEmpty()) {
                    onOutput(text.toString())
                    text.setLength(0)
                }
            }
        } catch (_: Exception) {
            // process died: fall through to exit reporting
        } finally {
            alive = false
            val code = try { proc.exitValue() } catch (_: Exception) { -1 }
            onExit(code)
        }
    }

    /** Runs a command and appends the cwd marker the UI uses as "command finished". */
    fun send(command: String): Boolean {
        val writer = stdin ?: return false
        return try {
            writer.write(command)
            writer.write("\n")
            writer.write("printf '\\001%s\\002' \"\$PWD\"\n")
            writer.flush()
            true
        } catch (e: Exception) {
            lastError = e.message
            false
        }
    }

    /** Raw bytes for interactive programs (REPLs, prompts waiting on stdin). */
    fun sendRaw(text: String): Boolean {
        val writer = stdin ?: return false
        return try {
            writer.write(text)
            writer.flush()
            true
        } catch (e: Exception) {
            lastError = e.message
            false
        }
    }

    /**
     * Sends SIGINT to the foreground command without killing the shell.
     * Java cannot signal a process group here, so the process tree is walked
     * through /proc and everything below the guest shell is interrupted.
     */
    fun interrupt(): Boolean {
        val proc = process ?: return false
        val myPid = android.os.Process.myPid()
        val all = processes()
        val proot = all.firstOrNull { it.parent == myPid } ?: return false
        val shells = all.filter { it.parent == proot.pid }
        var killed = 0
        for (shell in shells) {
            for (kid in all.filter { it.parent == shell.pid }) {
                try { android.os.Process.sendSignal(kid.pid, 2); killed++ } catch (_: Exception) { }
            }
        }
        if (killed == 0 && !proc.isAlive) stop()
        return killed > 0
    }

    private data class ProcInfo(val pid: Int, val parent: Int, val name: String)

    private fun processes(): List<ProcInfo> {
        val out = mutableListOf<ProcInfo>()
        val procDir = File("/proc")
        val entries = procDir.list() ?: return out
        for (entry in entries) {
            val pid = entry.toIntOrNull() ?: continue
            val stat = try { File(procDir, "$entry/stat").readText() } catch (_: Exception) { continue }
            val close = stat.lastIndexOf(')')
            val open = stat.indexOf('(')
            if (close < 0 || open < 0) continue
            val name = stat.substring(open + 1, close)
            val rest = stat.substring(close + 1).trim().split(' ')
            val ppid = rest.getOrNull(1)?.toIntOrNull() ?: 0
            out.add(ProcInfo(pid, ppid, name))
        }
        return out
    }

    fun stop() {
        alive = false
        try { stdin?.close() } catch (_: Exception) { }
        stdin = null
        val proc = process
        process = null
        if (proc != null) {
            try { proc.destroy() } catch (_: Exception) { }
        }
        readerThread = null
    }
}
