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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.CommonClientConfigs;
import org.eclipse.hono.util.Strings;

/**
 * Common Kafka client configuration properties.
 */
public class CommonKafkaClientConfigProperties {

    private final Map<String, String> commonClientConfig;

    /**
     * Creates an instance.
     */
    public CommonKafkaClientConfigProperties() {
        this.commonClientConfig = new HashMap<>();
    }

    /**
     * Creates an instance using existing options.
     *
     * @param commonOptions The common Kafka client options to use.
     * @throws NullPointerException if the options are {@code null}.
     */
    public CommonKafkaClientConfigProperties(final CommonKafkaClientOptions commonOptions) {
        commonClientConfig = ConfigOptionsHelper.toStringValueMap(commonOptions.commonClientConfig());
    }

    /**
     * Sets the common Kafka client config properties to be used.
     *
     * @param commonClientConfig The config properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    public final void setCommonClientConfig(final Map<String, String> commonClientConfig) {
        Objects.requireNonNull(commonClientConfig);
        this.commonClientConfig.putAll(commonClientConfig);
    }

    /**
     * Checks if a configuration has been set.
     *
     * @return {@code true} if the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} property has been
     *         configured with a non-empty value.
     */
    public final boolean isConfigured() {
        return !Strings.isNullOrEmpty(commonClientConfig.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
    }

    Map<String, String> getCommonClientConfig() {
        return commonClientConfig;
    }
}
