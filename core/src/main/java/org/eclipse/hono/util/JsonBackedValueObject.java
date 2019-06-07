/**
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
 */

package org.eclipse.hono.util;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.vertx.core.json.JsonObject;

/**
 * A base class for implementing value objects.
 * <p>
 * Uses a JSON object for storing arbitrary properties.
 */
abstract class JsonBackedValueObject {

    /**
     * The JSON object to map all values to/from.
     */
    @JsonIgnore
    protected final JsonObject json = new JsonObject();

    /**
     * Gets a map of this tenant's properties that should be included
     * in serialization to JSON in addition to the explicitly annotated
     * properties.
     * 
     * @return The properties.
     */
    @JsonAnyGetter
    private Map<String, Object> getPropertiesAsMap() {
        return json.getMap();
    }

    /**
     * Gets a property value.
     * 
     * @param name The property name.
     * @param clazz The target type.
     * @param <T> The type of the property.
     * @return The property value or {@code null} if the property is not set or is of an unexpected type.
     * @throws NullPointerException if name is {@code null}.
     */
    public final <T> T getProperty(final String name, final Class<T> clazz) {
        return getProperty(json, name, clazz);
    }

    /**
     * Gets a property value.
     * 
     * @param name The property name.
     * @param defaultValue A default value to return if the property is {@code null}.
     * @param <T> The type of the property.
     * @param clazz The target type.
     * @return The property value or the default value if the property is not set or is of an unexpected type.
     * @throws NullPointerException if name is {@code null}.
     */
    public final <T> T getProperty(final String name, final Class<T> clazz, final T defaultValue) {
        return getProperty(json, name, clazz, defaultValue);
    }

    /**
     * Gets a property value.
     * 
     * @param parent The JSON to get the property value from.
     * @param name The property name.
     * @param clazz The target type.
     * @param <T> The type of the property.
     * @return The property value or {@code null} if the property is not set or is of an unexpected type.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected static final <T> T getProperty(final JsonObject parent, final String name, final Class<T> clazz) {
        return getProperty(parent, name, clazz, null);
    }

    /**
     * Gets a property value.
     * 
     * @param parent The JSON to get the property value from.
     * @param name The property name.
     * @param defaultValue A default value to return if the property is {@code null}.
     * @param clazz The target type.
     * @param <T> The type of the property.
     * @return The property value or the given default value if the property is not set or is of an unexpected type.
     * @throws NullPointerException if any of parent or name are {@code null}.
     */
    protected static final <T> T getProperty(final JsonObject parent, final String name, final Class<T> clazz,
            final T defaultValue) {
        final Object value = parent.getValue(Objects.requireNonNull(name), defaultValue);
        if (value == null) {
            return defaultValue;
        }
        try {
            return clazz.cast(value);
        } catch (final ClassCastException e) {
            return defaultValue;
        }
    }
}
