package com.safakstream.inattv

import android.content.Context

/**
 * Kanal posterlerini üretir.
 * ChannelBranding'den logo döner.
 */
class ChannelArtwork(private val context: Context) {

    /**
     * Kanal için poster URL'i döner
     */
    fun poster(channel: SportsChannel): String {
        val brand = ChannelBranding.forChannel(channel)
        return brand.logo
    }
}
