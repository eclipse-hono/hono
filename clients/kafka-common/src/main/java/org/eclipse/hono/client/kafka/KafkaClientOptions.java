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


package org.eclipse.hono.client.kafka;

import java.util.Map;

import io.smallrye.config.ConfigMapping;

/**
 * Common options for configuring Kafka clients.
 */
@ConfigMapping(prefix = "hono.kafka", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface KafkaClientOptions {

    /**
     * The properties shared by all types of clients.
     *
     * @return The properties.
     */
    Map<String, String> commonClientConfig();

    /**
     * The properties to use for admin clients.
     *
     * @return The properties.
     */
    Map<String, String> adminClientConfig();

    /**
     * The properties to use for consumers.
     *
     * @return The properties.
     */
    Map<String, String> consumerConfig();

    /**
     * The properties to use for producers.
     *
     * @return The properties.
     */
    Map<String, String> producerConfig();
}
