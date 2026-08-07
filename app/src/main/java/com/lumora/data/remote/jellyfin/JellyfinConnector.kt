package com.lumora.data.remote.jellyfin

import android.content.Context
import android.content.SharedPreferences
import com.lumora.BaseApplication
import com.lumora.data.security.SecurePreferences
import com.lumora.util.normalizeServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Activity-independent Jellyfin authentication.
 *
 * The old connection path was an extension on MainActivity, which made services such as the
 * Android Auto session unable to resolve authenticated streams. Keeping the credentials and
 * session restoration here gives every playback surface the same behavior.
 */
class JellyfinConnector(
    private val client: OkHttpClient,
    private val prefs: SharedPreferences,
) {
    data class Connection(
        val provider: JellyfinProvider,
        val serverUrl: String,
        val accessToken: String,
        val userId: String,
    )

    fun configuredServerUrl(): String? = prefs.getString("jellyfin_url", null)
        ?.takeIf { it.isNotBlank() }
        ?.let { normalizeServerUrl(it, defaultScheme = "https") }

    suspend fun connect(serverUrl: String? = configuredServerUrl()): Result<Connection> {
        val url = serverUrl ?: return Result.failure(Exception("Jellyfin: no server URL"))
        val provider = JellyfinProvider(client)
        val savedToken = prefs.getString("jellyfin_token", null)
        val savedUserId = prefs.getString("jellyfin_userid", null)
        if (!savedToken.isNullOrBlank() && !savedUserId.isNullOrBlank()) {
            provider.restoreSession(url, savedToken, savedUserId)
            return Result.success(Connection(provider, url, savedToken, savedUserId))
        }

        val username = prefs.getString("jellyfin_user", null)
            ?: return Result.failure(Exception("Jellyfin: no username"))
        val password = prefs.getString("jellyfin_pass", null).orEmpty()
        val auth = withContext(Dispatchers.IO) { provider.authenticate(url, username, password) }
            .getOrElse {
                return Result.failure(Exception("Jellyfin: ${it.message?.take(60)}", it))
            }
        val token = auth.token ?: return Result.failure(Exception("Jellyfin: server returned no token"))
        val userId = auth.userId ?: return Result.failure(Exception("Jellyfin: server returned no user ID"))
        // Password logins used to keep the new token only in memory. Persist it so a car
        // session or a later cold start can restore playback without retaining the password.
        prefs.edit()
            .putString("jellyfin_token", token)
            .putString("jellyfin_userid", userId)
            .apply()
        return Result.success(Connection(provider, url, token, userId))
    }

    companion object {
        fun from(context: Context): JellyfinConnector {
            val app = context.applicationContext as BaseApplication
            return JellyfinConnector(app.okHttpClient, SecurePreferences.open(app))
        }
    }
}
