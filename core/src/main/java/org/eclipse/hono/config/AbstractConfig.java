/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.config;

import java.util.Objects;
import java.util.regex.Pattern;

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

    private static final Pattern PATTERN_PEM = Pattern.compile("^.*\\.[pP][eE][mM]$");
    private static final Pattern PATTERN_PKCS = Pattern.compile("^.*\\.[pP](12|[fF][xX])$");
    private static final Pattern PATTERN_JKS = Pattern.compile("^.*\\.[jJ][kK][sS]$");

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private String trustStorePath;
    private char[] trustStorePassword;
    private String pathSeparator = Constants.DEFAULT_PATH_SEPARATOR;
    private String keyStorePath;
    private char[] keyStorePassword;
    private String certPath;
    private String keyPath;

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
     * Gets the password for accessing the PKCS12 key store containing the certificates
     * of trusted CAs.
     * 
     * @return The password or {@code null} if no password is set.
     * @see #getTrustStorePath()
     */
    public final String getTrustStorePassword() {
        return fromChars(trustStorePassword);
    }

    /**
     * Sets the password for accessing the PKCS12 key store containing the certificates
     * of trusted CAs.
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
        } else if (hasPemFileSuffix(trustStorePath)) {
            LOG.debug("using certificates from file [{}] as trust anchor", trustStorePath);
            return new PemTrustOptions().addCertPath(trustStorePath);
        } else if (hasPkcsFileSuffix(trustStorePath)) {
            LOG.debug("using certificates from PKCS12 key store [{}] as trust anchor", trustStorePath);
            return new PfxOptions()
                        .setPath(getTrustStorePath())
                        .setPassword(getTrustStorePassword());
        } else if (hasJksFileSuffix(trustStorePath)) {
            LOG.debug("using certificates from JKS key store [{}] as trust anchor", trustStorePath);
            return new JksOptions()
                        .setPath(getTrustStorePath())
                        .setPassword(getTrustStorePassword());
        } else {
            LOG.debug("unsupported trust store format");
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
     * Sets the absolute path to the PKCS12 key store containing the private key
     * and certificate chain that should be used for authentication to peers.
     * 
     * @param keyStorePath The path.
     */
    public final void setKeyStorePath(final String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    /**
     * Gets the password for the PKCS12 key store containing the private key
     * and certificate chain that should be used for authentication to peers.
     * 
     * @return The password or {@code null} if no password has been set.
     */
    public final String getKeyStorePassword() {
        return fromChars(keyStorePassword);
    }

    /**
     * Sets the password for the PKCS12 key store containing the private key
     * and certificate chain that should be used for authentication to peers.
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
     */
    public KeyCertOptions getKeyCertOptions() {

        if (keyPath != null && certPath != null && hasPemFileSuffix(keyPath) && hasPemFileSuffix(certPath)) {
            LOG.debug("using key [{}] and certificate [{}] for identity", keyPath, certPath);
            return new PemKeyCertOptions().setKeyPath(keyPath).setCertPath(certPath);
        } else if (keyStorePath == null) {
            return null;
        } else if (hasPkcsFileSuffix(keyStorePath)) {
            LOG.debug("using key & certificate from PKCS12 key store [{}] for identity", keyStorePath);
            return new PfxOptions().setPath(keyStorePath).setPassword(getKeyStorePassword());
        } else if (hasJksFileSuffix(keyStorePath)) {
            LOG.debug("using key & certificate from JKS key store [{}] for server identity", keyStorePath);
            return new JksOptions().setPath(keyStorePath).setPassword(getKeyStorePassword());
        } else {
            LOG.debug("unsupported key store format");
            return null;
        }
    }

    /**
     * Gets the absolute path to the PEM file containing the X.509 certificate chain
     * for the RSA private key that should be used for authentication to peers.
     * 
     * @return The path or {@code null} if no path has been set.
     */
    public final String getCertPath() {
        return certPath;
    }

    /**
     * Sets the absolute path to the PEM file containing the X.509 certificate chain
     * for the RSA private key that should be used for authentication to peers.
     * <p>
     * In order to use a non-RSA type key (e.g. an ECC based key) a PKCS12 key store
     * containing the key and certificate chain should be configured by means of the
     * {@link #setKeyStorePath(String)} and {@link #setKeyStorePassword(String)} methods.
     * 
     * @param certPath The path.
     */
    public final void setCertPath(final String certPath) {
        this.certPath = certPath;
    }

    /**
     * Gets the absolute path to the PEM file containing the RSA private key
     * that will be used for authentication to peers.
     * 
     * @return The path or {@code null} if no path has been set.
     */
    public final String getKeyPath() {
        return keyPath;
    }

    /**
     * Sets the absolute path to the PEM file containing the RSA private key
     * that should be used for authentication to peers.
     * <p>
     * In order to use a non-RSA type key (e.g. an ECC based key) a PKCS12 key store
     * containing the key should be configured by means of the {@link #setKeyStorePath(String)}
     * and {@link #setKeyStorePassword(String)} methods.
     * 
     * @param keyPath The path.
     */
    public final void setKeyPath(final String keyPath) {
        this.keyPath = keyPath;
    }

    private static String fromChars(final char[] chars) {
        if (chars == null) {
            return null;
        } else {
            return String.valueOf(chars);
        }
    }

    static boolean hasPemFileSuffix(final String path) {
        return PATTERN_PEM.matcher(path).matches();
    }

    static boolean hasPkcsFileSuffix(final String path) {
        return PATTERN_PKCS.matcher(path).matches();
    }

    static boolean hasJksFileSuffix(final String path) {
        return PATTERN_JKS.matcher(path).matches();
    }
}