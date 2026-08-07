package com.lumora.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Opens Lumora's application preferences using a key held by Android Keystore.
 *
 * Provider URLs routinely contain usernames/passwords, and Jellyfin access tokens are bearer
 * credentials. Keeping those values in the old plain SharedPreferences file made both Android
 * backups and a copied app-data directory disclose them. The first successful open migrates the
 * complete old file, commits the encrypted copy, and only then clears the plaintext source.
 */
object SecurePreferences {
    const val FILE_NAME = "iptv_secure_prefs"
    private const val MIGRATION_COMPLETE = "__secure_preferences_migrated_v1"
    @Volatile private var cached: SharedPreferences? = null

    fun open(context: Context, legacyFileName: String = "iptv_prefs"): SharedPreferences {
        cached?.let { return it }
        return synchronized(this) {
            cached?.let { return@synchronized it }
            create(context.applicationContext, legacyFileName).also { cached = it }
        }
    }

    private fun create(context: Context, legacyFileName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val secure = EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        migratePlainPreferences(
            context.getSharedPreferences(legacyFileName, Context.MODE_PRIVATE),
            secure,
        )
        return secure
    }

    private fun migratePlainPreferences(
        legacy: SharedPreferences,
        secure: SharedPreferences,
    ) {
        if (secure.getBoolean(MIGRATION_COMPLETE, false)) return

        val editor = secure.edit()
        legacy.all.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.putBoolean(MIGRATION_COMPLETE, true)
        // Never erase the only readable copy if Keystore/encrypted-prefs persistence failed.
        if (editor.commit()) legacy.edit().clear().commit()
    }
}
