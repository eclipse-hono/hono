/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka;

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
public class KafkaAdminClientConfigProperties extends KafkaConfigProperties {

    /**
     * Gets the Kafka admin client configuration to which additional properties were applied. The following properties are
     * set here to the given configuration:
     * <ul>
     * <li>{@code client.id}=${unique client id}: the client id will be set to a unique value containing the already set
     * client id or alternatively the value set via {@link #setDefaultClientIdPrefix(String)}, the given adminClientName
     * and a newly created UUID.</li>
     * </ul>
     * Note: This method should be called for each new admin client, ensuring that a unique client id is used.
     *
     * @param adminClientName A name for the admin client to include in the added {@code client.id} property.
     * @return The admin client configuration properties.
     * @throws NullPointerException if adminClientName is {@code null}.
     */
    public final Map<String, String> getAdminClientConfig(final String adminClientName) {
        Objects.requireNonNull(adminClientName);

        final Map<String, String> newConfig = getConfig();

        setUniqueClientId(newConfig, adminClientName, AdminClientConfig.CLIENT_ID_CONFIG);
        return newConfig;
    }
}
