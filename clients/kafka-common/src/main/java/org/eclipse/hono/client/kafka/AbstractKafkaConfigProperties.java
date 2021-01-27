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

import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common configuration properties for Kafka clients.
 */
public abstract class AbstractKafkaConfigProperties {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Common client config properties.
     */
    protected Map<String, String> commonClientConfig;
    /**
     * Client id prefix to be applied if no {@code client.id} property is set in the common
     * or specific client config properties.
     */
    protected String defaultClientIdPrefix;

    /**
     * Sets the common Kafka client config properties to be used.
     *
     * @param commonClientConfig The config properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    public final void setCommonClientConfig(final Map<String, String> commonClientConfig) {
        this.commonClientConfig = Objects.requireNonNull(commonClientConfig);
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

}
