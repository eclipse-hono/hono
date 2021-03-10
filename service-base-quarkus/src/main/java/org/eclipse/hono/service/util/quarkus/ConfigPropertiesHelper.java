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


package org.eclipse.hono.service.util.quarkus;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for handling Quarkus configuration properties.
 *
 */
public final class ConfigPropertiesHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigPropertiesHelper.class);

    private ConfigPropertiesHelper() {
        // prevent instantiation
    }

    /**
     * Parses Quarkus configuration properties into a map.
     *
     * @param config The configuration properties.
     * @param configPrefix The prefix to filter for.
     * @return The parsed properties.
     */
    public static Map<String, String> createPropertiesFromConfig(final Config config, final String configPrefix) {
        final Map<String, String> properties = new HashMap<>();

        LOG.trace("Quarkus configuration properties with names: {}", config.getPropertyNames());

        StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                .filter(name -> name.startsWith(configPrefix))
                .distinct()
                .sorted()
                .forEach(name -> {
                    final String key = name.substring(configPrefix.length() + 1)
                            .toLowerCase()
                            .replaceAll("\"", "")
                            .replaceAll("[^a-z0-9.]", ".");
                    final String value = config.getOptionalValue(name, String.class).orElse("");
                    properties.put(key, value);
                });

        LOG.debug("configuration properties [prefix: {}]:{}{}",
                configPrefix, System.lineSeparator(), properties);
        return properties;
    }
}
