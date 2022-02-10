/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.test;

import java.net.URL;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;

/**
 * Helper methods for working with SmallRye Config mappings.
 *
 */
public final class ConfigMappingSupport {

    private ConfigMappingSupport() {
        // prevent instantation
    }

    /**
     * Gets an object representation of YAML properties.
     *
     * @param <T> The type of the returned object.
     * @param mappedType The interface that the returned object is an instance of. The interface needs to be annotated
     *                   with {@code io.smallrye.config.ConfigMapping}.
     * @param yamlFileResource The resource to read the YAML properties from.
     * @return The object that is the result of mapping the YAML properties to the interface type.
     * @throws AssertionError if the YAML properties could not be mapped to the given type.
     */
    public static <T> T getConfigMapping(final Class<T> mappedType, final URL yamlFileResource) {
        try {
            final SmallRyeConfig config = new SmallRyeConfigBuilder()
                    .withSources(new YamlConfigSource(yamlFileResource))
                    .withMapping(mappedType)
                    .build();
            return config.getConfigMapping(mappedType);
        } catch (final Exception e) {
            throw new AssertionError("could not load YAML file", e);
        }
    }
}
