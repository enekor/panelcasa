package com.n3k0chan.panelcasa

import com.n3k0chan.panelcasa.net.Fetcher
import java.io.File
import java.io.IOException

/** Aplicación de pruebas: sin red, para que Robolectric no intente descargar nada. */
class TestApp : PanelApp() {
    override fun createFetcher(): Fetcher = object : Fetcher {
        override fun get(url: String, etag: String?, lastModified: String?, maxBytes: Int): Fetcher.Response = throw IOException("sin red en pruebas")
        override fun download(url: String, dest: File, etag: String?, lastModified: String?, onProgress: ((Long, Long) -> Unit)?): Fetcher.Response = throw IOException("sin red en pruebas")
    }
}
