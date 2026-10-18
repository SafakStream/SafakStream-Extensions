package com.safakstream.hub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.plugins.Plugin

class HubFragment(private val plugin: Plugin) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // XML dosyasını kendi paket adımızdan yüklüyoruz
        val id = plugin.resources?.getIdentifier("fragment_hub", "layout", "com.safakstream.hub")
        if (id != null && id != 0) {
            val layout = plugin.resources?.getLayout(id)
            if (layout != null) {
                return inflater.inflate(layout, container, false)
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Butonları ID'leri ile bul
        val githubBtn = view.findViewById<Button>(plugin.resources!!.getIdentifier("btn_github", "id", "com.safakstream.hub"))
        val kreosusBtn = view.findViewById<Button>(plugin.resources!!.getIdentifier("btn_kreosus", "id", "com.safakstream.hub"))

        // Tıklama olayları
        githubBtn?.setOnClickListener {
            openBrowser("https://github.com/SafakStream")
        }

        kreosusBtn?.setOnClickListener {
            openBrowser("https://kreosus.com/safakstream")
        }
    }

    private fun openBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
