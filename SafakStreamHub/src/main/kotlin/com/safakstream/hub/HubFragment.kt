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
        // XML layout dosyasını yükle
        val id = plugin.resources!!.getIdentifier("fragment_hub", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = plugin.resources!!.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Butonları bul ve tıklama olaylarını ayarla
        val githubButton = view.findViewById<Button>(R.id.buttonGithub)
        val kreosusButton = view.findViewById<Button>(R.id.buttonKreosus)

        githubButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SafakStream"))
            startActivity(intent)
        }

        kreosusButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kreosus.com/safakstream"))
            startActivity(intent)
        }
    }
}
