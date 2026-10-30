package com.mivisor.tv.warp

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.mivisor.tv.services.WarpProtocol
import java.security.MessageDigest

/**
 * Comprobación de firma por HUELLA SHA-256 del certificado (no por el CN, que cualquiera puede copiar).
 * MiVisor solo puede controlar WARP si las dos apps están firmadas con el mismo certificado: Android concede entonces
 * el permiso "signature" com.mivisor.permission.CONTROL_WARP. Aquí solo se informa; la seguridad la impone Android.
 */
object WarpSignature {
    class Report(
        /** Huella de MiVisor TV (AA:BB:…), o null si no se pudo leer. */
        val own: String?,
        /** Huella de WARP, o null si no está instalada o no se pudo leer. */
        val warp: String?,
        val permissionGranted: Boolean
    ) {
        val sameSigner: Boolean get() = own != null && own == warp
    }

    fun report(context: Context): Report {
        val app = context.applicationContext
        val granted = app.checkCallingOrSelfPermission(WarpProtocol.PERMISSION_CONTROL_WARP) == PackageManager.PERMISSION_GRANTED
        return Report(fingerprint(app, app.packageName), fingerprint(app, WarpProtocol.WARP_PACKAGE), granted)
    }

    fun fingerprint(context: Context, packageName: String): String? = runCatching {
        signatures(context.packageManager, packageName).firstOrNull()?.let { sha256(it.toByteArray()) }
    }.getOrNull()

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it.toInt() and 0xff) }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun signatures(pm: PackageManager, pkg: String): List<Signature> =
        if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return emptyList()
            (if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory)?.toList().orEmpty()
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures?.toList().orEmpty()
        }
}
