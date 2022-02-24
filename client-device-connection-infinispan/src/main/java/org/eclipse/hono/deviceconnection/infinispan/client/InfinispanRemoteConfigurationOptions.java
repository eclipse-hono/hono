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


package org.eclipse.hono.deviceconnection.infinispan.client;

import java.util.Map;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;

/**
 * Options for configuring a Hotrod connection to a remote cache.
 *
 */
@ConfigMapping(prefix = "hono.cache.infinispan", namingStrategy = NamingStrategy.VERBATIM)
public interface InfinispanRemoteConfigurationOptions {

    /**
     * Gets the connection pool options.
     *
     * @return The options.
     */
    Map<String, String> connectionPool();

    /**
     * Gets the default executor factory options.
     *
     * @return The options.
     */
    Map<String, String> defaultExecutorFactory();

    /**
     * Gets the SASL properties.
     *
     * @return The properties.
     */
    Map<String, String> saslProperties();

    /**
     * Gets the cluster options.
     *
     * @return The options.
     */
    Map<String, String> cluster();

    /**
     * Gets the list of remote servers as a string of the form <em>host1[:port][;host2[:port]]</em>.
     *
     * @return The servers.
     */
    Optional<String> serverList();

    /**
     * Gets the auth server name.
     *
     * @return The server name.
     */
    Optional<String> authServerName();

    /**
     * Gets the user name to use for authentication.
     *
     * @return The user name.
     */
    Optional<String> authUsername();

    /**
     * Gets the password to use for authentication.
     *
     * @return The password.
     */
    Optional<String> authPassword();

    /**
     * Gets the auth realm (for DIGEST-MD5 authentication).
     *
     * @return The realm.
     */
    Optional<String> authRealm();

    /**
     * Gets the SASL mechanism to use for authentication.
     *
     * @return The mechanism.
     */
    Optional<String> saslMechanism();

    /**
     * Gets the socket timeout.
     *
     * @return The timeout.
     */
    @WithDefault("60000")
    int socketTimeout();

    /**
     * Gets the connect timeout.
     *
     * @return The timeout.
     */
    @WithDefault("60000")
    int connectTimeout();

    /**
     * Gets the path of the trust store.
     *
     * @return The path.
     */
    Optional<String> trustStorePath();

    /**
     * Gets the trust store file name.
     *
     * @return The file name.
     */
    Optional<String> trustStoreFileName();

    /**
     * Gets the type of the trust store (JKS, JCEKS, PCKS12 or PEM).
     *
     * @return The type.
     */
    Optional<String> trustStoreType();

    /**
     * Gets the password of the trust store.
     *
     * @return The password.
     */
    Optional<String> trustStorePassword();

    /**
     * Gets the file name of a keystore to use when using client certificate authentication.
     *
     * @return The file name.
     */
    Optional<String> keyStoreFileName();

    /**
     * Gets the keystore type.
     *
     * @return The type.
     */
    Optional<String> keyStoreType();

    /**
     * Gets the keystore password.
     *
     * @return The password.
     */
    Optional<String> keyStorePassword();

    /**
     * Gets the key alias.
     *
     * @return The alias.
     */
    Optional<String> keyAlias();

    /**
     * Gets the certificate password in the keystore.
     *
     * @return The password.
     */
    Optional<String> keyStoreCertificatePassword();

    /**
     * Checks whether TLS is enabled.
     *
     * @return {@code true} if TLS is enabled.
     */
    @WithDefault("false")
    boolean useSsl();

    /**
     * Gets the list of ciphers, separated with spaces and in order of preference, that are used during the TLS
     * handshake.
     *
     * @return The ciphers.
     */
    Optional<String> sslCiphers();

    /**
     * Gets the TLS protocol to use (e.g. TLSv1.2).
     *
     * @return The protocol.
     */
    Optional<String> sslProtocol();
}
