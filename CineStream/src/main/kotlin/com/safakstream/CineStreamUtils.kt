package com.safakstream.cinestream

// Cloudstream & NiceHttp
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

// Coroutines
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Network
import java.net.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody

// JSON & HTML Parsing
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Java Utils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Security & Crypto
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.math.BigInteger
import kotlin.math.min
import java.util.Base64

// NOT: Settings importu kaldırıldı.
// import com.megix.settings.Settings


class SpecOption(searchTerms: List<String>, val label: String) {
    constructor(term: String, label: String) : this(listOf(term), label)

    val regex = Regex(
        searchTerms.joinToString(
            separator = "|",
            prefix = "(?i)(?<=^|\\W)(?:",
            postfix = ")(?=[^a-zA-Z0-9_+]|$)"
        ) {
            Regex.escape(it)
        }
    )
}

val SPEC_OPTIONS = mapOf(
    "quality" to listOf(
        SpecOption("UHD BluRay", "4K UHD BluRay 💿"),
        SpecOption("BluRay", "BluRay 💿"),
        SpecOption("BluRay REMUX", "BluRay REMUX 💾"),
        SpecOption("BDRip", "BDRip 💿"),
        SpecOption("BRRip", "BRRip 💿"),
        SpecOption("DVD", "DVD Full/ISO 📀"),
        SpecOption("DVDRip", "DVDRip 📀"),
        SpecOption("DVD5", "DVD5 📀"),
        SpecOption("DVD9", "DVD9 📀"),
        SpecOption("HD-DVD", "HD-DVD 📀"),
        SpecOption("WEB-DL", "WEB-DL ☁️"),
        SpecOption("WEBRip", "WEBRip 🌐"),
        SpecOption("HDRip", "HDRip ✨"),
        SpecOption("HDTV", "HDTV 📺"),
        SpecOption("PDTV", "PDTV 📺"),
        SpecOption("SDTV", "SDTV 📺"),
        SpecOption("PPV", "PPV 🎫"),
        SpecOption("SATRip", "SATRip 📡"),
        SpecOption("DSR", "DSRip 📡"),
        SpecOption("TVRip", "TVRip 📺"),
        SpecOption("CAM", "CAM 📹"),
        SpecOption("TeleSync", "TeleSync 📹"),
        SpecOption("TS", "TS 🚫"),
        SpecOption("TC", "TeleCine 🎞️"),
        SpecOption("SCR", "SCR 📼"),
        SpecOption("DVDScr", "DVDScr 📼"),
        SpecOption("R5", "R5 ⁵"),
        SpecOption("VHS", "VHS 📼"),
        SpecOption("LaserDisc", "LaserDisc 💿")
    ),
    "codec" to listOf(
        SpecOption("av1", "AV1 🚀"),
        SpecOption(listOf("x265", "h.265", "hevc"), "HEVC ⚡"),
        SpecOption("vp9", "VP9 🧪"),
        SpecOption("vp8", "VP8 🧪"),
        SpecOption(listOf("x264", "h.264", "H264", "avc"), "H.264 📦"),
        SpecOption("vc-1", "VC-1 📼"),
        SpecOption("mpeg-2", "MPEG-2 🎞️"),
        SpecOption("mpeg-4", "MPEG-4 🎞️"),
        SpecOption("xvid", "XviD 🧩"),
        SpecOption("divx", "DivX 🧩"),
        SpecOption("wmv", "WMV 🪟"),
        SpecOption("theora", "Theora 🦦"),
        SpecOption("realvideo", "RealVideo 🎥"),
        SpecOption("h.263", "H.263 📱")
    ),
    "bitdepth" to listOf(
        SpecOption("12bit", "12bit 🌈"),
        SpecOption("10bit", "10bit 🎨"),
        SpecOption("Hi10P", "Hi10P (10bit) 🎨"),
        SpecOption("8bit", "8bit 🖍️"),
        SpecOption("3D", "3D 👓"),
        SpecOption("SBS", "3D SBS ↔️"),
        SpecOption("OU", "3D Over/Under ↕️"),
        SpecOption("IMAX", "IMAX 🏟️")
    ),
    "audio" to listOf(
        SpecOption("TrueHD", "Dolby TrueHD 🔊"),
        SpecOption("Atmos", "Dolby Atmos 🌌"),
        SpecOption(listOf("DDP5.1", "DDP 5.1"), "DD+ 5.1 🔉"),
        SpecOption("7.1", "7.1 Ch 🔊"),
        SpecOption("5.1", "5.1 Ch 🔉"),
        SpecOption("DTS-HD MA", "DTS-HD MA 🔊"),
        SpecOption("DTS-HD", "DTS-HD 🔊"),
        SpecOption("DTS:X", "DTS:X 🔊"),
        SpecOption("DTS Lossless", "DTS Lossless 🎼"),
        SpecOption("DTS-ES", "DTS-ES 🔉"),
        SpecOption("PCM", "LPCM/PCM 💿"),
        SpecOption("FLAC", "FLAC 🎹"),
        SpecOption("ALAC", "ALAC 🍏"),
        SpecOption("WAV", "WAV 🌊"),
        SpecOption("AIFF", "AIFF 🎼"),
        SpecOption(listOf("AAC2.0", "AAC 2.0"), "AAC 2.0 🎧"),
        SpecOption("DD2.0", "DD 2.0 🎧"),
        SpecOption(listOf("E-AC3", "DD+", "Dolby Digital Plus"), "DD+ 🔉"),
        SpecOption("AC3", "AC3 (Dolby Digital) 🔈"),
        SpecOption("DD5.1", "Dolby Digital 5.1 🔈"),
        SpecOption("DTS", "DTS 🔈"),
        SpecOption("AAC", "AAC 🎧"),
        SpecOption("HE-AAC", "HE-AAC 🎧"),
        SpecOption("OPUS", "Opus 🎙️"),
        SpecOption("VORBIS", "Vorbis 🌀"),
        SpecOption("MP3", "MP3 🎵"),
        SpecOption("WMA", "WMA 🎵"),
        SpecOption("OGG", "OGG 🌀"),
        SpecOption("MP2", "MP2 📻")
    ),
    "hdr" to listOf(
        SpecOption(listOf("DV", "DoVi", "DOLBYVISION", "Dolby Vision"), "Dolby Vision 👁️"),
        SpecOption("HDR10+", "HDR10+ 🔆"),
        SpecOption("HDR10", "HDR10 🔆"),
        SpecOption("HLG", "HLG 📡"),
        SpecOption("HDR", "HDR 🔆"),
        SpecOption("SDR", "SDR 🔅")
    ),
    "language" to listOf(
        SpecOption(listOf("HIN", "Hindi"), "Hindi 🇮🇳"),
        SpecOption("Tamil", "Tamil 🇮🇳"),
        SpecOption("Telugu", "Telugu 🇮🇳"),
        SpecOption("Malayalam", "Malayalam 🇮🇳"),
        SpecOption("Kannada", "Kannada 🇮🇳"),
        SpecOption("Bengali", "Bengali 🇮🇳"),
        SpecOption("Punjabi", "Punjabi 🇮🇳"),
        SpecOption(listOf("ENG", "English"), "English 🇺🇸"),
        SpecOption(listOf("KOR", "Korean"), "Korean 🇰🇷"),
        SpecOption(listOf("JPN", "Japanese"), "Japanese 🇯🇵"),
        SpecOption(listOf("CHN", "Chinese"), "Chinese 🇨🇳"),
        SpecOption("Spanish", "Spanish 🇪🇸"),
        SpecOption("French", "French 🇫🇷"),
        SpecOption("German", "German 🇩🇪"),
        SpecOption("Italian", "Italian 🇮🇹"),
        SpecOption("Russian", "Russian 🇷🇺"),
        SpecOption("Arabic", "Arabic 🇸🇦"),
        SpecOption(listOf("Multi-Audio", "Multi Audio", "Multi.Audio"), "Multi Audio 🌍"),
        SpecOption(listOf("Dual.Audio", "Dual Audio", "Dual"), "Dual Audio 🌗"),
        SpecOption(listOf("Multi-Sub", "MultiSub", "Multi Sub"), "Multi Subs 💬"),
        SpecOption("ESub", "English Subs 🇺🇸")
    )
)

private val SIZE_REGEX = """(\d+(?:\.\d+)?\s?(?:MB|GB))""".toRegex(RegexOption.IGNORE_CASE)
private val CATEGORY_ORDER = listOf("quality", "codec", "bitdepth", "audio", "hdr", "language")

fun getSimplifiedTitle(title: String): String {
    var remainingTitle = title
    val matchedLabels = mutableListOf<String>()

    CATEGORY_ORDER.forEach { category ->
        SPEC_OPTIONS[category].orEmpty().forEach { spec ->
            if (spec.regex.containsMatchIn(remainingTitle)) {
                matchedLabels.add(spec.label)
                remainingTitle = spec.regex.replace(remainingTitle, " ")
            }
        }
    }

    val sizeMatch = SIZE_REGEX.find(title)?.value?.uppercase()
    val size = sizeMatch?.let { "$it 💾" }

    val result = listOfNotNull(
        matchedLabels.distinct().joinToString(" | ").takeIf { it.isNotEmpty() },
        size
    ).joinToString(" | ")

    return if (result.isEmpty()) "" else "\n$result"
}

val languageMap = mapOf(
    "Afrikaans" to listOf("af", "afr"),
    "Albanian" to listOf("sq", "sqi"),
    "Amharic" to listOf("am", "amh"),
    "Arabic" to listOf("ar", "ara"),
    "Armenian" to listOf("hy", "hye"),
    "Azerbaijani" to listOf("az", "aze"),
    "Basque" to listOf("eu", "eus"),
    "Belarusian" to listOf("be", "bel"),
    "Bengali" to listOf("bn", "ben"),
    "Bosnian" to listOf("bs", "bos"),
    "Bulgarian" to listOf("bg", "bul"),
    "Catalan" to listOf("ca", "cat"),
    "Chinese" to listOf("zh", "zho"),
    "Croatian" to listOf("hr", "hrv"),
    "Czech" to listOf("cs", "ces"),
    "Danish" to listOf("da", "dan"),
    "Dutch" to listOf("nl", "nld"),
    "English" to listOf("en", "eng"),
    "Estonian" to listOf("et", "est"),
    "Filipino" to listOf("tl", "tgl", "fil"),
    "Finnish" to listOf("fi", "fin"),
    "French" to listOf("fr", "fra"),
    "Galician" to listOf("gl", "glg"),
    "Georgian" to listOf("ka", "kat"),
    "German" to listOf("de", "deu", "ger"),
    "Greek" to listOf("el", "ell"),
    "Gujarati" to listOf("gu", "guj"),
    "Hebrew" to listOf("he", "heb"),
    "Hindi" to listOf("hi", "hin"),
    "Hungarian" to listOf("hu", "hun"),
    "Icelandic" to listOf("is", "isl"),
    "Indonesian" to listOf("id", "ind"),
    "Italian" to listOf("it", "ita"),
    "Japanese" to listOf("ja", "jpn"),
    "Kannada" to listOf("kn", "kan"),
    "Kazakh" to listOf("kk", "kaz"),
    "Korean" to listOf("ko", "kor"),
    "Latvian" to listOf("lv", "lav"),
    "Lithuanian" to listOf("lt", "lit"),
    "Macedonian" to listOf("mk", "mkd"),
    "Malay" to listOf("ms", "msa"),
    "Malayalam" to listOf("ml", "mal"),
    "Maltese" to listOf("mt", "mlt"),
    "Marathi" to listOf("mr", "mar"),
    "Mongolian" to listOf("mn", "mon"),
    "Nepali" to listOf("ne", "nep"),
    "Norwegian" to listOf("no", "nor"),
    "Persian" to listOf("fa", "fas"),
    "Polish" to listOf("pl", "pol"),
    "Portuguese" to listOf("pt", "por"),
    "Punjabi" to listOf("pa", "pan"),
    "Romanian" to listOf("ro", "ron"),
    "Russian" to listOf("ru", "rus"),
    "Serbian" to listOf("sr", "srp"),
    "Sinhala" to listOf("si", "sin"),
    "Slovak" to listOf("sk", "slk"),
    "Slovenian" to listOf("sl", "slv"),
    "Spanish" to listOf("es", "spa"),
    "Swahili" to listOf("sw", "swa"),
    "Swedish" to listOf("sv", "swe"),
    "Tamil" to listOf("ta", "tam"),
    "Telugu" to listOf("te", "tel"),
    "Thai" to listOf("th", "tha"),
    "Turkish" to listOf("tr", "tur"),
    "Ukrainian" to listOf("uk", "ukr"),
    "Urdu" to listOf("ur", "urd"),
    "Uzbek" to listOf("uz", "uzb"),
    "Vietnamese" to listOf("vi", "vie"),
    "Welsh" to listOf("cy", "cym"),
    "Yiddish" to listOf("yi", "yid")
)

suspend fun mySubtitleCallback(
    lang: String? = null,
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    source: String? = null,
) {
    subtitleCallback.invoke(
        newSubtitleFile(
            getLanguage(lang) ?: lang ?: "Unknown",
            url
        )
    )
}

fun getLanguage(language: String?): String? {

    language ?: return null

    var normalizedLang = if(language.contains("-")) {
        language.substringBefore("-")
    } else if(language.contains(" ")) {
        language.substringBefore(" ")
    } else if(language.contains("CR_")) {
        language.substringAfter("CR_")
    } else {
        language
    }

    if(normalizedLang.isBlank()) {
        normalizedLang =  language
    }

    val tag = languageMap.entries.find { entry ->
        entry.value.contains(normalizedLang)
    }?.key

    if(tag == null) {
        return normalizedLang
    }
    return tag
}

fun String.getHost(): String {
    return fixTitle(URI(this).host.substringBeforeLast(".").substringAfterLast("."))
}

fun String.queryParams(): Map<String, String> {
    return split("&").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
        else null
    }.toMap()
}

fun JSONObject?.toStringMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    this?.keys()?.forEach { k -> map[k] = this.optString(k) }
    return map
}

suspend fun checkPosterAvailable(posterUrl: String? = null): String? {
    if(posterUrl == null) return null
    return try {
        val res = app.head(posterUrl)
        if (res.code == 200) {
            posterUrl
        } else {
            null
        }

    } catch (e: Exception) {
        null
    }
}

suspend fun getTvdbData(tvType: String, imdbId: String? = null): ExtractedMediaData? {
    if (imdbId == null) return null
    val primaryUrl = "https://aiometadata.elfhosted.com/stremio/9197a4a9-2f5b-4911-845e-8704c520bdf7/meta/$tvType/$imdbId.json"
    var jsonText = try {
        app.get(primaryUrl, timeout = 6L).text
    } catch (e: Exception) {
        ""
    }

    if (jsonText.isEmpty()) {
        val fallbackUrl = "https://94c8cb9f702d-tmdb-addon.baby-beamup.club/meta/$tvType/$imdbId.json"
        jsonText = try {
            app.get(fallbackUrl, timeout = 6L).text
        } catch (e: Exception) {
            ""
        }
    }

    if (jsonText.isEmpty()) return null

    val root = JSONObject(jsonText)
    val meta = root.optJSONObject("meta") ?: return null
    val image_proxy = "https://wsrv.nl/?url="
    val posterUrl = meta.optString("poster").takeIf { it.isNotEmpty() }?.let { "$image_proxy$it" }
    val backgroundUrl = meta.optString("background").takeIf { it.isNotEmpty() }?.let { "$image_proxy$it" }
    val logoUrl = meta.optString("logo").takeIf { it.isNotEmpty() }?.let { "$image_proxy$it" }

    val castArray = meta.optJSONObject("app_extras")?.optJSONArray("cast")
    val castList = if (castArray != null) {
        (0 until castArray.length()).mapNotNull { i ->
            val castMember = castArray.optJSONObject(i) ?: return@mapNotNull null

            val name = castMember.optString("name")
            if (name.isNotEmpty() && name != "null") {
                ActorData(
                    Actor(
                        name = name,
                        image = castMember.optString("photo")
                            .takeIf { it.isNotEmpty() && it != "null" }
                            ?.let { "$image_proxy$it" }
                    ),
                    roleString = castMember.optString("character")
                        .takeIf { it.isNotEmpty() && it != "null" }
                )
            } else null
        }
    } else null

    return ExtractedMediaData(castList, posterUrl, backgroundUrl, logoUrl)
}

fun buildMagnetString(stream: TorrentioStream): String {
    val trackersString = stream.sources
        ?.asSequence()
        ?.filter { it.startsWith("tracker:", ignoreCase = true) }
        ?.map { it.substringAfter("tracker:").trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?.joinToString("") { "&tr=$it" }
        ?: ""
    return "magnet:?xt=urn:btih:${stream.infoHash}&dn=${stream.infoHash}$trackersString&index=${stream.fileIdx}"
}

fun getFirstCharacterOrZero(input: String): String {
    val firstChar = input[0]
    return if (!firstChar.isLetter()) {
        "0"
    } else {
        firstChar.toString()
    }
}

fun getBaseUrl(url: String): String {
    try {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    } catch (e: Exception) {
        return url
    }
}

fun String?.createSlug(): String? {
    return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
        ?.trim()
        ?.replace("\\s+".toRegex(), "-")
        ?.lowercase()
}

fun quote(s: String): String =
    URLEncoder.encode(s, "UTF-8").replace("+", "%20")

fun String.capitalizeServer() = replaceFirstChar { it.uppercase() }

suspend fun extractMdrive(url: String): List<String> {
    val doc = app.get(url).document
    return doc.select("a")
        .mapNotNull { it.attr("href").takeIf { href ->
            href.contains(Regex("hubcloud|gdflix|gdlink", RegexOption.IGNORE_CASE))
        }}
}

fun getDate(): TmdbDate {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calendar = Calendar.getInstance()

    // Today
    val today = formatter.format(calendar.time)

    // Next week
    calendar.add(Calendar.WEEK_OF_YEAR, 1)
    val nextWeek = formatter.format(calendar.time)

    // Last week's Monday
    calendar.time = Date()
    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    calendar.add(Calendar.WEEK_OF_YEAR, -1)
    val lastWeekStart = formatter.format(calendar.time)

    // Start of current month
    calendar.time = Date()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val monthStart = formatter.format(calendar.time)

    return TmdbDate(today, nextWeek, lastWeekStart, monthStart)
}

fun isUpcoming(dateString: String?): Boolean {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
        unixTimeMS < dateTime
    } catch (t: Throwable) {
        //logError(t)
        false
    }
}

fun getUrlTitle(str: String?): String {
    if(str.isNullOrBlank()) return ""
    return str.replace(Regex("[^a-zA-Z\\d]"), "-")
}

suspend fun returnWorkingUrl(urls: List<String>): String? {
    for (url in urls) {
        try {
            val res = app.head(url, timeout = 30000L, allowRedirects = false)
            if (res.code == 200) {
                return url
            }
        } catch (e: Exception) {
            //logError(e)
            continue
        }
    }
    return null
}

suspend fun <T> retry(
    times: Int = 3,
    delayMs: Long = 1000,
    block: suspend () -> T?
): T? {

    repeat(times - 1) {
        runCatching {
            block()
        }.getOrNull()?.let {
            return it
        }

        delay(delayMs)
    }

    return runCatching {
        block()
    }.getOrNull()
}

fun getKisskhTitle(str: String?): String? {
    return str?.replace(Regex("[^a-zA-Z\\d]"), "-")
}


suspend fun <A, B> Iterable<A>.safeAmap(
    concurrency: Int = 7,
    f: suspend (A) -> B?
): List<B> = supervisorScope {
    val semaphore = Semaphore(concurrency)
    map { item ->
        async<B?>(Dispatchers.IO) {
            semaphore.withPermit {
                try {
                    f(item)
                } catch (e: CancellationException) {
                    if (!isActive) throw e
                    null
                } catch (e: Throwable) {
                    Log.e("safeAmap", "Item failed: $item — ${e.message}")
                    null
                }
            }
        }
    }.awaitAll().filterNotNull()
}

suspend fun runLimitedAsync(
    concurrency: Int = 7,
    vararg tasks: suspend () -> Unit
) = supervisorScope {
    val semaphore = Semaphore(concurrency)
    tasks.map { task ->
        async<Unit>(Dispatchers.IO) {
            semaphore.withPermit {
                try {
                    task()
                } catch (e: CancellationException) {
                    if (!isActive) throw e
                } catch (e: Throwable) {
                    Log.e("runLimitedAsync", "Task failed: ${e.message}")
                }
            }
        }
    }.awaitAll()
}

fun getEpisodeSlug(
    season: Int? = null,
    episode: Int? = null,
): Pair<String, String> {
    return if (season == null && episode == null) {
        "" to ""
    } else {
        (if (season!! < 10) "0$season" else "$season") to (if (episode!! < 10) "0$episode" else "$episode")
    }
}

fun getIndexQuality(str: String?): Int {
    if (str.isNullOrBlank()) return Qualities.Unknown.value

    Regex("""(\d{3,4})[pP]""").find(str)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it
    }

    val lowerStr = str.lowercase()
    return when {
        lowerStr.contains("8k") -> 4320
        lowerStr.contains("4k") -> 2160
        lowerStr.contains("2k") -> 1440
        else -> Qualities.Unknown.value
    }
}

//Dahmer
fun getIndexQualityTags(str: String?, fullTag: Boolean = false): String {
    return if (fullTag) Regex("(?i)(.*)\\.(?:mkv|mp4|avi)").find(str ?: "")?.groupValues?.get(1)
        ?.trim() ?: str ?: "" else Regex("(?i)\\d{3,4}[pP]\\.?(.*?)\\.(mkv|mp4|avi)").find(
        str ?: ""
    )?.groupValues?.getOrNull(1)
        ?.replace(".", " ")?.trim() ?: str ?: ""
}

suspend fun resolveFinalUrl(startUrl: String): String? {
    var currentUrl = startUrl
    var loopCount = 0
    val maxRedirects = 7

    while (loopCount < maxRedirects) {
        try {
            val res = app.head(currentUrl, allowRedirects = false, timeout = 2500L)
            if (res.code == 200 || res.code in 300..399) {
                val location = res.headers.get("Location")
                if(location.isNullOrEmpty()) break
                currentUrl = location
            } else {
                return null
            }
            loopCount++
        } catch (e: Exception) {
            return null
        }
    }
    return currentUrl
}

fun String.encodeUrl(): String {
    val url = URL(this)
    val uri = URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
    return uri.toURL().toString()
}

//Hindmoviez

val HindmoviezSECRET = base64Decode("NWU5NjA4NWM1NmUwZjU0ZWRhNjU3NzkwYWM1OGQxOWIyNzE0NzljNTA0MzY3ZmM5ZTZhNmMzM2YxZjgyNGU2Yg==")

fun hindmoviezbase64Url(input: String): String {
    return base64Encode(input.toByteArray())
        .replace("+", "-")
        .replace("/", "_")
        .replace("=", "")
}

fun hindmoviezhmacSha256(key: String, data: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    val secretKey = SecretKeySpec(key.toByteArray(), "HmacSHA256")
    mac.init(secretKey)
    return mac.doFinal(data.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .substring(0, 16)
}

fun hindmoviezsignHShare(rawId: String, domain: String): String {
    val t = System.currentTimeMillis() / 1000
    val encoded = hindmoviezbase64Url(rawId)
    val s = hindmoviezhmacSha256(HindmoviezSECRET, "$encoded|$t")
    return "$domain/r.php?d=${URLEncoder.encode(encoded, "UTF-8")}&t=$t&s=$s"
}

suspend fun getHindMoviezLinks(
    source: String,
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {
    val response = app.get(url)
    val doc = response.document
    val name = doc.select("div.container p:contains(Name:)").text().substringAfter("Name: ")
    val fileSize = doc.select("div.container p:contains(Size:)").text().substringAfter("Size: ")
    val simplifiedTitle = getSimplifiedTitle(name + fileSize)
    val link = doc.select("a.btn-info").attr("href")
    val document = app.get(link, timeout = 30000L).document

    document.select("a.button").safeAmap {
        val source = it.attr("href")

        callback.invoke(
            newExtractorLink(
                "Hindmoviez",
                "Hindmoviez $simplifiedTitle $fileSize",
                source,
                ExtractorLinkType.VIDEO
            ) {
                this.quality = getIndexQuality(name)
            }
        )

    }

}

//For Extractor new domain
suspend fun getLatestBaseUrl(baseUrl: String, source: String): String {
    return try {
        val dynamicUrls = app.get("https://raw.githubusercontent.com/SaurabhKaperwan/Utils/refs/heads/main/urls.json")
            .parsedSafe<Map<String, String>>()
        dynamicUrls?.get(source)?.takeIf { it.isNotBlank() } ?: baseUrl
    } catch (e: Exception) {
        baseUrl
    }
}


//Bold String
fun String.toSansSerifBold(): String {
    val builder = StringBuilder()
    for (char in this) {
        val codePoint = when (char) {
            // Mathematical Sans-Serif Bold ranges
            in 'A'..'Z' -> 0x1D5D4 + (char - 'A')
            in 'a'..'z' -> 0x1D5EE + (char - 'a')
            in '0'..'9' -> 0x1D7EC + (char - '0')
            else -> char.code
        }
        builder.append(Character.toChars(codePoint))
    }
    return builder.toString()
}

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
    size: String = ""
) = supervisorScope {
    val processLink: (ExtractorLink) -> Unit = { link ->
        launch(Dispatchers.IO) {
            val isDownload = link.source.contains("Download", ignoreCase = true) ||
                             link.url.contains("video-downloads.googleusercontent")
            val simplifiedTitle = getSimplifiedTitle(link.name)
            val combined = if (source.contains("(Combined)")) " (Combined)" else ""
            val fixSize = if (size.isNotEmpty()) " $size" else ""
            val sourceBold = "$source [${link.source}]".toSansSerifBold()
            val newSourceName = if (isDownload) "Download$combined" else "${link.source}$combined"
            val newName = "$sourceBold $simplifiedTitle$fixSize".trim()

            val newLink = newExtractorLink(
                newSourceName,
                newName,
                link.url,
                type = link.type
            ) {
                this.referer = link.referer
                this.quality = quality ?: link.quality
                this.headers = link.headers
                this.extractorData = link.extractorData
            }

            callback(newLink)
        }
    }
