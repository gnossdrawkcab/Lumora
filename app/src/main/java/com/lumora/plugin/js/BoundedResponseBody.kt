package com.lumora.plugin.js

import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream

/** Reads an HTTP response without letting an untrusted plugin allocate an unbounded String. */
internal fun ResponseBody.readUtf8Capped(maxBytes: Int): String? {
    if (contentLength() > maxBytes) return null
    val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
    byteStream().use { input ->
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
    }
    return output.toString(Charsets.UTF_8.name())
}
