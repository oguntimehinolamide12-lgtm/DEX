package com.vibecode.app.linux

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Minimal streaming tar.gz extractor. Android has no tar library in the JVM API and
 * device `tar` builds vary, so the rootfs is unpacked by hand: regular files, dirs,
 * symlinks, hardlinks, GNU long names and pax headers are supported, which is
 * everything an Alpine minirootfs contains.
 */
object TarGz {

    fun extract(gz: InputStream, dest: File): Int {
        require(dest.isDirectory || dest.mkdirs()) { "Cannot create ${dest.absolutePath}" }
        val destRoot = dest.canonicalFile
        val input = BufferedInputStream(GZIPInputStream(BufferedInputStream(gz)), 32768)
        val header = ByteArray(512)
        var pendingName: String? = null
        var pendingLink: String? = null
        var count = 0

        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) break

            var name = readString(header, 0, 100)
            val mode = parseOctal(header, 100, 8).toInt()
            val size = parseOctal(header, 124, 12)
            val type = header[156].toInt().toChar()
            var link = readString(header, 157, 100)
            val prefix = readString(header, 345, 155)
            if (prefix.isNotEmpty()) name = "$prefix/$name"
            pendingName?.let { name = it; pendingName = null }
            pendingLink?.let { link = it; pendingLink = null }

            val small = size in 0..(4L * 1024 * 1024)
            var consumed = false

            when (type) {
                'L' -> {
                    if (small && size > 0) {
                        pendingName = readString(readExactly(input, size.toInt()), 0, size.toInt()).trimEnd('\u0000')
                    }
                    consumed = true
                }
                'K' -> {
                    if (small && size > 0) {
                        pendingLink = readString(readExactly(input, size.toInt()), 0, size.toInt()).trimEnd('\u0000')
                    }
                    consumed = true
                }
                'x', 'g' -> {
                    if (small && size > 0) {
                        val bytes = readExactly(input, size.toInt())
                        if (type == 'x') parsePax(bytes)?.let { pax ->
                            pax[0]?.let { name = it }
                            pax[1]?.let { link = it }
                        }
                    }
                    consumed = true
                }
                '0', '\u0000', '7' -> {
                    val out = safeChild(destRoot, name)
                    if (out == null || !small) {
                        skipFully(input, size)
                        consumed = true
                    } else {
                        out.parentFile?.mkdirs()
                        out.writeBytes(readExactly(input, size.toInt()))
                        Os.chmod(out.absolutePath, mode)
                        count++
                        consumed = true
                    }
                }
                '5' -> safeChild(destRoot, name)?.let { out ->
                    out.mkdirs()
                    Os.chmod(out.absolutePath, mode)
                }
                '2' -> safeChild(destRoot, name)?.let { out ->
                    out.parentFile?.mkdirs()
                    out.delete()
                    try { Os.symlink(link, out.absolutePath); count++ } catch (_: Exception) { }
                }
                '1' -> safeChild(destRoot, name)?.let { out ->
                    out.parentFile?.mkdirs()
                    out.delete()
                    try { Os.link(File(destRoot, link).absolutePath, out.absolutePath); count++ } catch (_: Exception) { }
                }
                else -> { }
            }

            if (!consumed && size > 0) skipFully(input, size)
            if (size > 0) {
                val pad = (512 - (size % 512)).toInt() % 512
                if (pad > 0) skipFully(input, pad.toLong())
            }
        }
        return count
    }

    private fun safeChild(root: File, name: String): File? {
        val out = File(root, name).canonicalFile
        return if (out.path.startsWith(root.path)) out else null
    }

    private fun parsePax(data: ByteArray): Array<String?>? {
        if (data.isEmpty()) return null
        val text = String(data, Charsets.UTF_8)
        var path: String? = null
        var linkPath: String? = null
        for (line in text.split('\n')) {
            val space = line.indexOf(' ')
            if (space <= 0) continue
            val body = line.substring(space + 1)
            when {
                body.startsWith("path=") -> path = body.removePrefix("path=")
                body.startsWith("linkpath=") -> linkPath = body.removePrefix("linkpath=")
            }
        }
        return arrayOf(path, linkPath)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n < 0) return offset == 0
            offset += n
        }
        return offset > 0
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = input.read(out, offset, length - offset)
            if (n < 0) break
            offset += n
        }
        return if (offset == length) out else out.copyOf(offset)
    }

    private fun skipFully(input: InputStream, length: Long) {
        var left = length
        val sink = ByteArray(8192)
        while (left > 0) {
            val n = input.read(sink, 0, minOf(sink.size.toLong(), left).toInt())
            if (n < 0) return
            left -= n
        }
    }

    private fun readString(bytes: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = minOf(offset + length, bytes.size)
        while (end < limit && bytes[end] != 0.toByte()) end++
        return String(bytes, offset, end - offset, Charsets.UTF_8)
    }

    private fun parseOctal(bytes: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        for (i in offset until offset + length) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0 || b == ' '.code) continue
            if (b < '0'.code || b > '7'.code) break
            value = value * 8 + (b - '0'.code)
        }
        return value
    }
}

/** Thin wrapper over android.system.Os so the extractor stays readable. */
private object Os {
    fun chmod(path: String, mode: Int) {
        try {
            android.system.Os.chmod(path, mode)
        } catch (_: Exception) {
            val f = java.io.File(path)
            f.setReadable(true, false)
            f.setWritable(true, false)
            if (mode and 0b001_000_000 != 0) f.setExecutable(true, false)
        }
    }

    fun symlink(target: String, linkPath: String) {
        try {
            android.system.Os.symlink(target, linkPath)
        } catch (e: Exception) {
            throw java.io.IOException("symlink failed: $linkPath -> $target", e)
        }
    }

    fun link(oldPath: String, newPath: String) {
        try {
            android.system.Os.link(oldPath, newPath)
        } catch (e: Exception) {
            // Hardlink fallback: copy the target.
            java.io.File(oldPath).copyTo(java.io.File(newPath), overwrite = true)
        }
    }
}
