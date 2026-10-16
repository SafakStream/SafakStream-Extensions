package com.safakstream.cncverse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.lagradost.cloudstream3.plugins.BaseSettingsFragment
import com.lagradost.cloudstream3.plugins.Plugin

/**
 * CNCVerse ayarlar ekranı.
 * - "Verify Now" → WebView açar, reCAPTCHA
 * - "Clear Cookies" → cookie'leri siler
 */
class CNCVerseSettings : BaseSettingsFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(
            com.safakstream.cncverse.R.layout.settings,
            container,
            false
        )

        val context = view.context

        // "Verify Now" butonu
        view.findViewById<Button>(com.safakstream.cncverse.R.id.verifyBtn)?.setOnClickListener {
            Toast.makeText(context, "Opening verify page...", Toast.LENGTH_SHORT).show()
            CNCVerseUtils.startVerify(context)
        }

        // "Clear Cookies" butonu
        view.findViewById<Button>(com.safakstream.cncverse.R.id.clearCacheBtn)?.setOnClickListener {
            CNCVerseCookieStore.clear(context)
            Toast.makeText(context, "Cookies cleared", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
