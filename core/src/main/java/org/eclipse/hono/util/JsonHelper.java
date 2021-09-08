/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

/**
 * A helper class for working with {@link JsonObject}s.
 */
public final class JsonHelper {

    private static final Logger LOG = LoggerFactory.getLogger(JsonHelper.class);

    private JsonHelper() {
    }

    /**
     * Gets a value from the given JSON object.
     *
     * @param jsonObject The JSON object to get the value from.
     * @param name The key to return the value for.
     * @param defaultValue A default value to return if the value is {@code null} or is of an unexpected type.
     * @param clazz The target type.
     * @param <T> The type of the value.
     * @return The value or the given default value if the value is not set or is of an unexpected type.
     * @throws NullPointerException if any of the parameters except defaultValue are {@code null}.
     */
    public static <T> T getValue(
            final JsonObject jsonObject,
            final String name,
            final Class<T> clazz,
            final T defaultValue) {

        Objects.requireNonNull(jsonObject);
        Objects.requireNonNull(name);
        Objects.requireNonNull(clazz);

        if (clazz == byte[].class) {
            try {
                final byte[] binaryValue = jsonObject.getBinary(name);
                if (binaryValue == null) {
                    return defaultValue;
                }
                return clazz.cast(binaryValue);
            } catch (final IllegalArgumentException | ClassCastException e) {
                LOG.debug("field [{}] does not contain a proper base64 string", name, e);
                return defaultValue;
            }
        }

        final Object value = jsonObject.getValue(name, defaultValue);
        if (value == null) {
            return defaultValue;
        }
        try {
            return clazz.cast(value);
        } catch (final ClassCastException e) {
            LOG.debug("unexpected value type for field [{}]: {}; expected: {}", name, value.getClass().getSimpleName(),
                    clazz.getSimpleName());
            return defaultValue;
        }
    }
}
