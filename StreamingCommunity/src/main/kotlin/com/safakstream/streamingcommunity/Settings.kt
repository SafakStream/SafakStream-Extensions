package com.safakstream.streamingcommunity

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity

class Settings(
    private val plugin: StreamingCommunityPlugin,
    private val sharedPref: SharedPreferences?
) : BottomSheetDialogFragment() {

    private var currentLang: String = "it"
    private var currentLangPosition: Int = 0
    private var currentBaseUrl: String = ""

    init {
        sharedPref?.let { pref ->
            currentLang = pref.getString(StreamingCommunityPlugin.PREF_LANG, "it") ?: "it"
            currentLangPosition = pref.getInt(StreamingCommunityPlugin.PREF_LANG_POSITION, 0)
            currentBaseUrl = StreamingCommunity.Companion.normalizeBaseUrl(
                pref.getString(StreamingCommunityPlugin.PREF_BASE_URL, "")
            ) ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Layout dosyasını yükle
        val layoutId = resources.getIdentifier("settings", "layout", requireContext().packageName)
        return if (layoutId != 0) {
            inflater.inflate(layoutId, container, false)
        } else super.onCreateView(inflater, container, savedInstanceState)
    }

    @RequiresApi(23)
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Başlık ve Etiketler
        view.findViewById<TextView>(R.id.header_tw)?.text = getStringRes("header_tw")
        view.findViewById<TextView>(R.id.lang_label)?.text = getStringRes("lang_label")
        
        // 2. Dil Seçimi (Spinner)
        val langsDropdown = view.findViewById<Spinner>(R.id.lang_spinner)
        val langs = arrayOf("it", "en", "tr") // Diller
        val langsMap = langs.map { it to getStringRes(it) } // Görünen isimler
        
        langsDropdown?.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            langsMap.map { it.second }
        )
        langsDropdown?.setSelection(currentLangPosition)
        langsDropdown?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentLang = langs[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 3. Yaklaşanları Göster (Switch)
        val upcomingSwitch = view.findViewById<Switch>(R.id.upcoming_switch)
        val showUpcoming = sharedPref?.getBoolean(StreamingCommunityPlugin.PREF_SHOW_UPCOMING, true) ?: true
        upcomingSwitch?.isChecked = showUpcoming
        upcomingSwitch?.text = getStringRes("upcoming_label")
        upcomingSwitch?.setOnCheckedChangeListener { _, isChecked ->
            // Switch durumu kaydedilecek
        }

        // 4. Sunucu Adresi (Host / URL)
        val serverAddressInput = view.findViewById<EditText>(R.id.server_address_input)
        serverAddressInput?.hint = getStringRes("server_address_hint")
        serverAddressInput?.setText(currentBaseUrl)

        // 5. Kaydet Butonu
        val saveBtn = view.findViewById<ImageButton>(R.id.save_btn)
        saveBtn?.setOnClickListener {
            val rawUrl = serverAddressInput?.text?.toString()?.trim() ?: ""
            val normalizedUrl = StreamingCommunity.Companion.normalizeBaseUrl(rawUrl) ?: ""

            sharedPref?.edit()?.apply {
                putInt(StreamingCommunityPlugin.PREF_LANG_POSITION, langs.indexOf(currentLang))
                putString(StreamingCommunityPlugin.PREF_LANG, currentLang)
                putBoolean(StreamingCommunityPlugin.PREF_SHOW_UPCOMING, upcomingSwitch?.isChecked ?: true)
                if (normalizedUrl.isBlank()) {
                    remove(StreamingCommunityPlugin.PREF_BASE_URL)
                } else {
                    putString(StreamingCommunityPlugin.PREF_BASE_URL, normalizedUrl)
                }
                apply()
            }
            
            CommonActivity.showToast("Saved. Restart the app to apply the settings")
            dismiss()
        }
    }

    private fun getStringRes(name: String): String {
        val id = resources.getIdentifier(name, "string", requireContext().packageName)
        return if (id != 0) resources.getString(id) else name
    }
}
