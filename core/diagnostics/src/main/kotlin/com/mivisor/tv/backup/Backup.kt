package com.mivisor.tv.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Copia de MiVisor TV (3d): proveedores (con su código), favoritos, recordatorios y ajustes.
 * NO lleva historial, registros ni lo aprendido de las fuentes (eso es de cada tele).
 */
data class BackupData(
    val version: Int = 1,
    val createdAt: Long = 0,
    val app: String = "MiVisor TV",
    val providers: List<BackupProvider> = emptyList(),
    val favorites: List<BackupFavorite> = emptyList(),
    val reminders: List<BackupReminder> = emptyList(),
    val settings: Map<String, String> = emptyMap()
) {
    fun summary(): String = listOf(
        plural(providers.size, "proveedor", "proveedores"),
        plural(favorites.size, "favorito", "favoritos"),
        plural(reminders.size, "recordatorio", "recordatorios")
    ).joinToString(", ") + if (settings.isNotEmpty()) " y tus ajustes" else ""

    private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
}

data class BackupProvider(val name: String, val code: String, val usesWarp: Boolean = false)
data class BackupFavorite(val channelId: String, val name: String)
data class BackupReminder(
    val eventId: String, val title: String, val startMillis: Long, val sport: String,
    val competition: String? = null, val channels: List<String> = emptyList()
)

/**
 * Fichero .mvb: «MVB1» + iteraciones (4 bytes) + sal (16) + IV (12) + JSON cifrado con AES-256-GCM.
 * La clave sale del PIN con PBKDF2 (HMAC-SHA1, disponible en todos los Android) y una sal aleatoria.
 * Sin el PIN no se puede abrir; un PIN equivocado o un fichero dañado se detectan (GCM autentica).
 */
object BackupCodec {
    private val MAGIC = "MVB1".toByteArray(Charsets.US_ASCII)
    const val ITERATIONS = 120_000
    private const val SALT = 16
    private const val IV = 12
    private val random = SecureRandom()
    private val gson: Gson = GsonBuilder().create()

    /** PIN de 6 cifras (100000–999999), como en el diseño. */
    fun newPin(): String = (100_000 + random.nextInt(900_000)).toString()

    fun toJson(data: BackupData): String = gson.toJson(data)

    fun fromJson(json: String): BackupData? = runCatching { gson.fromJson(json, BackupData::class.java) }.getOrNull()
        ?.takeIf { it.app == "MiVisor TV" && it.version in 1..1 }

    fun encrypt(data: BackupData, pin: String, iterations: Int = ITERATIONS): ByteArray {
        val salt = ByteArray(SALT).also(random::nextBytes)
        val iv = ByteArray(IV).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(pin, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val ct = cipher.doFinal(toJson(data).toByteArray(Charsets.UTF_8))
        return MAGIC + int(iterations) + salt + iv + ct
    }

    sealed class Result {
        class Ok(val data: BackupData) : Result()
        object WrongPin : Result()
        object NotABackup : Result()
    }

    fun decrypt(bytes: ByteArray, pin: String): Result {
        if (bytes.size < 4 + 4 + SALT + IV + 16 || !bytes.copyOfRange(0, 4).contentEquals(MAGIC)) return Result.NotABackup
        val iterations = ((bytes[4].toInt() and 0xff) shl 24) or ((bytes[5].toInt() and 0xff) shl 16) or
            ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
        if (iterations !in 1_000..2_000_000) return Result.NotABackup
        val salt = bytes.copyOfRange(8, 8 + SALT)
        val iv = bytes.copyOfRange(8 + SALT, 8 + SALT + IV)
        val ct = bytes.copyOfRange(8 + SALT + IV, bytes.size)
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(pin.filter { it.isDigit() }, salt, iterations), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)
            cipher.doFinal(ct)
        } catch (e: Exception) {
            return Result.WrongPin                       // GCM: PIN equivocado o fichero alterado
        }
        val data = fromJson(String(plain, Charsets.UTF_8)) ?: return Result.NotABackup
        return Result.Ok(data)
    }

    private fun key(pin: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    private fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    /** «mivisor-copia-2026-10-30.mvb». */
    fun fileName(date: String) = "mivisor-copia-$date.mvb"
}
