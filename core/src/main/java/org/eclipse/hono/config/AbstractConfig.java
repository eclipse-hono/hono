/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.PortConfigurationHelper;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /**
     * Creates a new empty instance.
     */
    public AbstractConfig() {
        final List<String> initialProtocols = new ArrayList<>();
        initialProtocols.add("TLSv1.3");
        initialProtocols.add("TLSv1.2");
        this.secureProtocols = Collections.unmodifiableList(initialProtocols);
    }

    /**
     * Creates a new instance from another instance.
     *
     * @param other The other instance. All of the other instance's properties
     *              are copied to the newly created instance.
     */
    public AbstractConfig(final AbstractConfig other) {
        this();
        this.certPath = other.certPath;
        this.keyFormat = other.keyFormat;
        this.keyPath = other.keyPath;
        this.keyStorePassword = other.keyStorePassword;
        this.keyStorePath = other.keyStorePath;
        this.pathSeparator = other.pathSeparator;
        this.secureProtocols = new LinkedList<>(other.secureProtocols);
        this.trustStoreFormat = other.trustStoreFormat;
        this.trustStorePassword = other.trustStorePassword;
        this.trustStorePath = other.trustStorePath;
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
     * Gets the path to the PKCS12 key store to load certificates of trusted CAs from.
     *
     * @return The absolute path to the key store or {@code null} if not set.
     */
    public final String getTrustStorePath() {
        return trustStorePath;
    }

    /**
     * Sets the path to the PKCS12 key store to load certificates of trusted CAs from.
     *
     * @param trustStorePath The absolute path to the key store.
     */
    public final void setTrustStorePath(final String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    /**
     * Gets the password for accessing the PKCS12 key store containing the certificates of trusted CAs.
     *
     * @return The password or {@code null} if no password is set.
     * @see #getTrustStorePath()
     */
    public final String getTrustStorePassword() {
        return trustStorePassword;
    }

    /**
     * Sets the password for accessing the PKCS12 key store containing the certificates of trusted CAs.
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
     * Gets the absolute path to the PKCS12 key store containing the private key
     * and certificate chain that will be used for authentication to peers.
     *
     * @return The path or {@code null} if no path has been set.
     */
    public final String getKeyStorePath() {
        return keyStorePath;
    }

    /**
     * Sets the absolute path to the PKCS12 key store containing the private key and certificate chain that should be
     * used for authentication to peers.
     *
     * @param keyStorePath The path.
     */
    public final void setKeyStorePath(final String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    /**
     * Gets the password for the PKCS12 key store containing the private key and certificate chain that should be used
     * for authentication to peers.
     *
     * @return The password or {@code null} if no password has been set.
     */
    public final String getKeyStorePassword() {
        return keyStorePassword;
    }

    /**
     * Sets the password for the PKCS12 key store containing the private key and certificate chain that should be used
     * for authentication to peers.
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
     * @return The enabled protocols.
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
     * @param enabledProtocols The protocols.
     * @throws NullPointerException if protocols is {@code null}.
     */
    public final void setSecureProtocols(final List<String> enabledProtocols) {
        Objects.requireNonNull(enabledProtocols);
        this.secureProtocols = Collections.unmodifiableList(enabledProtocols);
    }

}
