package com.lumora.data.update

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Pure update checks kept separate so URL/digest handling is covered by ordinary JVM tests. */
object UpdateVerifier {
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    fun parseSha256Digest(raw: String?): String? = raw
        ?.trim()
        ?.lowercase()
        ?.removePrefix("sha256:")
        ?.takeIf { it.matches(SHA256) }

    fun isTrustedReleaseUrl(raw: String, repository: String): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        val repositoryParts = repository.split('/')
        if (repositoryParts.size != 2) return false
        val parts = url.pathSegments
        return url.isHttps && url.host == "github.com" && parts.size >= 6 &&
            parts[0].equals(repositoryParts[0], ignoreCase = true) &&
            parts[1].equals(repositoryParts[1], ignoreCase = true) &&
            parts[2] == "releases" && parts[3] == "download" &&
            parts.last().endsWith(".apk", ignoreCase = true)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matchesSha256(file: File, expected: String?): Boolean {
        val normalized = parseSha256Digest(expected) ?: return false
        return MessageDigest.isEqual(
            sha256(file).toByteArray(Charsets.US_ASCII),
            normalized.toByteArray(Charsets.US_ASCII),
        )
    }
}
