package com.example.cardwheel

import org.mariadb.jdbc.Configuration
import org.mariadb.jdbc.HostAddress
import org.mariadb.jdbc.export.ExceptionFactory
import org.mariadb.jdbc.plugin.TlsSocketPlugin
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.KeyManager
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSession
import java.security.cert.X509Certificate
import java.util.Locale

/** Verify the chain during the handshake, before credentials are sent. */
class StrictTlsPlugin : TlsSocketPlugin {
    override fun type() = "cardwheel-strict"
    override fun getKeyManager(conf: Configuration, exceptionFactory: ExceptionFactory): Array<KeyManager>? = null
    override fun getTrustManager(conf: Configuration, exceptionFactory: ExceptionFactory, hostAddress: HostAddress): Array<TrustManager> {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        val pem = conf.serverSslCert()
        val trustStore = if (pem.isNullOrEmpty()) null else KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            val certificates = CertificateFactory.getInstance("X.509").generateCertificates(pem.byteInputStream())
            require(certificates.isNotEmpty())
            certificates.forEachIndexed { index, cert -> setCertificateEntry("server-$index", cert) }
        }
        factory.init(trustStore)
        return factory.trustManagers
    }
    override fun verify(host: String, session: SSLSession, serverThreadId: Long) {
        val cert = session.peerCertificates.firstOrNull() as? X509Certificate ?: throw SSLException("Missing server certificate")
        try { cert.checkValidity() } catch (error: java.security.cert.CertificateException) { throw SSLException("Server certificate is not valid", error) }
        val name = host.removeSurrounding("[", "]").trimEnd('.').lowercase(Locale.ROOT)
        val ip = name.contains(':') || name.matches(Regex("[0-9.]+"))
        val matches = cert.subjectAlternativeNames.orEmpty().any { entry ->
            if (ip && entry[0] == 7) {
                val alternative = entry[1] as? String ?: return@any false
                java.net.InetAddress.getByName(name) == java.net.InetAddress.getByName(alternative)
            } else if (!ip && entry[0] == 2) {
                val alternative = (entry[1] as? String)?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return@any false
                if (alternative.startsWith("*.")) {
                    val suffix = alternative.substring(1)
                    name.endsWith(suffix) && name.removeSuffix(suffix).let { it.isNotEmpty() && '.' !in it }
                } else name == alternative
            } else false
        }
        if (!matches) throw SSLException("Server certificate does not match the connection host")
    }
}
