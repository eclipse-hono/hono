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
import java.util.stream.Collectors;

import io.smallrye.config.ConfigValue;

/**
 * Helper methods for config options.
 */
public final class ConfigOptionsHelper {

    private ConfigOptionsHelper() {
    }

    /**
     * Takes a map with values of type {@link ConfigValue} and converts it to a map with string values.
     *
     * @param config The map to be converted.
     * @return a map with the same keys as the input and the associated values as strings.
     */
    public static Map<String, String> toStringValueMap(final Map<String, ConfigValue> config) {
        return config.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
    }

}
