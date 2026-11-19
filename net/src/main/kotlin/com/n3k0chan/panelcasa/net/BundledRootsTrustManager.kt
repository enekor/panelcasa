package com.n3k0chan.panelcasa.net

import java.security.cert.CertPathValidator
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Validación de certificados con respaldo para Android 7.0.
 *
 * Problema: api.met.no envía la cadena servidor → GEANT TLS RSA 1 → HARICA TLS RSA Root CA 2021,
 * y esa última viene firmada en cruzado por «Hellenic RootCA 2015», que Android 7.0 no tiene. Aunque la app
 * empaqueta HARICA 2021 como ancla, el validador de Android 7.0 sigue la cadena tal como llega y falla con
 * «Trust anchor for certification path not found».
 *
 * Solución: primero se valida como siempre ([primary]: sistema + network_security_config). Solo si eso falla,
 * se repite una validación PKIX completa (firmas, fechas, restricciones) contra las raíces empaquetadas,
 * cortando la cadena en cuanto aparece una de ellas. Nada se acepta sin llegar a una de esas raíces.
 * El nombre del servidor lo sigue comprobando HttpsURLConnection por separado.
 */
class BundledRootsTrustManager(
    private val primary: X509TrustManager,
    roots: List<X509Certificate>,
    private val onFallback: (String) -> Unit = {},
    private val now: () -> Date = { Date() },
) : X509TrustManager {
    private val roots = roots.toList()
    private val anchors = this.roots.map { TrustAnchor(it, null) }.toSet()

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        primary.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        try {
            primary.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            try {
                validateWithBundledRoots(chain)
            } catch (fallback: Exception) {
                e.addSuppressed(fallback)
                throw e
            }
            onFallback("Certificado de ${chain.firstOrNull()?.subjectX500Principal?.name} validado con una raíz empaquetada")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = primary.acceptedIssuers + roots

    /** Valida [chain] (servidor primero) contra las raíces empaquetadas. Lanza excepción si no es válida. */
    fun validateWithBundledRoots(chain: Array<out X509Certificate>) {
        if (chain.isEmpty()) throw CertificateException("Cadena vacía")
        // Se corta la cadena en el primer certificado que sea una de las raíces (mismo nombre y clave),
        // descartando la versión firmada en cruzado que envía el servidor.
        val path = ArrayList<X509Certificate>()
        for (cert in chain) {
            if (roots.any { it.subjectX500Principal == cert.subjectX500Principal && it.publicKey == cert.publicKey }) break
            path.add(cert)
        }
        if (path.isEmpty()) throw CertificateException("El servidor solo envía una raíz")
        val leaf = path.first()
        val eku = leaf.extendedKeyUsage
        if (eku != null && SERVER_AUTH !in eku && ANY_EKU !in eku) {
            throw CertificateException("El certificado no es de servidor")
        }
        val params = PKIXParameters(anchors).apply {
            isRevocationEnabled = false
            date = now()
        }
        val certPath = CertificateFactory.getInstance("X.509").generateCertPath(path)
        CertPathValidator.getInstance("PKIX").validate(certPath, params)
    }

    companion object {
        private const val SERVER_AUTH = "1.3.6.1.5.5.7.3.1"
        private const val ANY_EKU = "2.5.29.37.0"

        /** Contexto TLS listo para usar con HttpsURLConnection. */
        fun socketFactory(trustManager: X509TrustManager): SSLSocketFactory =
            SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory
    }
}
