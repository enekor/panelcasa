package com.n3k0chan.panelcasa

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Receptor de administración del dispositivo. Solo pide la política «force-lock» (bloquear la pantalla);
 * no puede borrar datos, cambiar contraseñas ni nada más.
 */
class LockAdminReceiver : DeviceAdminReceiver()

/** Botón de bloqueo del panel lateral. La primera vez pide activar el permiso de bloqueo. */
object ScreenLock {
    fun component(context: Context) = ComponentName(context, LockAdminReceiver::class.java)

    private fun dpm(context: Context) = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isEnabled(context: Context): Boolean = dpm(context).isAdminActive(component(context))

    /** Bloquea la pantalla, o pide el permiso si aún no está concedido. */
    fun lockOrAsk(activity: Activity) {
        if (isEnabled(activity)) {
            dpm(activity).lockNow()
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(activity))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Solo para que el botón del candado pueda apagar y bloquear la pantalla. Se puede quitar desde los ajustes del panel.",
            )
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, "Este dispositivo no permite bloquear la pantalla desde una app", Toast.LENGTH_LONG).show()
        }
    }

    /** Retira el permiso (necesario antes de desinstalar la app). */
    fun disable(context: Context) {
        if (isEnabled(context)) dpm(context).removeActiveAdmin(component(context))
    }
}
