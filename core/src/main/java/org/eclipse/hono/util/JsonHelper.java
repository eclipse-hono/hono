/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
     * @throws NullPointerException if any of the parameters except defaultValue is {@code null}.
     */
    public static <T> T getValue(final JsonObject jsonObject, final String name, final Class<T> clazz,
            final T defaultValue) {
        Objects.requireNonNull(jsonObject);
        Objects.requireNonNull(name);
        Objects.requireNonNull(clazz);

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
