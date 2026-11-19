package com.n3k0chan.panelcasa

import android.app.Application
import com.n3k0chan.panelcasa.meteo.MeteoRepository
import com.n3k0chan.panelcasa.net.Fetcher
import com.n3k0chan.panelcasa.net.HttpFetcher
import com.n3k0chan.panelcasa.renfe.RenfeRepository
import java.io.File

open class PanelApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var diagnostics: Diagnostics
        private set
    lateinit var engine: DataEngine
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        diagnostics = Diagnostics(this)
        val fetcher = createFetcher()
        engine = DataEngine(
            prefs = prefs,
            renfe = RenfeRepository(File(filesDir, "renfe"), fetcher),
            meteo = MeteoRepository(File(filesDir, "meteo"), fetcher),
            diagnostics = diagnostics,
        )
        engine.init()
        diagnostics.startSampling()
    }

    /** Las pruebas lo sustituyen por un cliente sin red. */
    open fun createFetcher(): Fetcher = HttpFetcher(USER_AGENT)

    companion object {
        /** Identificación exigida por las condiciones de uso de MET Norway. */
        const val USER_AGENT = "pepe/1.0 pepe42rdh@hotmail.com"
        const val TAG = "PanelCasa"
    }
}
