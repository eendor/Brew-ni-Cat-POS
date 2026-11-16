package com.example.cattasticpos.service

import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256 checks for downloaded updates (FR-118). app_release.sha256 holds the
 * published hash; the POS installs an APK only when its hash matches.
 */
object ApkChecksum {
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** The hash in lowercase, or null when it isn't a SHA-256. */
    fun normalize(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { HEX64.matches(it) }

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return hex(digest.digest())
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** True only when there is a published hash and the file's hash equals it. */
    fun matches(expected: String?, actual: String): Boolean {
        val want = normalize(expected) ?: return false
        return want == actual.lowercase()
    }
}

/** The result of downloading an update. */
sealed interface ApkDownload {
    data class Ok(val file: java.io.File) : ApkDownload
    /** The network or server failed; trying again may work. */
    data object Failed : ApkDownload
    /** The file doesn't match the published SHA-256 (or none was published). It was deleted. */
    data object Rejected : ApkDownload
}
