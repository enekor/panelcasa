package com.n3k0chan.panelcasa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Refuerzo del arranque automático. Lo principal es que la app sea la pantalla de inicio predeterminada;
 * esto cubre el caso de que no lo sea. En Android 10+ el sistema no deja abrir actividades desde aquí.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val start = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(start) }
    }
}
