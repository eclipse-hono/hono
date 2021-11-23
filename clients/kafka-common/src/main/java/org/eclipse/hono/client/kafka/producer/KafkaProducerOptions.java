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

package org.eclipse.hono.client.kafka.producer;

import java.util.Map;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigValue;

/**
 * Options for configuring Kafka producers.
 * <p>
 * This class with its {@link ConfigValue} (instead of String) Map values is needed to support empty String values. The
 * default value converters consider an empty String as a {@code null} value and then throw a
 * {@link java.util.NoSuchElementException}. Using {@link ConfigValue} bypasses usage of the converter and allows access
 * to the original value.
 * <p>
 * The prefix set here should not be used by production code. Instead, provide the prefix at the injection.
 */
@ConfigMapping(prefix = "hono.kafka", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface KafkaProducerOptions {

    /**
     * The properties to use for producers.
     *
     * @return The properties.
     */
    Map<String, ConfigValue> producerConfig();

}
