package com.mivisor.tv.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Apps externas (3b):
 *  - Ace Stream: la app oficial abre `acestream://<Content ID>` (el mismo Intent que la v18). MiVisor TV no la
 *    controla: se abre fuera y, al pulsar Atrás en ella, se vuelve a MiVisor TV.
 *  - Enlaces de los proveedores que no son Ace Stream (magnet, Telegram, TVGram, webs): con la app que Android elija.
 */
object ExternalApps {
    fun aceIntent(contentId: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("acestream://" + contentId.trim().lowercase())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Apps instaladas que abren acestream:// (sin contar MiVisor TV). */
    fun aceHandlers(context: Context): List<String> = runCatching {
        context.packageManager.queryIntentActivities(aceIntent("0".repeat(40)), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
    }.getOrDefault(emptyList())

    fun aceInstalled(context: Context): Boolean = aceHandlers(context).isNotEmpty()

    /** Abre el Content ID en Ace Stream. Devuelve null si va bien o el motivo. */
    fun openInAce(context: Context, contentId: String): String? = try {
        context.startActivity(aceIntent(contentId))
        null
    } catch (e: ActivityNotFoundException) {
        "Ace Stream no está instalada en esta tele"
    } catch (e: SecurityException) {
        "Ace Stream no permite abrir el enlace"
    }

    /** Abre un enlace externo del proveedor. Devuelve null si va bien o el motivo. */
    fun openLink(context: Context, url: String): String? = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.trim())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (e: ActivityNotFoundException) {
        "No hay ninguna app en esta tele que abra este tipo de enlace"
    } catch (e: SecurityException) {
        "La app no permite abrir este enlace"
    }
}
