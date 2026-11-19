package com.n3k0chan.panelcasa

/** Horas de pantalla oscurecida. [from] y [to] en horas 0–23; el tramo puede cruzar la medianoche. */
object ScreenSchedule {
    fun isNight(enabled: Boolean, from: Int, to: Int, hour: Int): Boolean {
        if (!enabled || from == to) return false
        return if (from < to) hour in from until to else hour >= from || hour < to
    }
}
