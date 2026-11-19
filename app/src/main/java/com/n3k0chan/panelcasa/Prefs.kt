package com.n3k0chan.panelcasa

import android.content.Context
import com.n3k0chan.panelcasa.renfe.DisplayMode

/** Ajustes del usuario. Sin secretos: no hay tokens ni cuentas. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("panel", Context.MODE_PRIVATE)

    var stationId: String?
        get() = sp.getString(KEY_STATION, null)
        set(v) = sp.edit().putString(KEY_STATION, v).apply()

    var displayMode: DisplayMode
        get() = runCatching { DisplayMode.valueOf(sp.getString(KEY_MODE, null) ?: "AUTO") }.getOrDefault(DisplayMode.AUTO)
        set(v) = sp.edit().putString(KEY_MODE, v.name).apply()

    /** Coordenadas manuales para la meteo; null = las de la estación. */
    var manualCoords: Pair<Double, Double>?
        get() {
            val lat = sp.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
            val lon = sp.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
            return lat to lon
        }
        set(v) = sp.edit().apply {
            if (v == null) { remove(KEY_LAT); remove(KEY_LON) } else { putString(KEY_LAT, v.first.toString()); putString(KEY_LON, v.second.toString()) }
        }.apply()

    /** Componentes («paquete/clase») de las apps fijadas, en orden. */
    var pinnedApps: List<String>
        get() = sp.getString(KEY_PINNED, "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString(KEY_PINNED, v.joinToString("\n")).apply()

    var pinnedInitialized: Boolean
        get() = sp.getBoolean(KEY_PINNED_INIT, false)
        set(v) = sp.edit().putBoolean(KEY_PINNED_INIT, v).apply()

    var nightEnabled: Boolean
        get() = sp.getBoolean(KEY_NIGHT, false)
        set(v) = sp.edit().putBoolean(KEY_NIGHT, v).apply()

    var nightFrom: Int
        get() = sp.getInt(KEY_NIGHT_FROM, 0)
        set(v) = sp.edit().putInt(KEY_NIGHT_FROM, v).apply()

    var nightTo: Int
        get() = sp.getInt(KEY_NIGHT_TO, 7)
        set(v) = sp.edit().putInt(KEY_NIGHT_TO, v).apply()

    /** Brillo de la ventana del panel en %, o -1 para usar el del sistema. */
    var brightness: Int
        get() = sp.getInt(KEY_BRIGHTNESS, -1)
        set(v) = sp.edit().putInt(KEY_BRIGHTNESS, v).apply()

    private companion object {
        const val KEY_STATION = "station_id"
        const val KEY_MODE = "display_mode"
        const val KEY_LAT = "manual_lat"
        const val KEY_LON = "manual_lon"
        const val KEY_PINNED = "pinned_apps"
        const val KEY_PINNED_INIT = "pinned_init"
        const val KEY_NIGHT = "night_enabled"
        const val KEY_NIGHT_FROM = "night_from"
        const val KEY_NIGHT_TO = "night_to"
        const val KEY_BRIGHTNESS = "brightness"
    }
}
