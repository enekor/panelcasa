package com.n3k0chan.panelcasa

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout

/**
 * Columna de Google Home. Solo se instancia detrás de [VersionGate] (Android 10 o posterior).
 * Por ahora es una columna de pruebas vacía para validar los dos diseños de pantalla;
 * el módulo de Home APIs se conectará aquí en la fase 5.
 */
class HomeColumn(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        tag = TAG
        setBackgroundColor(0xFF12171C.toInt())
        addView(Ui.text(context, "Casa", 22f, Ui.TEXT))
        addView(Ui.text(context, "Columna de pruebas: los dispositivos de Google Home se añadirán en la fase 5.", 15f).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), 0)
        })
    }

    companion object {
        const val TAG = "home-column"
    }
}
