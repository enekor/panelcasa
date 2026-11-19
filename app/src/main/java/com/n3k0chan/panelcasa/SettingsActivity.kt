package com.n3k0chan.panelcasa

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Toast
import com.n3k0chan.panelcasa.renfe.DisplayMode

/** Ajustes nativos. Se abre desde la rueda dentada del panel lateral. */
class SettingsActivity : Activity() {
    private lateinit var app: PanelApp
    private lateinit var stationRow: LinearLayout
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as PanelApp
        content = Ui.column(this)
        setContentView(ScrollView(this).apply { addView(content) })
        build()
    }

    override fun onResume() {
        super.onResume()
        // La estación puede haber cambiado en el buscador.
        content.removeAllViews()
        build()
    }

    private fun build() {
        val p = app.prefs
        val c = content

        // Estación
        c.addView(Ui.title(this, "Estación de Cercanías"))
        val st = app.engine.currentStation
        stationRow = Ui.row(
            this,
            st?.name ?: "Sin estación elegida",
            st?.let { it.networks.joinToString(", ") + " · " + it.lines.joinToString(", ") + " · Toca para cambiar" } ?: "Toca para elegir",
        ) { startActivity(Intent(this, StationPickerActivity::class.java)) }
        c.addView(stationRow)
        c.addView(Ui.text(this, app.engine.dataStatus(), 15f).apply {
            setPadding(Ui.dp(this@SettingsActivity, 12), 0, Ui.dp(this@SettingsActivity, 12), 0)
        })

        // Presentación
        c.addView(Ui.title(this, "Presentación de los trenes"))
        val modes = listOf(
            DisplayMode.AUTO to "Automática: por sentidos en estaciones de paso, lista única en nudos y cabeceras",
            DisplayMode.A to "Siempre un bloque por sentido (2 trenes en cada uno)",
            DisplayMode.B to "Siempre una lista única (4 trenes)",
        )
        c.addView(radioGroup(modes.map { it.second }, modes.indexOfFirst { it.first == p.displayMode }) { i ->
            p.displayMode = modes[i].first
            app.engine.republish()
        })

        // Ubicación de la meteo
        c.addView(Ui.title(this, "Ubicación del tiempo"))
        val manual = p.manualCoords
        val lat = coordField("Latitud", manual?.first)
        val lon = coordField("Longitud", manual?.second)
        val coordsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(lat, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(lon, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.button(this@SettingsActivity, "Guardar") {
                val la = lat.text.toString().replace(',', '.').toDoubleOrNull()
                val lo = lon.text.toString().replace(',', '.').toDoubleOrNull()
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                    Toast.makeText(this@SettingsActivity, "Coordenadas no válidas", Toast.LENGTH_SHORT).show()
                } else {
                    p.manualCoords = la to lo
                    app.engine.locationChanged()
                    Toast.makeText(this@SettingsActivity, "Guardado", Toast.LENGTH_SHORT).show()
                }
            })
            visibility = if (manual != null) ViewGroup.VISIBLE else ViewGroup.GONE
        }
        c.addView(radioGroup(listOf("Coordenadas de la estación elegida", "Coordenadas escritas a mano"), if (manual != null) 1 else 0) { i ->
            if (i == 0) {
                p.manualCoords = null
                coordsRow.visibility = ViewGroup.GONE
                app.engine.locationChanged()
            } else {
                coordsRow.visibility = ViewGroup.VISIBLE
            }
        })
        c.addView(coordsRow)

        // Panel lateral
        c.addView(Ui.title(this, "Panel lateral"))
        c.addView(Ui.row(this, getString(R.string.pinned_apps), "${p.pinnedApps.size} fijadas · Toca para elegir y ordenar") {
            startActivity(Intent(this, PinnedAppsActivity::class.java))
        })

        // Pantalla
        c.addView(Ui.title(this, "Pantalla"))
        val from = hourPicker(p.nightFrom) { p.nightFrom = it }
        val to = hourPicker(p.nightTo) { p.nightTo = it }
        c.addView(CheckBox(this).apply {
            text = "Oscurecer la pantalla por la noche (se ilumina al tocar)"
            textSize = 17f
            isChecked = p.nightEnabled
            setOnCheckedChangeListener { _, checked -> p.nightEnabled = checked }
        })
        c.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Ui.text(this@SettingsActivity, "Desde las "))
            addView(from)
            addView(Ui.text(this@SettingsActivity, "  hasta las "))
            addView(to)
        })
        val brightnessLabel = Ui.text(this, brightnessText(p.brightness))
        c.addView(brightnessLabel)
        c.addView(SeekBar(this).apply {
            max = 100
            progress = if (p.brightness < 0) 0 else p.brightness
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    p.brightness = if (value == 0) -1 else value
                    brightnessLabel.text = brightnessText(p.brightness)
                }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        })

        // Sistema
        c.addView(Ui.title(this, "Sistema"))
        c.addView(Ui.row(this, getString(R.string.diagnostics), "Memoria, tiempos, últimas descargas y errores; exportar informe") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        })
        c.addView(Ui.row(this, "Ajustes de Android", null) { open(Intent(Settings.ACTION_SETTINGS)) })
        c.addView(Ui.row(this, "Cambiar la pantalla de inicio", "Para volver al launcher original de la tablet") {
            open(Intent(Settings.ACTION_HOME_SETTINGS))
        })
        if (ScreenLock.isEnabled(this)) {
            c.addView(Ui.row(this, "Quitar el permiso de bloqueo", "Necesario antes de desinstalar la app. El candado lo volverá a pedir si se pulsa.") {
                ScreenLock.disable(this)
                Toast.makeText(this, "Permiso de bloqueo retirado", Toast.LENGTH_SHORT).show()
                content.removeAllViews()
                build()
            })
        }
        c.addView(Ui.text(this, "Datos: Renfe (CC BY 4.0) · MET Norway (CC BY 4.0) · Versión ${versionName()}", 14f).apply {
            setPadding(0, Ui.dp(this@SettingsActivity, 24), 0, 0)
        })
    }

    private fun brightnessText(v: Int) = if (v < 0) "Brillo del panel: el del sistema" else "Brillo del panel: $v %"

    private fun radioGroup(options: List<String>, selected: Int, onSelect: (Int) -> Unit): RadioGroup = RadioGroup(this).apply {
        options.forEachIndexed { i, label ->
            addView(RadioButton(this@SettingsActivity).apply {
                id = 1000 + i
                text = label
                textSize = 17f
                minHeight = Ui.dp(this@SettingsActivity, 48)
            })
        }
        if (selected >= 0) check(1000 + selected)
        setOnCheckedChangeListener { _, id -> onSelect(id - 1000) }
    }

    private fun coordField(hint: String, value: Double?): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        setText(value?.toString() ?: "")
    }

    private fun hourPicker(value: Int, onChange: (Int) -> Unit): NumberPicker = NumberPicker(this).apply {
        minValue = 0
        maxValue = 23
        this.value = value
        setFormatter { "%02d:00".format(it) }
        setOnValueChangedListener { _, _, v -> onChange(v) }
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
}
