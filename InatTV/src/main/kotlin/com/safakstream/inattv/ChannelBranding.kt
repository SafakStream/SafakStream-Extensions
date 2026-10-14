package com.safakstream.inattv

object ChannelBranding {
    private const val TR = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/turkey/"
    private const val BEIN = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/international/beinsports/old/horizontal/"

    data class Brand(val title: String, val logo: String)

    private val channels: Map<String, Brand> = buildMap {
        // beIN Sports 1-5
        for (i in 1..5) {
            put("bein-$i", Brand(
                "beIN Sports $i",
                "${BEIN}bein-sports-$i-hz-int.png"
            ))
        }
        // beIN Sports Max 1-2
        for (i in 1..2) {
            put("bein-max-$i", Brand(
                "beIN Sports Max $i",
                "${BEIN}bein-sports-$i-max-hz-int.png"
            ))
        }
        // S Sport
        put("s-sport-1", Brand("S Sport", "${TR}s-sport-tr.png"))
        put("s-sport-2", Brand("S Sport 2", "${TR}s-sport-2-tr.png"))

        // Spor Smart
        put("smartspor-1", Brand("Spor Smart", "${TR}spor-smart-hd-tr.png"))
        put("smartspor-2", Brand("Spor Smart 2", "https://i.imgur.com/qyUKCUa.png"))

        // Tivibu Spor 1-4
        val tivibu = listOf("qvrKQY3", "fZMSjNE", "xLrgt2O", "LgGxe7z")
        for (i in 1..4) {
            put("tivibuspor-$i", Brand("Tivibu Spor $i", "https://i.imgur.com/${tivibu[i - 1]}.png"))
        }

        // TRT
        put("trt-1", Brand("TRT 1", "${TR}trt-1-tr.png"))
        put("trt-2", Brand("TRT 2", "${TR}trt-2-tr.png"))
        put("trt-spor", Brand("TRT Spor", "${TR}trt-spor-tr.png"))
        put("trt-yildiz", Brand("TRT Spor Yıldız", "${TR}trt-spor-yildiz-tr.png"))

        // Diğer Türk kanalları
        put("a-spor", Brand("A Spor", "${TR}a-spor-tr.png"))
        put("tv-85", Brand("TV8,5", "${TR}tv85-tr.png"))

        // Uluslararası
        put("nba-tv", Brand("NBA TV", "https://upload.wikimedia.org/wikipedia/en/thumb/d/d2/NBA_TV.svg/960px-NBA_TV.svg.png"))
        put("eurosport-1", Brand("Eurosport 1", "https://i.imgur.com/olQJgm7.png"))
        put("eurosport-2", Brand("Eurosport 2", "https://i.imgur.com/f56dHgR.png"))
    }

    fun forChannel(channel: SportsChannel): Brand {
        return channels[channel.id] ?: Brand(channel.title, "")
    }
}
