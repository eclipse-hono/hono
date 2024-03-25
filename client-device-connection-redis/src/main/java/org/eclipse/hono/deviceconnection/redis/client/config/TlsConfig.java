/**
 * TODO.
 */
package org.eclipse.hono.deviceconnection.redis.client.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.vertx.core.runtime.config.JksConfiguration;
import io.quarkus.vertx.core.runtime.config.PemKeyCertConfiguration;
import io.quarkus.vertx.core.runtime.config.PemTrustCertConfiguration;
import io.quarkus.vertx.core.runtime.config.PfxConfiguration;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * TODO.
 */
@SuppressWarnings("checkstyle:JavadocMethod")
@ConfigGroup
public interface TlsConfig {

    /**
     * Whether SSL/TLS is enabled.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Enable trusting all certificates. Disabled by default.
     */
    @WithDefault("false")
    boolean trustAll();

    /**
     * TODO.
     */
    interface PemTrustCertificate {
        /**
         * TODO.
         */
        @WithParentName
        @WithDefault("false")
        boolean enabled();

        /**
         * TODO.
         */
        Optional<List<String>> certs();

        /**
         * TODO.
         */
        default PemTrustCertConfiguration convert() {
            final PemTrustCertConfiguration trustCertificatePem = new PemTrustCertConfiguration();
            trustCertificatePem.enabled = enabled();
            trustCertificatePem.certs = certs();
            return trustCertificatePem;
        }
    }

    /**
     * Trust configuration in the PEM format.
     * <p>
     * When enabled, {@code #trust-certificate-jks} and {@code #trust-certificate-pfx} must be disabled.
     */
    PemTrustCertificate trustCertificatePem();

    /**
     * TODO.
     */
    interface Jks {
        /**
         * TODO.
         */
        @WithParentName
        @WithDefault("false")
        boolean enabled();

        /**
         * TODO.
         */
        Optional<String> path();

        /**
         * TODO.
         */
        Optional<String> password();

        /**
         * TODO.
         */
        default JksConfiguration convert() {
            final JksConfiguration jks = new JksConfiguration();
            jks.enabled = enabled();
            jks.path = path();
            jks.password = password();
            return jks;
        }
    }

    /**
     * Trust configuration in the JKS format.
     * <p>
     * When enabled, {@code #trust-certificate-pem} and {@code #trust-certificate-pfx} must be disabled.
     */
    Jks trustCertificateJks();

    /**
     * TODO.
     */
    interface Pfx {
        /**
         * TODO.
         */
        @WithParentName
        @WithDefault("false")
        boolean enabled();

        /**
         * TODO.
         */
        Optional<String> path();

        /**
         * TODO.
         */
        Optional<String> password();

        /**
         * TODO.
         */
        default PfxConfiguration convert() {
            final PfxConfiguration jks = new PfxConfiguration();
            jks.enabled = enabled();
            jks.path = path();
            jks.password = password();
            return jks;
        }
    }

    /**
     * Trust configuration in the PFX format.
     * <p>
     * When enabled, {@code #trust-certificate-jks} and {@code #trust-certificate-pem} must be disabled.
     */
    Pfx trustCertificatePfx();

    /**
     * TODO.
     */
    interface PemKeyCert {
        /**
         * TODO.
         */
        @WithParentName
        @WithDefault("false")
        boolean enabled();

        /**
         * TODO.
         */
        Optional<List<String>> keys();

        /**
         * TODO.
         */
        Optional<List<String>> certs();

        /**
         * TODO.
         */
        default PemKeyCertConfiguration convert() {
            final PemKeyCertConfiguration pemKeyCert = new PemKeyCertConfiguration();
            pemKeyCert.enabled = enabled();
            pemKeyCert.keys = keys();
            pemKeyCert.certs = certs();
            return pemKeyCert;
        }
    }

    /**
     * Key/cert configuration in the PEM format.
     * <p>
     * When enabled, {@code key-certificate-jks} and {@code #key-certificate-pfx} must be disabled.
     */
    PemKeyCert keyCertificatePem();

    /**
     * Key/cert configuration in the JKS format.
     * <p>
     * When enabled, {@code #key-certificate-pem} and {@code #key-certificate-pfx} must be disabled.
     */
    Jks keyCertificateJks();

    /**
     * Key/cert configuration in the PFX format.
     * <p>
     * When enabled, {@code key-certificate-jks} and {@code #key-certificate-pem} must be disabled.
     */
    Pfx keyCertificatePfx();

    /**
     * The hostname verification algorithm to use in case the server's identity should be checked.
     * Should be {@code HTTPS}, {@code LDAPS} or an {@code NONE} (default).
     * <p>
     * If set to {@code NONE}, it does not verify the hostname.
     * <p>
     */
    @WithDefault("NONE")
    String hostnameVerificationAlgorithm();

}
