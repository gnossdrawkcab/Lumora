package com.lumora.data.remote.jellyfin

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Tracks the currently-authenticated Jellyfin connection so the app's *shared* OkHttp
 *  client (used for poster/logo/backdrop image fetches, not just JellyfinProvider's own
 *  API calls) knows to attach an auth token - without this, image requests to a server
 *  that doesn't allow anonymous image access just fail. */
object JellyfinSession {
    /** Origin and credential move together so a concurrent reconnect cannot pair the old host
     *  with the new token (or vice versa) for even one request. */
    internal data class Active(val origin: HttpUrl, val accessToken: String)

    @Volatile internal var active: Active? = null
        private set

    fun update(serverBase: String, token: String) {
        val url = serverBase.toHttpUrlOrNull() ?: return clear()
        active = Active(url.newBuilder().encodedPath("/").query(null).fragment(null).build(), token)
    }

    fun clear() {
        active = null
    }
}
