/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
     * Gets the options.
     *
     * @return The options.
     */
    Map<String, String> connectionPool();

    /**
     * Gets the options.
     *
     * @return The options.
     */
    Map<String, String> saslProperties();

    /**
     * Gets the options.
     *
     * @return The options.
     */
    Map<String, String> cluster();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> serverList();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> authServerName();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> authUsername();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> authPassword();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> authRealm();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> saslMechanism();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    @WithDefault("60000")
    int socketTimeout();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    @WithDefault("60000")
    int connectTimeout();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> trustStorePath();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> trustStoreFileName();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> trustStoreType();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> trustStorePassword();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> keyStoreFileName();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> keyStoreType();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> keyStorePassword();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> keyAlias();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    Optional<String> keyStoreCertificatePassword();

    /**
     * Gets the value.
     *
     * @return The value.
     */
    @WithDefault("false")
    boolean useSsl();
}
