package com.n3k0chan.panelcasa

import android.app.Application
import com.n3k0chan.panelcasa.meteo.MeteoRepository
import com.n3k0chan.panelcasa.net.BundledRootsTrustManager
import com.n3k0chan.panelcasa.net.Fetcher
import com.n3k0chan.panelcasa.net.HttpFetcher
import com.n3k0chan.panelcasa.renfe.RenfeRepository
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

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
    open fun createFetcher(): Fetcher = HttpFetcher(USER_AGENT, sslSocketFactory = tlsSocketFactory())

    /**
     * Validación del sistema (con network_security_config) y, si falla, validación PKIX contra las raíces
     * empaquetadas: necesario en Android 7.0 para api.met.no. Ver BundledRootsTrustManager.
     */
    private fun tlsSocketFactory(): SSLSocketFactory? = try {
        val primary = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()
        val cf = CertificateFactory.getInstance("X.509")
        val roots = BUNDLED_ROOTS.map { id -> resources.openRawResource(id).use { cf.generateCertificate(it) as X509Certificate } }
        val reported = java.util.Collections.synchronizedSet(HashSet<String>())
        val tm = BundledRootsTrustManager(primary, roots, onFallback = { msg ->
            // Una sola vez por servidor y arranque, para no llenar el registro cada minuto.
            if (reported.add(msg)) diagnostics.event(msg)
        })
        BundledRootsTrustManager.socketFactory(tm)
    } catch (e: Exception) {
        diagnostics.error("preparación de TLS", e)
        null
    }

    companion object {
        /** Identificación exigida por las condiciones de uso de MET Norway. */
        const val USER_AGENT = "pepe/1.0 pepe42rdh@hotmail.com"
        const val TAG = "PanelCasa"

        /** Las mismas raíces que declara res/xml/network_security_config.xml. */
        val BUNDLED_ROOTS = listOf(
            R.raw.harica_tls_rsa_root_2021,
            R.raw.harica_tls_ecc_root_2021,
            R.raw.isrg_root_x1,
            R.raw.isrg_root_x2,
        )
    }
}
