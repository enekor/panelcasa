package com.n3k0chan.panelcasa

import android.os.Build

/**
 * Puerta de versión, regla única del proyecto: la columna de Google Home existe solo con Android 10 (API 29) o posterior.
 * Por debajo no se crea la vista, no se carga ninguna clase de las Home APIs y no se pide nada a Google.
 */
object VersionGate {
    const val HOME_MIN_SDK = 29

    fun homeEnabled(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= HOME_MIN_SDK
}
