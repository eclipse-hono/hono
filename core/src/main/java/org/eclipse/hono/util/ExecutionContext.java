/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A context that can be used to pass around arbitrary key/value pairs.
 *
 */
public class ExecutionContext {

    private Map<String, Object> data;

    /**
     * Creates an empty execution context.
     * 
     * @return The new context.
     */
    public static ExecutionContext empty() {
        return new ExecutionContext();
    }

    /**
     * Creates an execution context for given set of properties.
     * 
     * @param props The properties to add to the new context.
     * @return The new context.
     */
    public static ExecutionContext of(final Map<String, Object> props) {
        final ExecutionContext result = new ExecutionContext();
        result.data = new HashMap<>(props);
        return result;
    }

    /**
     * Gets the value for a key.
     * 
     * @param <T> The type of the value.
     * @param key The key to get the value for.
     * @return The value or {@code null} if the key is unknown.
     */
    public final <T> T get(final String key) {
        return get(key, null);
    }

    /**
     * Gets the value for a key.
     * 
     * @param <T> The type of the value.
     * @param key The key to get the value for.
     * @param defaultValue The value to return if the key is unknown.
     * @return The value.
     */
    @SuppressWarnings("unchecked")
    public final <T> T get(final String key, final T defaultValue) {
        return Optional.ofNullable(getData().get(key)).map(value -> {
            return (T) value;
        }).orElse(defaultValue);
    }

    /**
     * Sets a value for a key.
     * 
     * @param key The key.
     * @param value The value.
     */
    public final void put(final String key, final Object value) {
        getData().put(key, value);
    }

    /**
     * Gets the properties stored in this context.
     * 
     * @return An unmodifiable view on this context's properties.
     */
    public final Map<String, Object> asMap() {
        return Collections.unmodifiableMap(getData());
    }

    private Map<String, Object> getData() {
        return Optional.ofNullable(data).orElseGet(() -> {
            data = new HashMap<>();
            return data;
        });
    }
}
