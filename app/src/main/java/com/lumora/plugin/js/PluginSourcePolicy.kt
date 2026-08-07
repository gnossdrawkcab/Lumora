package com.lumora.plugin.js

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Executable plugin code must be authenticated in transit; loopback HTTP remains for testing. */
object PluginSourcePolicy {
    fun isAllowed(raw: String): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        return url.isHttps || (url.scheme == "http" &&
            (url.host == "localhost" || url.host == "::1" || url.host == "127.0.0.1"))
    }
}
