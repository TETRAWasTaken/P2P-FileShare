package com.kolhey.p2p.crypto;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.io.File;
import java.security.cert.CertificateException;

public class WsSecurityManager {

    private static final String ALLOW_INSECURE_DEV_TLS_PROPERTY = "p2p.allowInsecureDevTls";

    public static SslContext buildServerSslContext() throws CertificateException {
        try {
            if (isInsecureDevTlsAllowed()) {
                SelfSignedCertificate ssc = new SelfSignedCertificate("p2p-node-ws");
                return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
            }

            File certChainFile = getRequiredFile("p2p.ws.certChain", "P2P_WS_CERT_CHAIN");
            File privateKeyFile = getRequiredFile("p2p.ws.privateKey", "P2P_WS_PRIVATE_KEY");
            return SslContextBuilder.forServer(certChainFile, privateKeyFile).build();
        } catch (Exception e) {
            throw new CertificateException("Failed to generate WSS Server Context", e);
        }
    }

    public static SslContext buildClientSslContext() throws CertificateException {
        try {
            SslContextBuilder builder = SslContextBuilder.forClient();
            if (isInsecureDevTlsAllowed()) {
                builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            } else {
                File trustCertFile = getRequiredFile("p2p.ws.trustCert", "P2P_WS_TRUST_CERT");
                builder.trustManager(trustCertFile);
            }
            return builder.build();
        } catch (Exception e) {
            throw new CertificateException("Failed to generate WSS Client Context", e);
        }
    }

    private static boolean isInsecureDevTlsAllowed() {
        return Boolean.getBoolean(ALLOW_INSECURE_DEV_TLS_PROPERTY);
    }

    private static File getRequiredFile(String propertyName, String envVarName) {
        String fromProperty = System.getProperty(propertyName);
        String fromEnv = System.getenv(envVarName);
        String value = fromProperty != null && !fromProperty.isBlank() ? fromProperty : fromEnv;

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing TLS material. Set either system property '" + propertyName + "' or env var '" + envVarName + "'."
            );
        }

        File file = new File(value);
        if (!file.isFile()) {
            throw new IllegalStateException("Configured TLS file does not exist: " + file.getAbsolutePath());
        }
        return file;
    }
}