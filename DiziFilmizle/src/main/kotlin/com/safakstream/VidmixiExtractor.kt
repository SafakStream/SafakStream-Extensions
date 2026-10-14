package com.safakstream

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VidmixiExtractor : ExtractorApi() {
    override var name = "Vidmixi"
    override var mainUrl = "https://vidmixi.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Vidmixi", "--> getUrl: $url")
        val embedPage = app.get(url, referer = referer).text

        val bePlayerRegex = """bePlayer\(\s*'([^']+)'\s*,\s*'([^']+)'""".toRegex()
        val match = bePlayerRegex.find(embedPage)
        if (match == null) {
            Log.e("Vidmixi", "bePlayer bulunamadi")
            return
        }
        val hash = match.groupValues[1]
        val setJson = match.groupValues[2]

        val decrypted = try {
            decryptAes(setJson, hash)
        } catch (e: Exception) {
            Log.e("Vidmixi", "decrypt hatasi: ${e.message}")
            return
        }

        Log.d("Vidmixi", "--> decrypted: $decrypted")

        val json = try { JSONObject(decrypted) } catch (e: Exception) {
            Log.e("Vidmixi", "JSON parse hatasi: ${e.message}")
            return
        }
        val videoLocation = json.optString("video_location", "")
        if (videoLocation.isBlank()) {
            Log.e("Vidmixi", "video_location bos")
            return
        }
        Log.d("Vidmixi", "--> video_location: $videoLocation")

        val m3u8Resp = app.get(videoLocation, referer = "https://dizifilmizle.to/").text
        Log.d("Vidmixi", "--> m3u8Resp: ${m3u8Resp.take(200)}")

        val lines = m3u8Resp.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val nameMatch = """NAME="([^"]+)"""".toRegex().find(line)
                val resMatch = """RESOLUTION=(\d+)x(\d+)""".toRegex().find(line)
                val qualityName = nameMatch?.groupValues?.get(1) ?: "Auto"
                val height = resMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

                if (i + 1 < lines.size) {
                    val streamUrl = lines[i + 1].trim()
                    if (streamUrl.startsWith("http")) {
                        Log.d("Vidmixi", "--> stream: $qualityName $streamUrl")
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = "$name ($qualityName)",
                                url = streamUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                headers = mapOf(
                                    "Referer" to "https://vidmixi.com/",
                                    "Origin" to "https://vidmixi.com"
                                )
                                quality = heightToQuality(height)
                            }
                        )
                    }
                }
            }
            i++
        }
    }

    private fun heightToQuality(h: Int): Int = when {
        h >= 2160 -> Qualities.P2160.value
        h >= 1440 -> Qualities.P1440.value
        h >= 1080 -> Qualities.P1080.value
        h >= 720 -> Qualities.P720.value
        h >= 480 -> Qualities.P480.value
        h >= 360 -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }

    private fun decryptAes(setJson: String, password: String): String {
        val j = JSONObject(setJson)
        val ct = Base64.decode(j.getString("ct"), Base64.DEFAULT)
        val salt = hexToBytes(j.getString("s"))
        val ivHex = j.optString("iv", "")
        val full = evpFull(password.toByteArray(Charsets.UTF_8), salt)
        val key = full.copyOfRange(0, 32)
        val iv = if (ivHex.isNotBlank()) hexToBytes(ivHex) else full.copyOfRange(32, 48)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val plain = cipher.doFinal(ct)
        return String(plain, Charsets.UTF_8)
    }

    private fun evpFull(password: ByteArray, salt: ByteArray): ByteArray {
        val dt = ByteArray(48)
        var d = ByteArray(0)
        var idx = 0
        while (idx < 48) {
            val md = MessageDigest.getInstance("MD5")
            md.update(d)
            md.update(password)
            md.update(salt)
            d = md.digest()
            val copyLen = minOf(d.size, 48 - idx)
            System.arraycopy(d, 0, dt, idx, copyLen)
            idx += copyLen
        }
        return dt
    }

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            out[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}
