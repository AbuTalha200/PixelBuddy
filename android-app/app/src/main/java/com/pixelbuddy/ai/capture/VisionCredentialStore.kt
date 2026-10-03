package com.pixelbuddy.ai.capture

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VisionCredentialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("vision_credentials", Context.MODE_PRIVATE)

    fun save(apiKey: String) {
        val value = apiKey.trim()
        require(value.isNotEmpty()) { "An API key is required" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        preferences.edit()
            .putString("key_data", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString("key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun read(): String? {
        val data = preferences.getString("key_data", null) ?: return null
        val iv = preferences.getString("key_iv", null) ?: return null

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            preferences.edit().clear().apply()
            null
        }
    }

    fun hasKey(): Boolean = !read().isNullOrBlank()

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val saved = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (saved != null) return saved

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "pixelbuddy_gemini_vision_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}