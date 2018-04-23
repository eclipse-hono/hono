/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */

package org.eclipse.hono.config;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.PortConfigurationHelper;
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

    private String trustStorePath;
    private char[] trustStorePassword;
    private String pathSeparator = Constants.DEFAULT_PATH_SEPARATOR;
    private String keyStorePath;
    private char[] keyStorePassword;
    private String certPath;
    private String keyPath;
    private FileFormat trustStoreFormat;
    private FileFormat keyFormat;

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
        return fromChars(trustStorePassword);
    }

    /**
     * Sets the password for accessing the PKCS12 key store containing the certificates of trusted CAs.
     * 
     * @param trustStorePassword The password to set.
     * @see #setTrustStorePath(String)
     */
    public final void setTrustStorePassword(final String trustStorePassword) {
        if (trustStorePassword == null) {
            this.trustStorePassword = null;
        } else {
            this.trustStorePassword = trustStorePassword.toCharArray();
        }
    }

    /**
     * Gets the trust options derived from the trust store properties.
     * 
     * @return The trust options or {@code null} if trust store path is not set or not supported.
     */
    public final TrustOptions getTrustOptions() {

        if (trustStorePath == null) {
            return null;
        }

        final FileFormat format = FileFormat.orDetect(trustStoreFormat, trustStorePath);

        if (format == null) {
            LOG.debug("unsupported trust store format");
            return null;
        }

        switch (format) {
        case PEM:
            LOG.debug("using certificates from file [{}] as trust anchor", trustStorePath);
            return new PemTrustOptions().addCertPath(trustStorePath);
        case PKCS12:
            LOG.debug("using certificates from PKCS12 key store [{}] as trust anchor", trustStorePath);
            return new PfxOptions()
                    .setPath(getTrustStorePath())
                    .setPassword(getTrustStorePassword());
        case JKS:
            LOG.debug("using certificates from JKS key store [{}] as trust anchor", trustStorePath);
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
        return fromChars(keyStorePassword);
    }

    /**
     * Sets the password for the PKCS12 key store containing the private key and certificate chain that should be used
     * for authentication to peers.
     * 
     * @param keyStorePassword The password.
     */
    public final void setKeyStorePassword(final String keyStorePassword) {
        if (keyStorePassword == null) {
            this.keyStorePassword = null;
        } else {
            this.keyStorePassword = keyStorePassword.toCharArray();
        }
    }

    /**
     * Gets the key &amp; certificate options derived from the key store properties.
     * 
     * @return The options or {@code null} if key store path or key path and cert path are not set or not supported.
     * @throws IllegalArgumentException In the case any of the configured files is not present in the file system.
     */
    public KeyCertOptions getKeyCertOptions() {

        if (this.keyPath != null && this.certPath != null) {

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

        } else if (this.keyStorePath != null) {

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

    private static String fromChars(final char[] chars) {
        if (chars == null) {
            return null;
        } else {
            return String.valueOf(chars);
        }
    }
}