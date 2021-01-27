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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.admin.AdminClientConfig;

/**
 * Configuration properties for Kafka admin clients.
 * <p>
 * This class is intended to be as agnostic to the provided properties as possible in order to be forward-compatible
 * with changes in new versions of the Kafka client.
 *
 * @see <a href="https://kafka.apache.org/documentation/#adminclientconfigs">Kafka AdminClient Configs</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/telemetry-kafka">Telemetry API for Kafka Specification</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/event-kafka">Event API for Kafka Specification</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control-kafka/">Command &amp; Control API for Kafka Specification</a>
 */
public class KafkaAdminClientConfigProperties extends AbstractKafkaConfigProperties {

    private Map<String, String> adminClientConfig;

    /**
     * Sets the Kafka admin client config properties to be used.
     *
     * @param adminConfig The config properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    public final void setAdminClientConfig(final Map<String, String> adminConfig) {
        this.adminClientConfig = Objects.requireNonNull(adminConfig);
    }

    /**
     * Checks if a configuration has been set.
     *
     * @return true if configuration is present.
     */
    public final boolean isConfigured() {
        return commonClientConfig != null || adminClientConfig != null;
    }

    /**
     * Gets the Kafka admin client configuration to which additional properties were applied. The following properties are
     * set here to the given configuration:
     * <ul>
     * <li>{@code client.id} if the property is not already present in the configuration and a value has been set with
     * {@link #setDefaultClientIdPrefix(String)}, this value will be taken</li>
     * </ul>
     *
     * @return a copy of the admin client configuration with the applied properties or an empty map if neither an admin
     *         client configuration was set with {@link #setAdminClientConfig(Map)} nor common configuration properties were
     *         set with {@link #setCommonClientConfig(Map)}.
     */
    public final Map<String, String> getAdminClientConfig() {

        if (commonClientConfig == null && adminClientConfig == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> newConfig = new HashMap<>();
        if (commonClientConfig != null) {
            newConfig.putAll(commonClientConfig);
        }
        if (adminClientConfig != null) {
            newConfig.putAll(adminClientConfig);
        }

        if (defaultClientIdPrefix != null) {
            newConfig.putIfAbsent(AdminClientConfig.CLIENT_ID_CONFIG, defaultClientIdPrefix);
        }
        return newConfig;
    }

}
