package com.safakstream

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SelcukFlix sitesinin secureData ve API response şifreleme algoritmasını çözer.
 *
 * Algoritma:
 *   - Key: SHA256("!!22xx!!90!!") -> base64 -> ilk 32 karakter
 *   - IV:  16 byte sıfır
 *   - Cipher: AES-256-CBC
 *   - Input: base64 encoded string
 *   - Output: JSON string
 *
 * Kaynak: app.js modül 379 içindeki 'h' fonksiyonu
 */
object SelcukFlixDecryptor {

    private const val SECRET_PHRASE = "!!22xx!!90!!"

    private val key: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(SECRET_PHRASE.toByteArray(Charsets.UTF_8))
        val b64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        b64.take(32)
    }

    private val iv: ByteArray = ByteArray(16) { 0 }

    /**
     * secureData (sayfa içi) veya API response (arama) şifreli metnini çözer.
     * @return çözülmüş JSON string veya hata durumunda boş string
     */
    fun decryptSecureData(encryptedBase64: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

            val decoded = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val decrypted = cipher.doFinal(decoded)
            String(decrypted, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            Log.e("SelcukFlixDecryptor", "decryptSecureData hata: ${e.message}")
            ""
        }
    }

    /**
     * Arama API'sinden dönen {"response": "..."} JSON'unu açar.
     * @return çözülmüş JSON string veya hata durumunda boş string
     */
    fun decryptResponse(apiJson: String): String {
        return try {
            val json = JSONObject(apiJson)
            val response = json.optString("response", "")
            if (response.isEmpty()) return ""
            decryptSecureData(response)
        } catch (e: Exception) {
            Log.e("SelcukFlixDecryptor", "decryptResponse hata: ${e.message}")
            ""
        }
    }
}
