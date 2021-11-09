/*
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

package org.eclipse.hono.client.kafka;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.CommonClientConfigs;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common configuration properties for Kafka clients.
 */
public abstract class AbstractKafkaConfigProperties {

    /**
     * The name of the Kafka configuration property containing the servers to bootstrap from.
     */
    public static final String PROPERTY_BOOTSTRAP_SERVERS = "bootstrap.servers";

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Client id prefix to be applied if no {@code client.id} property is set in the common
     * or specific client config properties.
     */
    protected String defaultClientIdPrefix;

    private Map<String, String> commonClientConfig;
    private Map<String, String> specificClientConfig;

    /**
     * Sets the common Kafka client config to be used.
     *
     * @param commonConfig The config.
     * @throws NullPointerException if the config is {@code null}.
     */
    public final void setCommonClientConfig(final Map<String, String> commonConfig) {
        this.commonClientConfig = Objects.requireNonNull(commonConfig);
    }

    /**
     * Sets the Kafka client config properties to be used for a specific client.
     *
     * @param specificConfig The config properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    protected final void setSpecificClientConfig(final Map<String, String> specificConfig) {
        this.specificClientConfig = Objects.requireNonNull(specificConfig);
    }

    /**
     * Sets the prefix for the client ID that is passed to the Kafka server to allow application specific server-side
     * request logging.
     * <p>
     * If the common or specific client config already contains a value for key {@code client.id}, that one
     * will be used and the parameter here will be ignored.
     *
     * @param clientId The client ID prefix to set.
     * @throws NullPointerException if the client ID is {@code null}.
     */
    public final void setDefaultClientIdPrefix(final String clientId) {
        this.defaultClientIdPrefix = Objects.requireNonNull(clientId);
    }

    /**
     * Checks if a configuration has been set.
     *
     * @return {@code true} if the {@value #PROPERTY_BOOTSTRAP_SERVERS} property has been configured with a non-null
     *         value.
     */
    public final boolean isConfigured() {
        return containsMinimalConfiguration(commonClientConfig) || containsMinimalConfiguration(specificClientConfig);
    }

    /**
     * Gets the Kafka client configuration. This is the common configuration on which the client specific configuration
     * has been applied.
     *
     * An already set client id property or alternatively the value set via {@link #setDefaultClientIdPrefix(String)}
     * will be used as a prefix, to which the given clientName and a UUID will be added.
     *
     * @param clientName A name for the client to include in the added {@code client.id} property.
     * @return a copy of the client configuration with the applied properties.
     * @throws NullPointerException if clientName is {@code null}.
     */
    protected final Map<String, String> getConfig(final String clientName) {
        Objects.requireNonNull(clientName);

        final Map<String, String> newConfig = new HashMap<>();

        if (commonClientConfig != null) {
            newConfig.putAll(commonClientConfig);
        }

        // the client specific configuration may overwrite common properties
        if (specificClientConfig != null) {
            newConfig.putAll(specificClientConfig);
        }

        setUniqueClientId(newConfig, clientName);

        return newConfig;
    }

    /**
     * Overrides a property in the given map.
     *
     * @param config The map to set the property in.
     * @param key The key of the property.
     * @param value The property value.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final void overrideConfigProperty(final Map<String, String> config, final String key, final String value) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        log.trace("setting Kafka config property [{}={}]", key, value);
        final Object oldValue = config.put(key, value);
        if (oldValue != null) {
            log.debug("provided Kafka configuration contains property [{}={}], changing it to [{}]", key,
                    oldValue, value);
        }
    }

    private void setUniqueClientId(final Map<String, String> config, final String clientName) {

        final UUID uuid = UUID.randomUUID();
        final String clientIdPrefix = Optional.ofNullable(config.get(CommonClientConfigs.CLIENT_ID_CONFIG))
                .orElse(defaultClientIdPrefix);
        if (clientIdPrefix == null) {
            config.put(CommonClientConfigs.CLIENT_ID_CONFIG, String.format("%s-%s", clientName, uuid));
        } else {
            config.put(CommonClientConfigs.CLIENT_ID_CONFIG,
                    String.format("%s-%s-%s", clientIdPrefix, clientName, uuid));
        }
    }

    /**
     * Checks if a given set of properties contains the minimal required configuration.
     *
     * @param properties The properties to check.
     * @return {@code true} if the properties contain a non-null value for key {@value #PROPERTY_BOOTSTRAP_SERVERS}.
     */
    protected final boolean containsMinimalConfiguration(final Map<String, String> properties) {
        return Optional.ofNullable(properties)
                .map(props -> !Strings.isNullOrEmpty(props.get(PROPERTY_BOOTSTRAP_SERVERS)))
                .orElse(false);
    }
}
