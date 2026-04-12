package com.kolhey.p2p.crypto;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

public class QuicSecurityManager {

    private static final String ALLOW_INSECURE_DEV_TLS_PROPERTY = "p2p.allowInsecureDevTls";
    public static final String P2P_ALPN_PROTOCOL = "secure-prp-transfer-v1";

    public static QuicSslContext buildServerSslContext()
    throws CertificateException, SSLException {
        if (isInsecureDevTlsAllowed()) {
            SelfSignedCertificate ssc = new SelfSignedCertificate("p2p-node");

            return QuicSslContextBuilder.forServer(
                ssc.privateKey(),
                null,
                ssc.certificate())
                .applicationProtocols(P2P_ALPN_PROTOCOL)
                .build();
        }

        File keyStoreFile = getRequiredFile("p2p.quic.keyStore", "P2P_QUIC_KEYSTORE");
        String keyStorePassword = getRequiredValue("p2p.quic.keyStorePassword", "P2P_QUIC_KEYSTORE_PASSWORD");
        KeyManagerFactory keyManagerFactory = buildKeyManagerFactory(keyStoreFile, keyStorePassword);

        return QuicSslContextBuilder.forServer(keyManagerFactory, keyStorePassword)
            .applicationProtocols(P2P_ALPN_PROTOCOL)
            .build();
    }

    public static QuicSslContext buildClientSslContext() {
        try {
            QuicSslContextBuilder builder = QuicSslContextBuilder.forClient()
                .applicationProtocols(P2P_ALPN_PROTOCOL);

            if (isInsecureDevTlsAllowed()) {
                builder.trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE);
            } else {
                File trustCertFile = getRequiredFile("p2p.quic.trustCert", "P2P_QUIC_TRUST_CERT");
                builder.trustManager(trustCertFile);
            }

            return builder.build();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build QUIC client SSL context", exception);
        }
    }

    private static boolean isInsecureDevTlsAllowed() {
        return Boolean.getBoolean(ALLOW_INSECURE_DEV_TLS_PROPERTY);
    }

    private static File getRequiredFile(String propertyName, String envVarName) {
        String value = getRequiredValue(propertyName, envVarName);

        File file = new File(value);
        if (!file.isFile()) {
            throw new IllegalStateException("Configured TLS file does not exist: " + file.getAbsolutePath());
        }
        return file;
    }

    private static String getRequiredValue(String propertyName, String envVarName) {
        String fromProperty = System.getProperty(propertyName);
        String fromEnv = System.getenv(envVarName);
        String value = fromProperty != null && !fromProperty.isBlank() ? fromProperty : fromEnv;

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing TLS material. Set either system property '" + propertyName + "' or env var '" + envVarName + "'."
            );
        }

        return value;
    }

    private static KeyManagerFactory buildKeyManagerFactory(File keyStoreFile, String keyStorePassword) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            char[] passwordChars = keyStorePassword.toCharArray();
            try (FileInputStream inputStream = new FileInputStream(keyStoreFile)) {
                keyStore.load(inputStream, passwordChars);
            }

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, passwordChars);
            return keyManagerFactory;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load QUIC key store: " + keyStoreFile.getAbsolutePath(), exception);
        }
    }
}
