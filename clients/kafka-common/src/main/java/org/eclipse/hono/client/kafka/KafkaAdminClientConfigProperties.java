/*
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

package org.eclipse.hono.client.kafka;

import java.util.Map;
import java.util.Objects;

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
// When renaming or moving this class, please update it in the documentation
public class KafkaAdminClientConfigProperties extends AbstractKafkaConfigProperties {

    /**
     * Creates an instance.
     */
    public KafkaAdminClientConfigProperties() {
    }

    /**
     * Creates an instance using existing options.
     *
     * @param commonOptions The common Kafka client options to use.
     * @param options The producer options to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaAdminClientConfigProperties(final CommonKafkaClientOptions commonOptions,
            final KafkaAdminClientOptions options) {

        Objects.requireNonNull(commonOptions);
        Objects.requireNonNull(options);

        final CommonKafkaClientConfigProperties commonConfig = new CommonKafkaClientConfigProperties(commonOptions);
        setCommonClientConfig(commonConfig);
        setSpecificClientConfig(ConfigOptionsHelper.toStringValueMap(options.adminClientConfig()));
    }

    /**
     * Sets the Kafka admin client config properties to be used.
     *
     * @param adminConfig The config properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    public final void setAdminClientConfig(final Map<String, String> adminConfig) {
        setSpecificClientConfig(adminConfig);
    }

    /**
     * Gets the Kafka admin client configuration. This is the result of applying the admin client configuration on the
     * common configuration.
     * <p>
     * It is ensured that the returned map contains a unique {@code client.id}. The client ID will be created from the
     * given client name, followed by a unique ID (containing component identifiers if running in Kubernetes).
     * An already set {@code client.id} property value will be used as prefix for the client ID.
     *
     * Note: This method should be called for each new admin client, ensuring that a unique client id is used.
     *
     * @param adminClientName A name for the admin client to include in the added {@code client.id} property.
     * @return a copy of the admin client configuration with the applied properties.
     * @throws NullPointerException if adminClientName is {@code null}.
     */
    public final Map<String, String> getAdminClientConfig(final String adminClientName) {

        return getConfig(adminClientName);
    }

}
