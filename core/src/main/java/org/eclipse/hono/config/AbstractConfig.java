/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.PortConfigurationHelper;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TrustOptions;

/**
 * A base class for managing basic configuration properties shared by clients and server components.
 *
 */
public abstract class AbstractConfig {

    /**
     * The prefix indicating a file path.
     */
    public static final String PREFIX_FILE = "file:";

    /**
     *  A logger to be shared with subclasses.
     */
    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    private TrustOptions trustOptions = null;
    private String trustStorePath = null;
    private String trustStorePassword = null;
    private String pathSeparator = Constants.DEFAULT_PATH_SEPARATOR;
    private String keyStorePath = null;
    private String keyStorePassword = null;
    private String certPath = null;
    private String keyPath = null;
    private KeyCertOptions keyCertOptions = null;
    private FileFormat trustStoreFormat = null;
    private FileFormat keyFormat = null;
    private List<String> secureProtocols;
    private List<String> supportedCipherSuites;

    /**
     * Creates a new empty instance.
     */
    protected AbstractConfig() {
        final List<String> initialProtocols = new ArrayList<>();
        initialProtocols.add("TLSv1.3");
        initialProtocols.add("TLSv1.2");
        this.secureProtocols = Collections.unmodifiableList(initialProtocols);
        final List<String> initialCiphers = new ArrayList<>();
        this.supportedCipherSuites = Collections.unmodifiableList(initialCiphers);
    }

    /**
     * Creates a new instance from another instance.
     *
     * @param other The other instance. All of the other instance's properties
     *              are copied to the newly created instance.
     */
    protected AbstractConfig(final AbstractConfig other) {
        this();
        this.certPath = other.certPath;
        this.keyFormat = other.keyFormat;
        this.keyPath = other.keyPath;
        this.keyStorePassword = other.keyStorePassword;
        this.keyStorePath = other.keyStorePath;
        this.pathSeparator = other.pathSeparator;
        this.secureProtocols = Collections.unmodifiableList(new ArrayList<>(other.secureProtocols));
        this.supportedCipherSuites = Collections.unmodifiableList(new ArrayList<>(other.supportedCipherSuites));
        this.trustStoreFormat = other.trustStoreFormat;
        this.trustStorePassword = other.trustStorePassword;
        this.trustStorePath = other.trustStorePath;
    }


    /**
     * Creates a new instance from generic options.
     *
     * @param other The options. All of the options are copied to the newly created instance.
     */
    protected AbstractConfig(final GenericOptions other) {
        this();
        this.certPath = other.certPath().orElse(null);
        this.keyFormat = other.keyFormat().orElse(null);
        this.keyPath = other.keyPath().orElse(null);
        this.keyStorePassword = other.keyStorePassword().orElse(null);
        this.keyStorePath = other.keyStorePath().orElse(null);
        this.pathSeparator = other.pathSeparator();
        this.secureProtocols = Collections.unmodifiableList(new ArrayList<>(other.secureProtocols()));
        this.supportedCipherSuites = Collections.unmodifiableList(
                new ArrayList<>(other.supportedCipherSuites().orElse(Collections.emptyList())));
        this.trustStoreFormat = other.trustStoreFormat().orElse(null);
        this.trustStorePassword = other.trustStorePassword().orElse(null);
        this.trustStorePath = other.trustStorePath().orElse(null);
    }

    /**
     * Gets the password represented by a property value.
     * <p>
     * This method determines the password as follows:
     * <ol>
     * <li>If the given value does not start with {@value #PREFIX_FILE}, then the password is the given value.</li>
     * <li>Otherwise the password is the UTF-8 encoded string represented by the first line read from the
     * file indicated by the property's value after the {@value #PREFIX_FILE} prefix.</li>
     * </ol>
     *
     * @param purpose A (very) short description of the context in which the password is being used.
     * @param value The property value to determine the password from.
     * @return The password.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = """
                    The path that the password is read from is determined from configuration properties that
                    are supposed to be passed in during startup of the component only.
                    """)
    protected final String getPassword(final String purpose, final String value) {

        Objects.requireNonNull(purpose);
        Objects.requireNonNull(value);

        if (!value.startsWith(PREFIX_FILE)) {
            return value;
        }

        final String fsPath = value.substring(PREFIX_FILE.length());
        final File file = new File(fsPath);
        if (!file.exists()) {
            LOG.warn("cannot read {} password, file [{}] does not exist", purpose, fsPath);
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            LOG.debug("reading {} password from [{}]", purpose, fsPath);
            return reader.readLine();
        } catch (IOException e) {
            LOG.warn("could not read {} password from file [{}]", purpose, fsPath, e);
            return null;
        }
    }

    /**
     * Checks if a given port number is valid.
     *
     * @param port The port number.
     * @return {@code true} if port &gt;= 0 and port &lt;= 65535.
     */
    protected boolean isValidPort(final int port) {
        return PortConfigurationHelper.isValidPort(port);
    }

    /**
     * Gets the character separating the segments of target addresses.
     *
     * @return The separator.
     */
    public final String getPathSeparator() {
        return pathSeparator;
    }

    /**
     * Sets the character separating the segments of target addresses.
     * <p>
     * The default value of this property is {@link Constants#DEFAULT_PATH_SEPARATOR}.
     *
     * @param pathSeparator The separator to use.
     * @throws NullPointerException if the given character is {@code null}.
     */
    public final void setPathSeparator(final String pathSeparator) {
        this.pathSeparator = Objects.requireNonNull(pathSeparator);
    }

    /**
     * Gets the path to the key store to load certificates of trusted CAs from.
     *
     * @return The absolute path to the key store or {@code null} if not set.
     */
    public final String getTrustStorePath() {
        return trustStorePath;
    }

    /**
     * Sets the path to the key store to load certificates of trusted CAs from.
     *
     * @param trustStorePath The absolute path to the key store.
     */
    public final void setTrustStorePath(final String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    /**
     * Gets the password for accessing the key store containing the certificates of trusted CAs.
     *
     * @return The password or {@code null} if no password is set.
     * @see #getTrustStorePath()
     * @see #setTrustStorePassword(String)
     */
    public final String getTrustStorePassword() {
        this.trustStorePassword = Optional.ofNullable(trustStorePassword)
                .map(v -> getPassword("trust store", v))
                .orElse(null);
        return trustStorePassword;
    }

    /**
     * Sets the password for accessing the key store containing the certificates of trusted CAs.
     * <p>
     * The password can be set either explicitly or implicitly by specifying the path to a file from where the
     * password will be read. In the latter case, the value to set is the (absolute) path to the file prefixed
     * by {@value #PREFIX_FILE}. To read the password from file <em>/etc/hono/password</em>, this property
     * would need to be set to value {@code file:/etc/hono/password}.
     *
     * @param trustStorePassword The password to set.
     * @see #setTrustStorePath(String)
     */
    public final void setTrustStorePassword(final String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    /**
     * Gets the trust options derived from the trust store properties.
     *
     * @return The trust options or {@code null} if trust store path is not set or not supported.
     * @throws IllegalArgumentException In the case the configured trust store is not present in the file system.
     */
    public final TrustOptions getTrustOptions() {
       if (trustOptions == null) {
           trustOptions = createTrustOptions();
       }

       return trustOptions;
    }


    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = """
                    The path that the trust store is read from is determined from configuration properties that
                    are supposed to be passed in during startup of the component only.
                    """)
    private TrustOptions createTrustOptions() {

        if (trustOptions != null) {
            return trustOptions;
        }

        if (Strings.isNullOrEmpty(this.trustStorePath)) {
            return null;
        }

        if (!Files.exists(Paths.get(this.trustStorePath))) {
            throw new IllegalArgumentException(
                    String.format("Configured trust store file does not exist: %s", this.trustStorePath));
        }

        final FileFormat format = FileFormat.orDetect(this.trustStoreFormat, this.trustStorePath);

        if (format == null) {
            LOG.debug("unsupported trust store format");
            return null;
        }

        switch (format) {
        case PEM:
            LOG.debug("using certificates from file [{}] as trust anchor", this.trustStorePath);
            return new PemTrustOptions().addCertPath(this.trustStorePath);
        case PKCS12:
            LOG.debug("using certificates from PKCS12 key store [{}] as trust anchor", this.trustStorePath);
            return new PfxOptions()
                    .setPath(getTrustStorePath())
                    .setPassword(getTrustStorePassword());
        case JKS:
            LOG.debug("using certificates from JKS key store [{}] as trust anchor", this.trustStorePath);
            return new JksOptions()
                    .setPath(getTrustStorePath())
                    .setPassword(getTrustStorePassword());
        default:
            LOG.debug("unsupported trust store format: {}", format);
            return null;
        }
    }

    /**
     * Gets the absolute path to the key store containing the private key
     * and certificate chain that will be used for authentication to peers.
     *
     * @return The path or {@code null} if no path has been set.
     */
    public final String getKeyStorePath() {
        return keyStorePath;
    }

    /**
     * Sets the absolute path to the key store containing the private key and certificate chain that should be
     * used for authentication to peers.
     *
     * @param keyStorePath The path.
     */
    public final void setKeyStorePath(final String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    /**
     * Gets the password for the key store containing the private key and certificate chain that should be used
     * for authentication to peers.
     *
     * @return The password or {@code null} if no password has been set.
     * @see #setKeyStorePassword(String)
     */
    public final String getKeyStorePassword() {
        this.keyStorePassword = Optional.ofNullable(keyStorePassword)
                .map(v -> getPassword("key store", v))
                .orElse(null);
        return keyStorePassword;
    }

    /**
     * Sets the password for the key store containing the private key and certificate chain that should be used
     * for authentication to peers.
     * <p>
     * The password can be set either explicitly or implicitly by specifying the path to a file from where the
     * password will be read. In the latter case, the value to set is the (absolute) path to the file prefixed
     * by {@value #PREFIX_FILE}. To read the password from file <em>/etc/hono/password</em>, this property
     * would need to be set to value {@code file:/etc/hono/password}.
     *
     * @param keyStorePassword The password.
     */
    public final void setKeyStorePassword(final String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    /**
     * Gets the key &amp; certificate options derived from the key store properties.
     *
     * @return The options or {@code null} if key store path or key path and cert path are not set or not supported.
     * @throws IllegalArgumentException In the case any of the configured files is not present in the file system.
     */
    public final KeyCertOptions getKeyCertOptions() {
        if (keyCertOptions == null) {
            keyCertOptions = createKeyCertOptions();
        }

        return keyCertOptions;
    }

    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = """
                    The paths that the certificate and key are read from are determined from configuration properties that
                    are supposed to be passed in during startup of the component only.
                    """)
    private KeyCertOptions createKeyCertOptions() {

        if (!Strings.isNullOrEmpty(this.keyPath) && !Strings.isNullOrEmpty(this.certPath)) {

            if (!Files.exists(Paths.get(this.keyPath))) {
                throw new IllegalArgumentException(
                        String.format("Configured key file does not exist: %s", this.keyPath));
            }

            if (!Files.exists(Paths.get(this.certPath))) {
                throw new IllegalArgumentException(
                        String.format("Configured certificate file does not exist: %s", this.certPath));
            }

            final FileFormat format = FileFormat.orDetect(this.keyFormat, this.keyPath);
            final FileFormat certFormat = FileFormat.orDetect(this.keyFormat, this.certPath);

            // consistency checks

            if (format == null) {
                LOG.warn("Unable to detect key file format for: {}", keyPath);
                return null;
            }

            if (certFormat == null) {
                LOG.warn("Unable to detect cert file format for: {}", certPath);
                return null;
            }

            if (certFormat != format) {
                LOG.warn("Key file is {}, but cert file is {}, it must be {} as well", format, certFormat, format);
                return null;
            }

            // now construct result

            switch (format) {
            case PEM:
                LOG.debug("using key [{}] and certificate [{}] for identity", this.keyPath, this.certPath);
                return new PemKeyCertOptions().setKeyPath(this.keyPath).setCertPath(this.certPath);
            default:
                LOG.warn("unsupported key & cert format: {}", format);
                return null;
            }

        } else if (!Strings.isNullOrEmpty(this.keyStorePath)) {

            if (!Files.exists(Paths.get(this.keyStorePath))) {
                throw new IllegalArgumentException(
                        String.format("Configured keystore file does not exist: %s", this.keyStorePath));
            }

            final FileFormat format = FileFormat.orDetect(this.keyFormat, this.keyStorePath);

            if (format == null) {
                LOG.warn("Unable to detect keystore file format for: {}", keyStorePath);
                return null;
            }

            // construct result

            switch (format) {
            case PKCS12:
                LOG.debug("using key & certificate from PKCS12 key store [{}] for identity", this.keyStorePath);
                return new PfxOptions().setPath(this.keyStorePath).setPassword(getKeyStorePassword());
            case JKS:
                LOG.debug("using key & certificate from JKS key store [{}] for server identity", this.keyStorePath);
                return new JksOptions().setPath(this.keyStorePath).setPassword(getKeyStorePassword());
            default:
                LOG.warn("unsupported key store format: {}", format);
                return null;
            }

        } else {

            // no configuration

            LOG.debug("neither key/cert nor keystore is configured");
            return null;

        }
    }

    /**
     * Gets the absolute path to the PEM file containing the X.509 certificate chain for the RSA private key that should
     * be used for authentication to peers.
     *
     * @return The path or {@code null} if no path has been set.
     */
    public final String getCertPath() {
        return certPath;
    }

    /**
     * Sets the absolute path to the PEM file containing the X.509 certificate chain for the RSA private key that should
     * be used for authentication to peers.
     * <p>
     * In order to use a non-RSA type key (e.g. an ECC based key) a PKCS12 key store containing the key and certificate
     * chain should be configured by means of the {@link #setKeyStorePath(String)} and
     * {@link #setKeyStorePassword(String)} methods.
     *
     * @param certPath The path.
     */
    public final void setCertPath(final String certPath) {
        this.certPath = certPath;
    }

    /**
     * Gets the absolute path to the PEM file containing the RSA private key that will be used for authentication to
     * peers.
     *
     * @return The path or {@code null} if no path has been set.
     */
    public final String getKeyPath() {
        return keyPath;
    }

    /**
     * Sets the absolute path to the PEM file containing the RSA private key that should be used for authentication to
     * peers.
     * <p>
     * In order to use a non-RSA type key (e.g. an ECC based key) a PKCS12 key store containing the key should be
     * configured by means of the {@link #setKeyStorePath(String)} and {@link #setKeyStorePassword(String)} methods.
     *
     * @param keyPath The path.
     */
    public final void setKeyPath(final String keyPath) {
        this.keyPath = keyPath;
    }

    /**
     * Specify the format of the trust store explicitly.
     *
     * @param trustStoreFormat The format to use when reading the trust store, may be {@code null} to trigger auto
     *            detection.
     */
    public final void setTrustStoreFormat(final FileFormat trustStoreFormat) {
        this.trustStoreFormat = trustStoreFormat;
    }

    /**
     * Get the specified format of the trust store.
     *
     * @return The format or {@code null} if auto-detection should be tried.
     */
    public final FileFormat getTrustStoreFormat() {
        return trustStoreFormat;
    }

    /**
     * Specify the format of the key material explicitly.
     *
     * @param keyFormat The format to use when reading the key material, may be {@code null} to trigger auto detection.
     */
    public final void setKeyFormat(final FileFormat keyFormat) {
        this.keyFormat = keyFormat;
    }

    /**
     * Get the specified format of the key files.
     *
     * @return The format or {@code null} if auto-detection should be tried.
     */
    public final FileFormat getKeyFormat() {
        return keyFormat;
    }

    /**
     * Gets the secure protocols that are enabled for TLS connections.
     * <p>
     * By default, only <em>TLSv1.2</em> and <em>TLSv1.3</em> are enabled.
     * Please refer to the
     * <a href="https://vertx.io/docs/vertx-core/java/#ssl">vert.x
     * documentation</a> for a list of supported values.
     *
     * @return The enabled protocols in order of preference.
     */
    public final List<String> getSecureProtocols() {
        return secureProtocols;
    }

    /**
     * Sets the secure protocols that are enabled for TLS connections.
     * <p>
     * By default, only <em>TLSv1.2</em> and <em>TLSv1.3</em> are enabled.
     * Please refer to the
     * <a href="https://vertx.io/docs/vertx-core/java/#ssl">vert.x
     * documentation</a> for a list of supported values.
     * <p>
     * Note that setting this property to an empty list effectively
     * disables TLS altogether.
     *
     * @param enabledProtocols The protocols in order of preference.
     * @throws NullPointerException if protocols is {@code null}.
     */
    public final void setSecureProtocols(final List<String> enabledProtocols) {
        Objects.requireNonNull(enabledProtocols);
        this.secureProtocols = Collections.unmodifiableList(enabledProtocols);
    }

    /**
     * Gets the names of the cipher suites that may be used in TLS connections.
     * <p>
     * An empty list indicates that all cipher suites supported by the JVM can be used. This is also the default.
     * <p>
     * Please refer to
     * <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#jsse-cipher-suite-names">
     * JSSE Cipher Suite Names</a> for a list of supported cipher suite names.
     *
     * @return The supported cipher suites in order of preference.
     */
    public final List<String> getSupportedCipherSuites() {
        return supportedCipherSuites;
    }

    /**
     * Sets the names of the cipher suites that may be used in TLS connections.
     * <p>
     * An empty list indicates that all cipher suites supported by the JVM can be used. This is also the default.
     * <p>
     * Please refer to
     * <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#jsse-cipher-suite-names">
     * JSSE Cipher Suite Names</a> for a list of supported cipher suite names.
     *
     * @param cipherSuites The supported cipher suites in order of preference.
     * @throws NullPointerException if cipher suites is {@code null}.
     */
    public final void setSupportedCipherSuites(final List<String> cipherSuites) {
        Objects.requireNonNull(cipherSuites);
        this.supportedCipherSuites = Collections.unmodifiableList(cipherSuites);
    }
}
