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
import java.util.Objects;

import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;

/**
 * Helper methods for working with SmallRye Config mappings.
 *
 */
public final class ConfigMappingSupport {

    private ConfigMappingSupport() {
        // prevent instantiation
    }

    /**
     * Gets an object representation of YAML properties.
     *
     * @param <T> The type of the returned object.
     * @param mappedType The interface that the returned object is an instance of. The interface needs to be annotated
     *                   with {@code io.smallrye.config.ConfigMapping}.
     * @param yamlFileResource The resource to read the YAML properties from.
     * @return The object that is the result of mapping the YAML properties to the interface type.
     * @throws NullPointerException if mapped type or resource URL are {@code null}.
     * @throws AssertionError if the YAML properties could not be mapped to the given type.
     */
    public static <T> T getConfigMapping(final Class<T> mappedType, final URL yamlFileResource) {

        return getConfigMapping(mappedType, yamlFileResource, null);
    }

    /**
     * Gets an object representation of YAML properties.
     *
     * @param <T> The type of the returned object.
     * @param mappedType The interface that the returned object is an instance of. The interface needs to be annotated
     *                   with {@code io.smallrye.config.ConfigMapping}.
     * @param yamlFileResource The resource to read the YAML properties from.
     * @param prefix The prefix to use for mapping the properties or {@code null} if the type's default prefix should
     *               be used.
     * @return The object that is the result of mapping the YAML properties to the interface type.
     * @throws NullPointerException if mapped type or resource URL are {@code null}.
     * @throws AssertionError if the YAML properties could not be mapped to the given type.
     */
    public static <T> T getConfigMapping(final Class<T> mappedType, final URL yamlFileResource, final String prefix) {

        Objects.requireNonNull(mappedType);
        Objects.requireNonNull(yamlFileResource);

        try {
            final var builder = new SmallRyeConfigBuilder()
                    .withSources(new YamlConfigSource(yamlFileResource))
                    .withValidateUnknown(false);
            if (prefix == null) {
                builder.withMapping(mappedType);
                return builder.build().getConfigMapping(mappedType);
            } else {
                builder.withMapping(mappedType, prefix);
                return builder.build().getConfigMapping(mappedType, prefix);
            }
        } catch (final Exception e) {
            throw new AssertionError("could not load YAML file", e);
        }
    }
}
