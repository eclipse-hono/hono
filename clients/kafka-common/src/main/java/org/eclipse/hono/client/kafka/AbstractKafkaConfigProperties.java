/*
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.CommonClientConfigs;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common configuration properties for Kafka clients.
 */
public abstract class AbstractKafkaConfigProperties {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    private static final String COMPONENT_UID_DEFAULT = getK8sComponentUId();

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * If set, this ID along with the incremented idCounter value will be used as client ID suffix.
     */
    private String componentUId;

    private Map<String, String> commonClientConfig;
    private Map<String, String> specificClientConfig;

    /**
     * Creates a new instance.
     */
    protected AbstractKafkaConfigProperties() {
        this.componentUId = COMPONENT_UID_DEFAULT;
    }

    private static String getK8sComponentUId() {
        if (System.getenv("KUBERNETES_SERVICE_HOST") != null) {
            // running in Kubernetes: use HOSTNAME env var containing the pod name
            final String podName = System.getenv("HOSTNAME");
            final String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            return String.format("%s_%s", podName, random);
        }
        return null;
    }

    /**
     * Sets the common Kafka client config to be used.
     *
     * @param commonConfig The config.
     * @throws NullPointerException if the config is {@code null}.
     */
    public final void setCommonClientConfig(final CommonKafkaClientConfigProperties commonConfig) {
        Objects.requireNonNull(commonConfig);
        this.commonClientConfig = commonConfig.getCommonClientConfig();
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
     * Sets the component unique ID to be included in the client ID along with a counter value.
     * <p>
     * This overrides the unique ID determined automatically in case this application is running in Kubernetes
     * (containing the Pod name then).
     * <p>
     * Setting {@code null} here means that a UUID will be used in the client ID instead of component unique ID and
     * counter value.
     * <p>
     * This method is mainly intended for usage in non-Kubernetes environments and unit tests.
     *
     * @param componentUId The component ID to use.
     */
    public final void overrideComponentUidUsedForClientId(final String componentUId) {
        this.componentUId = componentUId;
    }

    /**
     * Checks if a configuration has been set.
     *
     * @return {@code true} if the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} property has been configured
     *         with a non-empty value.
     */
    public final boolean isConfigured() {
        return containsMinimalConfiguration(commonClientConfig) || containsMinimalConfiguration(specificClientConfig);
    }

    /**
     * Gets the value of the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} property containing the servers to
     * bootstrap from.
     *
     * @return The servers or {@code null} if not set.
     */
    public String getBootstrapServers() {
        String result = null;
        if (specificClientConfig != null) {
            result = specificClientConfig.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
        }
        if (result == null && commonClientConfig != null) {
            result = commonClientConfig.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
        }
        return result;
    }

    /**
     * Gets the Kafka client configuration. This is the common configuration on which the client specific configuration
     * has been applied.
     * <p>
     * It is ensured that the returned map contains a unique {@code client.id}. The client ID will be created from the
     * given client name, followed by a unique ID (containing a component identifier if running in Kubernetes).
     * An already set {@code client.id} property value will be used as prefix for the client ID.
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
        final String uniqueClientIdSuffix = Optional.ofNullable(componentUId)
                .map(uid -> String.format("%s_%s", uid, ID_COUNTER.getAndIncrement()))
                .orElseGet(() -> UUID.randomUUID().toString());

        final String uniqueClientId = Optional.ofNullable(config.get(CommonClientConfigs.CLIENT_ID_CONFIG))
                .map(clientIdPrefix -> String.format("%s-%s-%s", clientIdPrefix, clientName, uniqueClientIdSuffix))
                .orElseGet(() -> String.format("%s-%s", clientName, uniqueClientIdSuffix));

        config.put(CommonClientConfigs.CLIENT_ID_CONFIG, uniqueClientId);
    }

    /**
     * Checks if a given set of properties contains the minimal required configuration.
     *
     * @param properties The properties to check.
     * @return {@code true} if the properties contain a non-empty value for key {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG}.
     */
    protected final boolean containsMinimalConfiguration(final Map<String, String> properties) {
        return Optional.ofNullable(properties)
                .map(props -> !Strings.isNullOrEmpty(props.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)))
                .orElse(false);
    }
}
