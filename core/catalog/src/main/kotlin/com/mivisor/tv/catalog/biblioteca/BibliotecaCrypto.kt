package com.mivisor.tv.catalog.biblioteca

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 4f · Descifrado de los ficheros `.enc` de la Biblioteca de MiVisor Gold.
 *
 * Formato: "MVB1" (4 bytes) + sal (16) + nonce (12) + texto cifrado con la etiqueta GCM de 16 bytes al final.
 * Clave = PBKDF2WithHmacSHA256(contraseña UTF-8, sal, iteraciones del manifest, 256 bits). AES/GCM/NoPadding.
 * Lo descifrado es JSON Lines comprimido con gzip.
 *
 * La contraseña nunca se guarda aquí: llega de quien llama. Las claves derivadas se recuerdan en memoria (por sal),
 * porque derivar con 200.000 iteraciones cuesta 1–3 s en una tele.
 */
object BibliotecaCrypto {
    private val MAGIC = byteArrayOf('M'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    private const val SALT = 16
    private const val NONCE = 12
    private const val TAG_BITS = 128
    /** Tope de lo descomprimido (una biblioteca de miles de IDs ocupa unos pocos MB). */
    private const val MAX_PLAIN = 64 * 1024 * 1024

    /** Contraseña incorrecta (o fichero manipulado): la etiqueta GCM no cuadra. */
    class WrongPasswordException : Exception("Contraseña de la Biblioteca incorrecta")

    /** El fichero no tiene el formato esperado. */
    class FormatException(msg: String) : Exception(msg)

    private val keys = ConcurrentHashMap<String, ByteArray>()

    /** Descifra y descomprime: el texto JSON Lines (UTF-8). */
    fun decrypt(data: ByteArray, password: CharArray, iterations: Int): String {
        if (data.size < MAGIC.size + SALT + NONCE + TAG_BITS / 8) throw FormatException("fichero demasiado corto")
        if (!data.copyOfRange(0, 4).contentEquals(MAGIC)) throw FormatException("no empieza por MVB1")
        if (iterations !in 1_000..10_000_000) throw FormatException("iteraciones fuera de rango")
        val salt = data.copyOfRange(4, 4 + SALT)
        val nonce = data.copyOfRange(4 + SALT, 4 + SALT + NONCE)
        val key = keyFor(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val zipped = try {
            cipher.doFinal(data, 4 + SALT + NONCE, data.size - 4 - SALT - NONCE)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
        return gunzip(zipped)
    }

    /** Solo para pruebas y herramientas: cifra con el mismo formato. */
    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int, salt: ByteArray, nonce: ByteArray): ByteArray {
        require(salt.size == SALT && nonce.size == NONCE)
        val key = derive(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return MAGIC + salt + nonce + cipher.doFinal(plain)
    }

    /** Olvida las claves derivadas (al cambiar u olvidar la contraseña). */
    fun forgetKeys() = keys.clear()

    private fun keyFor(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        // La entrada de la memoria no guarda la contraseña: solo una huella con la sal y las iteraciones.
        val id = MessageDigest.getInstance("SHA-256").run {
            update(String(password).toByteArray(Charsets.UTF_8))
            update(salt)
            update(iterations.toString().toByteArray())
            digest()
        }.joinToString("") { "%02x".format(it) }
        return keys.getOrPut(id) { derive(password, salt, iterations) }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun gunzip(zipped: ByteArray): String {
        val out = ByteArrayOutputStream()
        try {
            GZIPInputStream(ByteArrayInputStream(zipped)).use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_PLAIN) throw FormatException("demasiado grande al descomprimir")
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw FormatException("no es gzip")
        }
        return out.toString("UTF-8")
    }
}
