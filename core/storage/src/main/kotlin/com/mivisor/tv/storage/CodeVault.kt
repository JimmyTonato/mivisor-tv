package com.mivisor.tv.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra los códigos de proveedor con una clave AES-256-GCM del Android Keystore (no extraíble, propia de este
 * aparato). Si el Keystore de una TV antigua falla, el código se guarda igualmente en el almacenamiento privado de la
 * app (marcado como "p1") para no perder la configuración; el registro lo indica sin mostrar el código.
 */
class CodeVault {
    fun seal(plain: String): String = try {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        "k1:" + Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.w(TAG, "Keystore no disponible (${e.javaClass.simpleName}): código guardado sin cifrar en el almacenamiento privado")
        "p1:" + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun open(sealed: String): String? = runCatching {
        when {
            sealed.startsWith("k1:") -> {
                val all = Base64.decode(sealed.substring(3), Base64.NO_WRAP)
                val c = Cipher.getInstance(TRANSFORMATION)
                c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 0, IV_BYTES))
                String(c.doFinal(all, IV_BYTES, all.size - IV_BYTES), Charsets.UTF_8)
            }
            sealed.startsWith("p1:") -> String(Base64.decode(sealed.substring(3), Base64.NO_WRAP), Charsets.UTF_8)
            else -> null
        }
    }.getOrNull()

    @Synchronized
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
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
        const val TAG = "MiVisorTV/Vault"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "mivisor_tv_provider_codes"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
