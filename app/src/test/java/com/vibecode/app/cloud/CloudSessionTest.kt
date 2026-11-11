package com.vibecode.app.cloud

import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Exercises the real SSH code path against an embedded server: password auth,
 * pty shell I/O and remote exec. No device or network needed.
 */
class CloudSessionTest {

    private class EchoShell : Command {
        private var input: InputStream? = null
        private var output: OutputStream? = null
        private var exit: ExitCallback? = null

        override fun setInputStream(input: InputStream) { this.input = input }
        override fun setOutputStream(output: OutputStream) { this.output = output }
        override fun setErrorStream(err: OutputStream) { }
        override fun setExitCallback(callback: ExitCallback) { this.exit = callback }

        override fun start(channel: ChannelSession, env: Environment) {
            Thread {
                try {
                    output?.write("cloud-shell-ready\r\n".toByteArray())
                    output?.flush()
                    val reader = input!!.bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        output?.write("echo:$line\r\n".toByteArray())
                        output?.flush()
                        if (line == "exit") break
                    }
                } catch (_: Exception) { }
                exit?.onExit(0)
            }.start()
        }

        override fun destroy(channel: ChannelSession) { }
    }

    private class ExecCommand(private val command: String) : Command {
        private var output: OutputStream? = null
        private var exit: ExitCallback? = null

        override fun setInputStream(input: InputStream) { }
        override fun setOutputStream(output: OutputStream) { this.output = output }
        override fun setErrorStream(err: OutputStream) { }
        override fun setExitCallback(callback: ExitCallback) { this.exit = callback }

        override fun start(channel: ChannelSession, env: Environment) {
            output?.write("exec-ok:$command\n".toByteArray())
            output?.flush()
            exit?.onExit(0)
        }

        override fun destroy(channel: ChannelSession) { }
    }

    private fun startServer(): SshServer {
        val server = SshServer.setUpDefaultServer()
        server.port = 0
        server.keyPairProvider = SimpleGeneratorHostKeyProvider(File.createTempFile("dex-hostkey", ".ser").toPath())
        server.passwordAuthenticator = org.apache.sshd.server.auth.password.PasswordAuthenticator { user, pass, _ ->
            user == "dex" && pass == "secret"
        }
        server.shellFactory = org.apache.sshd.server.shell.ShellFactory { _ -> EchoShell() }
        server.commandFactory = org.apache.sshd.server.command.CommandFactory { _, command -> ExecCommand(command) }
        server.start()
        return server
    }

    @Test
    fun connectsStreamsShellAndExecutes() {
        val server = startServer()
        try {
            val output = StringBuilder()
            val connected = CountDownLatch(1)
            val session = CloudSession(object : CloudSession.Listener {
                override fun onOutput(text: String) {
                    synchronized(output) { output.append(text) }
                }

                override fun onConnected(banner: String) {
                    connected.countDown()
                }

                override fun onDisconnected(reason: String?) { }
            })

            session.connect(
                CloudProfile(host = "127.0.0.1", port = server.port, user = "dex", password = "secret"),
                80, 24
            )
            assertTrue("did not connect", connected.await(15, TimeUnit.SECONDS))

            val exec = session.exec("whoami")
            assertTrue("exec output was: $exec", exec.contains("exec-ok:whoami"))

            session.send("hello\r")
            var echoed = false
            for (i in 0 until 80) {
                Thread.sleep(100)
                synchronized(output) { echoed = output.contains("echo:hello") }
                if (echoed) break
            }
            assertTrue("shell echo missing, got: $output", echoed)

            session.disconnect()
            assertTrue(!session.isConnected())
        } finally {
            server.stop()
        }
    }
}
