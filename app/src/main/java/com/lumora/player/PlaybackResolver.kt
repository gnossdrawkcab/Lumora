package com.lumora.player

import android.content.Context
import com.lumora.data.remote.jellyfin.JellyfinConnector
import com.lumora.model.Channel

/** Resolves provider-specific metadata into the URL and headers PlayerManager understands. */
class PlaybackResolver(
    private val jellyfinConnector: JellyfinConnector,
) {
    data class Resolved(
        val url: String,
        val userAgent: String? = null,
        val headers: Map<String, String>? = null,
    )

    suspend fun resolve(channel: Channel, startPositionMs: Long = 0L): Result<Resolved> {
        if (!channel.isJellyfin) {
            if (channel.url.isBlank()) return Result.failure(Exception("Stream has no direct URL"))
            return Result.success(Resolved(channel.url, channel.streamUserAgent, channel.streamHeaders))
        }
        if (channel.id.isBlank()) return Result.failure(Exception("Jellyfin item has no ID"))

        val connection = jellyfinConnector.connect().getOrElse { return Result.failure(it) }
        val negotiated = connection.provider.resolveStream(channel.id, startPositionMs)
        return if (negotiated != null) {
            Result.success(Resolved(negotiated.url, channel.streamUserAgent))
        } else if (channel.url.isNotBlank()) {
            // Static fallback remains useful for older Jellyfin servers that do not implement
            // PlaybackInfo fully, but unlike the old car path it is authenticated.
            Result.success(
                Resolved(
                    channel.url,
                    channel.streamUserAgent,
                    channel.streamHeaders.orEmpty() + ("X-Emby-Token" to connection.accessToken),
                )
            )
        } else {
            Result.failure(Exception("Jellyfin could not resolve this stream"))
        }
    }

    companion object {
        fun from(context: Context) = PlaybackResolver(JellyfinConnector.from(context))
    }
}
