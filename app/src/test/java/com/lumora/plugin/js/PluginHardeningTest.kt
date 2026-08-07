package com.lumora.plugin.js

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginHardeningTest {
    @Test
    fun `plugin sources require HTTPS except loopback development`() {
        assertTrue(PluginSourcePolicy.isAllowed("https://plugins.example/script.js"))
        assertTrue(PluginSourcePolicy.isAllowed("http://127.0.0.1:8080/script.js"))
        assertFalse(PluginSourcePolicy.isAllowed("http://plugins.example/script.js"))
        assertFalse(PluginSourcePolicy.isAllowed("file:///sdcard/script.js"))
    }

    @Test
    fun `response reader rejects content beyond its byte budget`() {
        assertTrue("small".toResponseBody().readUtf8Capped(5) == "small")
        assertNull("too large".toResponseBody().readUtf8Capped(5))
    }
}
