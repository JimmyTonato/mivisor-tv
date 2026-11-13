package com.mivisor.tv.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 4f · Contraseña de la Biblioteca de MiVisor Gold, cifrada en el aparato con una clave del Android Keystore
 * (AES-GCM; la clave no sale nunca del Keystore). En las preferencias solo hay el texto cifrado.
 * La contraseña no está en el código, no se escribe en el registro y no va en la copia cifrada de ajustes.
 */
class GoldPasswordStore(context: Context) {
    private val prefs = context.getSharedPreferences("gold_biblioteca", Context.MODE_PRIVATE)

    val isSet: Boolean get() = prefs.getString(KEY, null) != null

    fun save(password: CharArray) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = String(password).toByteArray(Charsets.UTF_8)
        val ct = cipher.doFinal(bytes)
        bytes.fill(0)
        val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
        prefs.edit().putString(KEY, blob).apply()
    }

    /** La contraseña, o null si no hay o ya no se puede descifrar (Keystore borrado): entonces hay que escribirla otra vez. */
    fun load(): CharArray? = runCatching {
        val blob = prefs.getString(KEY, null) ?: return null
        val iv = Base64.decode(blob.substringBefore(':'), Base64.NO_WRAP)
        val ct = Base64.decode(blob.substringAfter(':'), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(ct)
        String(plain, Charsets.UTF_8).toCharArray().also { plain.fill(0) }
    }.getOrNull()

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "mivisor_gold_biblioteca"
        const val KEY = "password"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
