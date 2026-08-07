package com.lumora.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateVerifierTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val repository = "gnossdrawkcab/Lumora"

    @Test
    fun `only official Lumora release assets are trusted`() {
        assertTrue(
            UpdateVerifier.isTrustedReleaseUrl(
                "https://github.com/gnossdrawkcab/Lumora/releases/download/v3.4/Lumora.apk",
                repository,
            )
        )
        assertFalse(
            UpdateVerifier.isTrustedReleaseUrl(
                "https://evil.example/gnossdrawkcab/Lumora/releases/download/v3.4/Lumora.apk",
                repository,
            )
        )
        assertFalse(
            UpdateVerifier.isTrustedReleaseUrl(
                "http://github.com/gnossdrawkcab/Lumora/releases/download/v3.4/Lumora.apk",
                repository,
            )
        )
        assertFalse(
            UpdateVerifier.isTrustedReleaseUrl(
                "https://github.com/disclosurez/Lumora/releases/download/v3.4/Lumora.apk",
                repository,
            )
        )
    }

    @Test
    fun `digest parser accepts GitHub format and rejects malformed values`() {
        val hash = "ab".repeat(32)
        assertTrue(UpdateVerifier.parseSha256Digest("sha256:$hash") == hash)
        assertNull(UpdateVerifier.parseSha256Digest("sha256:not-a-hash"))
        assertNull(UpdateVerifier.parseSha256Digest(null))
    }

    @Test
    fun `file checksum uses constant-time digest comparison`() {
        val file = temporaryFolder.newFile("Lumora.apk").apply { writeText("known contents") }
        assertTrue(
            UpdateVerifier.matchesSha256(
                file,
                "sha256:a8c101f2219ec671b5721f0893cadbf0c9c18b23b76c53385b8856f5d1bbe31f",
            )
        )
        assertFalse(UpdateVerifier.matchesSha256(file, "sha256:${"00".repeat(32)}"))
        assertFalse(UpdateVerifier.matchesSha256(file, null))
    }
}
