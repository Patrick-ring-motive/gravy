package com.example

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Supplies HTTPS trust from platform stores in addition to JVM defaults.
 * Hostname verification remains owned by {@link java.net.http.HttpClient}.
 */
final class JavaScriptSystemTrust {
    private static final List<String> MACOS_SYSTEM_KEYCHAINS = [
        '/System/Library/Keychains/SystemRootCertificates.keychain',
        '/Library/Keychains/System.keychain'
    ].asImmutable()
    private static final List<String> LINUX_CERTIFICATE_BUNDLES = [
        '/etc/ssl/certs/ca-certificates.crt',
        '/etc/pki/tls/certs/ca-bundle.crt',
        '/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem',
        '/etc/ssl/ca-bundle.pem',
        '/etc/ssl/cert.pem',
        '/etc/ca-certificates/extracted/cadir/ca-certs.crt'
    ].asImmutable()
    private static final Pattern PEM_CERTIFICATE = Pattern.compile(
        '-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----', Pattern.DOTALL
    )
    private static final String OS_NAME = System.getProperty('os.name', '').toLowerCase(Locale.ROOT)
    private static final List<X509Certificate> SYSTEM_CERTIFICATES = systemCertificates()
    private static final SSLContext CONTEXT = createContext(SYSTEM_CERTIFICATES)

    private JavaScriptSystemTrust() {
    }

    static SSLContext sslContext() { CONTEXT }

    static boolean macOS() { OS_NAME.contains('mac') }
    static boolean windows() { OS_NAME.contains('win') }
    static boolean linux() { OS_NAME.contains('linux') }

    static int systemCertificateCount() { SYSTEM_CERTIFICATES.size() }
    static int macOSSystemCertificateCount() { macOS() ? SYSTEM_CERTIFICATES.size() : 0 }

    static String trustSource() {
        macOS() ? 'macOS keychains' : windows() ? 'Windows-ROOT' : linux() ? 'Linux CA bundles' : 'JVM default'
    }

    private static SSLContext createContext(List<X509Certificate> certificates) {
        SSLContext fallback = SSLContext.default
        if (certificates.isEmpty()) {
            return fallback
        }

        try {
            X509TrustManager defaultTrust = trustManager(null)
            LinkedHashSet<X509Certificate> trustedCertificates = new LinkedHashSet<>()
            trustedCertificates.addAll(defaultTrust.acceptedIssuers)
            trustedCertificates.addAll(certificates)

            KeyStore combinedStore = KeyStore.getInstance(KeyStore.defaultType)
            combinedStore.load(null, null)
            trustedCertificates.eachWithIndex { X509Certificate certificate, int index ->
                combinedStore.setCertificateEntry("trusted-${index}", certificate)
            }

            SSLContext context = SSLContext.getInstance('TLS')
            context.init(null, [trustManager(combinedStore)] as TrustManager[], null)
            context
        } catch (Exception ignored) {
            fallback
        }
    }

    private static X509TrustManager trustManager(KeyStore keyStore) {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.defaultAlgorithm)
        if (keyStore == null) {
            factory.init((KeyStore) null)
        } else {
            factory.init(keyStore)
        }
        factory.trustManagers.find { TrustManager manager -> manager instanceof X509TrustManager } as X509TrustManager
    }

    private static List<X509Certificate> systemCertificates() {
        if (macOS()) return macOSSystemCertificates()
        if (windows()) return windowsRootCertificates()
        if (linux()) return linuxSystemCertificates()
        []
    }

    private static List<X509Certificate> macOSSystemCertificates() {
        LinkedHashSet<X509Certificate> certificates = new LinkedHashSet<>()
        MACOS_SYSTEM_KEYCHAINS.each { String keychain ->
            certificates.addAll(certificatesFromPem(runSecurityExport(keychain)))
        }
        new ArrayList<>(certificates)
    }

    private static List<X509Certificate> windowsRootCertificates() {
        try {
            KeyStore rootStore = KeyStore.getInstance('Windows-ROOT')
            rootStore.load(null, null)
            LinkedHashSet<X509Certificate> certificates = new LinkedHashSet<>()
            Enumeration<String> aliases = rootStore.aliases()
            while (aliases.hasMoreElements()) {
                def certificate = rootStore.getCertificate(aliases.nextElement())
                if (certificate instanceof X509Certificate) {
                    certificates << certificate as X509Certificate
                }
            }
            new ArrayList<>(certificates)
        } catch (Exception ignored) {
            []
        }
    }

    private static List<X509Certificate> linuxSystemCertificates() {
        LinkedHashSet<X509Certificate> certificates = new LinkedHashSet<>()
        List<String> bundlePaths = []
        String configuredBundle = System.getenv('SSL_CERT_FILE')
        if (configuredBundle) bundlePaths << configuredBundle
        bundlePaths.addAll(LINUX_CERTIFICATE_BUNDLES)
        bundlePaths.unique().each { String path -> certificates.addAll(certificatesFromFile(new File(path))) }
        new ArrayList<>(certificates)
    }

    private static String runSecurityExport(String keychain) {
        try {
            Process process = new ProcessBuilder('/usr/bin/security', 'find-certificate', '-a', '-p', keychain)
                .redirectErrorStream(true)
                .start()
            String output = process.inputStream.getText('US-ASCII')
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ''
            }
            process.exitValue() == 0 ? output : ''
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt()
            }
            ''
        }
    }

    private static List<X509Certificate> certificatesFromFile(File file) {
        if (!file.isFile() || !file.canRead()) {
            return []
        }
        try {
            certificatesFromPem(file.getText('US-ASCII'))
        } catch (IOException | SecurityException ignored) {
            []
        }
    }

    private static List<X509Certificate> certificatesFromPem(String source) {
        CertificateFactory factory = CertificateFactory.getInstance('X.509')
        Matcher matcher = PEM_CERTIFICATE.matcher(source)
        List<X509Certificate> certificates = []
        while (matcher.find()) {
            try {
                certificates << (factory.generateCertificate(new ByteArrayInputStream(matcher.group().bytes)) as X509Certificate)
            } catch (CertificateException ignored) {
                // Ignore malformed records while preserving all valid system certificates.
            }
        }
        certificates
    }
}
