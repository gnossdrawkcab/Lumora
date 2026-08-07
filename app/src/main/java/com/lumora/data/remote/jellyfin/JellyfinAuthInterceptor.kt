package com.lumora.data.remote.jellyfin

import okhttp3.Interceptor
import okhttp3.Response

/** Attaches the Jellyfin session token to any request hitting the currently-connected
 *  Jellyfin server's host - added to the app's shared OkHttpClient so generic image
 *  loading (PosterLoader etc, which have no idea a given URL is Jellyfin-specific)
 *  gets authenticated automatically instead of silently 401ing. */
class JellyfinAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val active = JellyfinSession.active
        if (active == null || !sameOrigin(request.url, active.origin) ||
            !request.header("X-Emby-Token").isNullOrBlank()
        ) {
            return chain.proceed(request)
        }
        return chain.proceed(request.newBuilder().header("X-Emby-Token", active.accessToken).build())
    }

    companion object {
        /** Exact origin matching prevents a token for jellyfin.example from being attached to
         *  eviljellyfin.example, a scheme downgrade, or a different service on another port. */
        internal fun sameOrigin(left: okhttp3.HttpUrl, right: okhttp3.HttpUrl): Boolean =
            left.scheme == right.scheme && left.host == right.host && left.port == right.port
    }
}
