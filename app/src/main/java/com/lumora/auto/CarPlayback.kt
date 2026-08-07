package com.lumora.auto

import android.content.Context
import android.view.Surface
import androidx.media3.common.Player
import com.lumora.cache.ChannelCache
import com.lumora.cache.FavoritesStore
import com.lumora.cache.RecentlyPlayedStore
import com.lumora.model.Channel
import com.lumora.model.MediaType
import com.lumora.player.PlayerManager
import com.lumora.player.PlaybackResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The car session's own player and channel list.
 *
 * Separate from the phone Activity's [PlayerManager] on purpose: the two screens are
 * independent surfaces with independent lifecycles, and the car session routinely outlives
 * (or precedes) the Activity. Audio focus arbitrates if both ever run at once.
 *
 * The catalogue comes from the on-disk channel cache rather than a fresh provider fetch -
 * the car session can start with no Activity ever having run, and a cold multi-provider
 * fetch is tens of seconds of work behind a driver waiting for a picture.
 */
class CarPlayback(private val context: Context) {

    private val player = PlayerManager(context)
    private val resolver = PlaybackResolver.from(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playGeneration = 0L

    var channels: List<Channel> = emptyList()
        private set

    var current: Channel? = null
        private set

    val isPlaying: Boolean get() = player.isPlaying

    /**
     * Live channels that can be played from a URL alone. Stalker commands, plugin tokens and
     * plugin tokens still require UI/plugin state that is not safe to recreate in a car
     * session. Jellyfin is resolved by the shared PlaybackResolver and is supported here.
     */
    fun loadCatalog(): List<Channel> {
        val cached = ChannelCache.load(context).orEmpty()
        channels = cached.filter {
            it.mediaType == MediaType.LIVE && it.url.isNotBlank() &&
                it.stalkerCmd.isNullOrBlank() && it.pluginToken.isNullOrBlank()
        }
        return channels
    }

    fun favourites(): List<Channel> {
        val ids = FavoritesStore.getFavoriteChannelIds(context)
        return channels.filter { it.id in ids }
    }

    /** Recently played, in the order they were played, limited to what the car list shows. */
    fun recents(limit: Int = 20): List<Channel> {
        val ids = RecentlyPlayedStore.getRecentIds(context)
        val byId = channels.associateBy { it.id }
        return ids.mapNotNull { byId[it] }.take(limit)
    }

    /** Category name -> channels, for the browse list. Uncategorised channels are grouped
     *  under one heading rather than dropped. */
    fun categories(): Map<String, List<Channel>> =
        channels.groupBy { it.categoryName?.takeIf { name -> name.isNotBlank() } ?: "Other" }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)

    fun play(channel: Channel) {
        current = channel
        RecentlyPlayedStore.recordPlayed(context, channel.id)
        val generation = ++playGeneration
        scope.launch {
            val resolved = withContext(Dispatchers.IO) { resolver.resolve(channel) }.getOrNull()
            if (generation != playGeneration || resolved == null) return@launch
            player.playUrl(
                url = resolved.url,
                userAgent = resolved.userAgent,
                headers = resolved.headers,
            )
        }
    }

    /** Next/previous within whatever list the user was browsing, wrapping at the ends -
     *  a driver pressing skip should never land on "nothing happened". */
    fun step(from: List<Channel>, delta: Int) {
        if (from.isEmpty()) return
        val index = from.indexOfFirst { it.id == current?.id }
        val next = if (index < 0) 0 else ((index + delta) % from.size + from.size) % from.size
        play(from[next])
    }

    fun togglePlayPause() = player.togglePlayPause()

    /**
     * Where the picture goes. The car surface is turned into a VirtualDisplay carrying a
     * Presentation (see CarPlayerScreen), so what the player actually attaches to is an
     * ordinary SurfaceView inside that window - the same thing it uses on the phone.
     */
    fun setSurfaceView(surfaceView: android.view.SurfaceView) = player.setSurfaceView(surfaceView)

    /** Detach, when the car screen goes away. */
    fun setSurface(surface: Surface?) = player.setVideoSurface(surface)

    /**
     * Drops the video track while keeping the audio one - what the parked check uses when the
     * car starts moving. Cheaper and far less disruptive than stopping playback: the stream
     * keeps running and the sound continues uninterrupted.
     */
    fun setVideoEnabled(enabled: Boolean) {
        val exo = player.getExoPlayer()
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, !enabled)
            .build()
    }

    fun addListener(listener: Player.Listener) = player.addListener(listener)

    fun release() {
        playGeneration++
        scope.cancel()
        player.setVideoSurface(null)
        player.release()
    }
}
