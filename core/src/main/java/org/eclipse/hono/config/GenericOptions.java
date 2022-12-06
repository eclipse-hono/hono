/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.config;

import java.util.List;
import java.util.Optional;

import org.eclipse.hono.util.Constants;

import io.smallrye.config.WithDefault;

/**
 * Generic configuration options shared by clients and server components.
 *
 */
public interface GenericOptions {

    /**
     * Gets the character separating the segments of target addresses.
     *
     * @return The separator.
     */
    @WithDefault(Constants.DEFAULT_PATH_SEPARATOR)
    String pathSeparator();

    /**
     * Gets the path to the key store to load certificates of trusted CAs from.
     *
     * @return The absolute path.
     */
    Optional<String> trustStorePath();

    /**
     * Gets the password for accessing the key store containing the certificates of trusted CAs.
     *
     * @return The password.
     */
    Optional<String> trustStorePassword();

    /**
     * Gets the absolute path to the key store containing the private key
     * and certificate chain that will be used for authentication to peers.
     *
     * @return The path.
     */
    Optional<String> keyStorePath();

    /**
     * Gets the password for the key store containing the private key and certificate chain that should be used
     * for authentication to peers.
     *
     * @return The password.
     */
    Optional<String> keyStorePassword();

    /**
     * Gets the absolute path to the PEM file containing the X.509 certificate chain for the RSA private key that should
     * be used for authentication to peers.
     *
     * @return The path or {@code null} if no path has been set.
     */
    Optional<String> certPath();

    /**
     * Gets the absolute path to the PEM file containing the RSA private key that will be used for authentication to
     * peers.
     *
     * @return The path or {@code null} if no path has been set.
     */
    Optional<String> keyPath();

    /**
     * Get the specified format of the trust store.
     *
     * @return The format or {@code null} if auto-detection should be tried.
     */
    Optional<FileFormat> trustStoreFormat();

    /**
     * Get the specified format of the key files.
     *
     * @return The format or {@code null} if auto-detection should be tried.
     */
    Optional<FileFormat> keyFormat();

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
    @WithDefault("TLSv1.2,TLSv1.3")
    List<String> secureProtocols();

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
    Optional<List<String>> supportedCipherSuites();
}
