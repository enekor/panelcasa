package com.n3k0chan.panelcasa

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Muestra las métricas y permite exportarlas a un fichero accesible por USB. */
class DiagnosticsActivity : Activity() {
    private lateinit var app: PanelApp
    private lateinit var body: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as PanelApp
        val root = Ui.column(this)
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Ui.button(this@DiagnosticsActivity, "Actualizar") { refresh() })
            addView(Ui.button(this@DiagnosticsActivity, "Exportar informe") {
                val f = runCatching { app.diagnostics.export() }.getOrNull()
                Toast.makeText(
                    this@DiagnosticsActivity,
                    if (f != null) "Guardado en ${f.absolutePath}" else "No se pudo guardar el informe",
                    Toast.LENGTH_LONG,
                ).show()
                refresh()
            })
        }
        root.addView(buttons)
        body = Ui.text(this, "", 15f, Ui.TEXT).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        root.addView(body)
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    private fun refresh() {
        body.text = app.diagnostics.report()
    }
}
