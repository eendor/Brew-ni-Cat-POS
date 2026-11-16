package com.example.cattasticpos

import com.example.cattasticpos.service.ApkChecksum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TC-P9-073: the POS installs an update only when its SHA-256 matches app_release (FR-118). */
class ApkChecksumTest {
    private val apk = ByteArray(100_000) { (it % 253).toByte() }
    private val sha = ApkChecksum.sha256Hex(apk.inputStream())

    @Test fun hashesLikeSha256sum() {
        // sha256 of "abc"
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ApkChecksum.sha256Hex("abc".byteInputStream()),
        )
    }

    @Test fun acceptsOnlyAMatchingPublishedHash() {
        assertTrue(ApkChecksum.matches(sha, sha))
        assertTrue("upper case and spaces in the row are fine", ApkChecksum.matches(" ${sha.uppercase()} ", sha))
        val changed = apk.copyOf().also { it[0] = 7 }
        assertFalse(ApkChecksum.matches(sha, ApkChecksum.sha256Hex(changed.inputStream())))
        assertFalse("no published hash means no install", ApkChecksum.matches(null, sha))
        assertFalse(ApkChecksum.matches("abc", sha))
    }

    @Test fun normalizesOnlyRealHashes() {
        assertEquals(sha, ApkChecksum.normalize(sha.uppercase()))
        assertNull(ApkChecksum.normalize(""))
        assertNull(ApkChecksum.normalize("z".repeat(64)))
        assertNull(ApkChecksum.normalize(null))
    }
}
