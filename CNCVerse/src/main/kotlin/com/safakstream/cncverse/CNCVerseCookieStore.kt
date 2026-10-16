package com.safakstream.cncverse

import android.content.Context
import android.content.SharedPreferences

/**
 * Cookie'leri ve token'ları SharedPreferences'a kaydeder/okur.
 */
object CNCVerseCookieStore {

    private const val PREFS_NAME = "cncverse_prefs"
    private const val KEY_HEE5 = "hee5_token"
    private const val KEY_COOKIES = "cookies_json"
    private const val KEY_TIMESTAMP = "cookies_timestamp"

    // 7 gün geçerli (cookie'ler expire olursa tekrar verify)
    private const val COOKIE_TTL_MS = 7L * 24 * 60 * 60 * 1000

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, hee5: String?, cookies: Map<String, String>) {
        val cookieJson = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        prefs(context).edit()
            .putString(KEY_HEE5, hee5)
            .putString(KEY_COOKIES, cookieJson)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getHee5(context: Context): String? = prefs(context).getString(KEY_HEE5, null)

    fun getCookies(context: Context): Map<String, String> {
        val str = prefs(context).getString(KEY_COOKIES, null) ?: return emptyMap()
        return str.split(";").mapNotNull {
            val parts = it.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    fun isValid(context: Context): Boolean {
        val ts = prefs(context).getLong(KEY_TIMESTAMP, 0L)
        val cookies = getCookies(context)
        return cookies.isNotEmpty() && (System.currentTimeMillis() - ts) < COOKIE_TTL_MS
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
