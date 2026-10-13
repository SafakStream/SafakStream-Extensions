package com.safakstream

import android.content.Context
import android.content.SharedPreferences

object NetMirrorStorage {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("NetMirrorPrefs", Context.MODE_PRIVATE)
    }

    fun getCookie(): Pair<String?, Long> {
        val p = prefs ?: return Pair(null, 0L)
        return Pair(p.getString("nf_cookie", null), p.getLong("nf_cookie_timestamp", 0L))
    }

    fun saveCookie(cookie: String) {
        prefs?.edit()?.putString("nf_cookie", cookie)?.putLong("nf_cookie_timestamp", System.currentTimeMillis())?.apply()
    }

    fun getApiBase(): Pair<String?, Long> {
        val p = prefs ?: return Pair(null, 0L)
        return Pair(p.getString("newtv_api_base", null), p.getLong("newtv_api_base_timestamp", 0L))
    }

    fun saveApiBase(apiBase: String) {
        prefs?.edit()?.putString("newtv_api_base", apiBase)?.putLong("newtv_api_base_timestamp", System.currentTimeMillis())?.apply()
    }

    fun getUserToken(ott: String): Pair<String?, Long> {
        val p = prefs ?: return Pair(null, 0L)
        return Pair(p.getString("usertoken_$ott", null), p.getLong("usertoken_timestamp_$ott", 0L))
    }

    fun saveUserToken(ott: String, token: String) {
        prefs?.edit()?.putString("usertoken_$ott", token)?.putLong("usertoken_timestamp_$ott", System.currentTimeMillis())?.apply()
    }

    fun clearAll() {
        prefs?.edit()?.clear()?.apply()
    }
}
