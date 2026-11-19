package com.n3k0chan.panelcasa

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast

/** App con icono de inicio. [key] = componente aplanado («paquete/clase»). */
class LaunchableApp(val key: String, val label: String, val icon: Drawable)

/** Consulta las apps instaladas que se pueden abrir, sin QUERY_ALL_PACKAGES. */
class Apps(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun all(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                val cn = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                LaunchableApp(cn.flattenToString(), it.loadLabel(pm).toString(), it.loadIcon(pm))
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    /** Apps fijadas en el orden guardado; las desinstaladas desaparecen sin error. */
    fun pinned(keys: List<String>): List<LaunchableApp> {
        val byKey = all().associateBy { it.key }
        return keys.mapNotNull { byKey[it] }
    }

    /** Primera vez: se fijan los Ajustes de Android si existen. */
    fun defaultPinned(): List<String> = all().filter { it.key.startsWith("com.android.settings/") }.map { it.key }.take(1)

    fun launch(key: String) {
        val cn = ComponentName.unflattenFromString(key) ?: return
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "La app ya no está instalada", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "No se puede abrir la app", Toast.LENGTH_SHORT).show()
        }
    }
}
