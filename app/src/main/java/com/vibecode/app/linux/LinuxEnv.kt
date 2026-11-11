package com.vibecode.app.linux

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Installs the proot Linux environment.
 *
 * proot itself ships inside the APK as a jniLib (Android refuses to execute
 * binaries written to app storage on newer releases, but the native library
 * directory is always executable). The only download is the Alpine minirootfs.
 */
object LinuxEnv {

    private const val ALPINE_MAJOR = "3.21"
    private const val ALPINE_RELEASE = "3.21.3"

    data class Target(val abi: String, val alpineArch: String, val prootArch: String)

    fun target(): Target? = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> Target("arm64-v8a", "aarch64", "aarch64")
        "armeabi-v7a" -> Target("armeabi-v7a", "armv7", "arm")
        "x86_64" -> Target("x86_64", "x86_64", "x86_64")
        "x86" -> Target("x86", "x86", "x86")
        else -> null
    }

    fun baseDir(ctx: Context) = File(ctx.filesDir, "linux")
    fun rootfsDir(ctx: Context) = File(baseDir(ctx), "rootfs")
    fun libDir(ctx: Context) = File(baseDir(ctx), "lib")

    /** proot is packaged as libproot.so so Android extracts it executable. */
    fun prootFile(ctx: Context): File {
        val bundled = File(ctx.applicationInfo.nativeLibraryDir, "libproot.so")
        val copied = File(baseDir(ctx), "bin/proot")
        return if (bundled.isFile) bundled else copied
    }

    fun alpineUrl(arch: String) =
        "https://dl-cdn.alpinelinux.org/alpine/v$ALPINE_MAJOR/releases/$arch/alpine-minirootfs-$ALPINE_RELEASE-$arch.tar.gz"

    fun isInstalled(ctx: Context): Boolean {
        val root = rootfsDir(ctx)
        if (!root.isDirectory) return false
        val shellOk = File(root, "bin/sh").exists() ||
            File(root, "bin/busybox").exists() ||
            File(root, "bin/ash").exists() ||
            File(root, "usr/bin/env").exists()
        val rootfsLooksPresent = shellOk || (File(root, "etc").isDirectory && File(root, "usr").isDirectory)
        if (!rootfsLooksPresent) return false
        val proot = prootFile(ctx)
        val bundled = File(ctx.applicationInfo.nativeLibraryDir, "libproot.so")
        return proot.isFile || bundled.isFile
    }

    // ------------------------------------------------------------------ install

    fun install(ctx: Context, onStatus: (String, Int) -> Unit) {
        val target = target() ?: throw IOException("Unsupported device ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        val base = baseDir(ctx).apply { mkdirs() }
        File(base, "bin").mkdirs()
        val rootfs = rootfsDir(ctx)
        val libs = libDir(ctx).apply { mkdirs() }

        onStatus("linking proot runtime", 6)
        installRuntimeLibs(ctx, libs)
        val proot = prootFile(ctx)
        if (!proot.isFile) throw IOException("This APK was built without the bundled proot binary.")

        onStatus("downloading alpine ${ALPINE_RELEASE}", 12)
        val tgz = File(base, "alpine.tar.gz")
        download(alpineUrl(target.alpineArch), tgz) { pct ->
            onStatus("downloading alpine ${ALPINE_RELEASE}", 12 + pct * 70 / 100)
        }

        onStatus("extracting rootfs", 84)
        rootfs.deleteRecursively()
        rootfs.mkdirs()
        TarGz.extract(tgz.inputStream(), rootfs)
        tgz.delete()

        onStatus("configuring", 94)
        configure(ctx, rootfs)

        onStatus("checking proot", 98)
        val version = runProotVersion(ctx)
        if (version == null) {
            throw IOException(
                "proot could not start on this device. If this build targets SDK 29+, " +
                    "Android blocks executing the rootfs binaries — target SDK 28 is required."
            )
        }
        onStatus("ready", 100)
    }

    /** Copies the bundled native proot + its shared libraries with their sonames. */
    private fun installRuntimeLibs(ctx: Context, libs: File) {
        val native = File(ctx.applicationInfo.nativeLibraryDir)
        val mapping = mapOf(
            "libtalloc.so" to "libtalloc.so.2",
            "libandroid_shmem.so" to "libandroid-shmem.so"
        )
        for ((source, target) in mapping) {
            val from = File(native, source)
            if (!from.isFile) continue
            val to = File(libs, target)
            if (to.length() != from.length()) {
                from.copyTo(to, overwrite = true)
                to.setReadable(true, false)
            }
        }
        // keep a non-executable copy of the proot binary path resolution honest
        val proot = File(native, "libproot.so")
        if (proot.isFile) {
            val marker = File(baseDir(ctx), "bin/.proot-present")
            try { marker.writeText(proot.absolutePath) } catch (_: Exception) { }
        }
    }

    private fun configure(ctx: Context, rootfs: File) {
        File(rootfs, "root").mkdirs()
        File(rootfs, "etc").mkdirs()
        File(rootfs, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(rootfs, "etc/hosts").writeText("127.0.0.1 localhost dex\n::1 localhost ip6-localhost\n")
        File(rootfs, "root/.profile").writeText(
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export LANG=C.UTF-8
            alias ll='ls -la'
            """.trimIndent() + "\n"
        )
        // mount point for the host storage bind performed by TerminalSession
        File(rootfs, "root/sdcard").mkdirs()
        File(ctx.filesDir, "linux/home").mkdirs()
    }

    fun runProotVersion(ctx: Context): String? {
        return try {
            val builder = ProcessBuilder(prootFile(ctx).absolutePath, "--version").redirectErrorStream(true)
            builder.environment()["LD_LIBRARY_PATH"] = libDir(ctx).absolutePath
            val process = builder.start()
            val out = process.inputStream.bufferedReader().readText().trim()
            val waiter = Thread { try { process.waitFor() } catch (_: Exception) { } }
            waiter.start()
            waiter.join(8000)
            if (waiter.isAlive) { process.destroyForcibly(); return null }
            if (process.exitValue() == 0) out else null
        } catch (_: Exception) {
            null
        }
    }

    private fun download(url: String, out: File, onProgress: (Int) -> Unit) {
        var current = URL(url)
        var redirects = 0
        while (redirects++ < 5) {
            val conn = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "DEX-Android-IDE")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IOException("Redirect without location")
                current = URL(current, location)
                conn.disconnect()
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("HTTP $code for $url")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(out).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        copied += n
                        if (total > 0) {
                            val pct = (copied * 100 / total).toInt()
                            if (pct != lastPct && pct % 3 == 0) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
            conn.disconnect()
            return
        }
        throw IOException("Too many redirects for $url")
    }
}
