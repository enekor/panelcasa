package com.n3k0chan.panelcasa

import android.os.Build

/**
 * Puerta de versión, regla única del proyecto: la columna de Google Home existe solo con Android 10 (API 29) o posterior.
 * Por debajo no se crea la vista, no se carga ninguna clase de las Home APIs y no se pide nada a Google.
 */
object VersionGate {
    const val HOME_MIN_SDK = 29

    /**
     * La integración con Google Home (fase 5) está aplazada. Mientras sea false, la columna de casa
     * no se crea en ninguna versión de Android. Cambiar a true al conectar las Home APIs.
     */
    const val HOME_MODULE_READY = false

    fun homeEnabled(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= HOME_MIN_SDK

    /** La columna se muestra solo si el dispositivo lo permite y el módulo está listo. */
    fun homeColumnVisible(sdk: Int = Build.VERSION.SDK_INT, moduleReady: Boolean = HOME_MODULE_READY): Boolean =
        moduleReady && homeEnabled(sdk)
}
