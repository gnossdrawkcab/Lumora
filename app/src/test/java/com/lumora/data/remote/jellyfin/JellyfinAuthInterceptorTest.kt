package com.lumora.data.remote.jellyfin

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinAuthInterceptorTest {
    private val configured = "https://jellyfin.example:8920/base".toHttpUrl()

    @Test
    fun `same origin accepts another path on configured server`() {
        assertTrue(
            JellyfinAuthInterceptor.sameOrigin(
                "https://jellyfin.example:8920/Items/1".toHttpUrl(),
                configured,
            )
        )
    }

    @Test
    fun `same origin rejects suffix lookalike host`() {
        assertFalse(
            JellyfinAuthInterceptor.sameOrigin(
                "https://eviljellyfin.example:8920/steal".toHttpUrl(),
                configured,
            )
        )
    }

    @Test
    fun `same origin rejects scheme downgrade and different port`() {
        assertFalse(
            JellyfinAuthInterceptor.sameOrigin(
                "http://jellyfin.example:8920/Items".toHttpUrl(),
                configured,
            )
        )
        assertFalse(
            JellyfinAuthInterceptor.sameOrigin(
                "https://jellyfin.example/Items".toHttpUrl(),
                configured,
            )
        )
    }
}
