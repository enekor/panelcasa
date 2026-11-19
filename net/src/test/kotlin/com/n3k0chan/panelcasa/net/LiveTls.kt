package com.n3k0chan.panelcasa.net

import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Conexión real a Renfe y Met.no validando como Android 7.0 (solo sus raíces) más el respaldo con las raíces
 * empaquetadas. Necesita red y el almacén de AOSP 7.0:
 * ./gradlew :net:liveTls -Paosp=<carpeta con los .0 de AOSP> -Proots=<carpeta res/raw>
 */
fun main(args: Array<String>) {
    val cf = CertificateFactory.getInstance("X.509")
    fun load(f: File) = f.inputStream().use { cf.generateCertificate(it) as X509Certificate }
    val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
    File(args[0]).listFiles { f -> f.name.endsWith(".0") }!!.forEach { ks.setCertificateEntry(it.name, load(it)) }
    val primary = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
        .trustManagers.filterIsInstance<X509TrustManager>().first()
    val roots = File(args[1]).listFiles { f -> f.name.endsWith(".pem") }!!.map(::load)
    println("Raíces de Android 7.0: ${ks.size()} · empaquetadas: ${roots.size}")
    val tm = BundledRootsTrustManager(primary, roots, onFallback = { println("  respaldo: $it") })
    val fetcher = HttpFetcher("pepe/1.0 pepe42rdh@hotmail.com", sslSocketFactory = BundledRootsTrustManager.socketFactory(tm))
    for (url in listOf(
        "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=40.4169&lon=-3.7035",
        "https://gtfsrt.renfe.com/trip_updates.pb",
    )) {
        val r = fetcher.get(url)
        println("${url.substringBefore('?')}: HTTP ${r.code}, ${r.body?.size ?: 0} bytes")
    }
    // Sin respaldo, Met.no debe fallar (es lo que pasaba en la tablet).
    val plain = HttpFetcher("pepe/1.0 pepe42rdh@hotmail.com", sslSocketFactory = BundledRootsTrustManager.socketFactory(primary))
    val metNoWithoutFallback = runCatching { plain.get("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=40.4169&lon=-3.7035") }
    println("Met.no solo con raíces de Android 7.0: " + (metNoWithoutFallback.exceptionOrNull()?.javaClass?.simpleName ?: "HTTP ${metNoWithoutFallback.getOrNull()?.code}"))
}
