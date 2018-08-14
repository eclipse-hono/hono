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
 * An execution context that stores properties in a {@code Map}.
 *
 */
public class MapBasedExecutionContext implements ExecutionContext {

    private Map<String, Object> data;

    /**
     * Creates an empty execution context.
     * 
     * @return The new context.
     */
    public static ExecutionContext empty() {
        return new MapBasedExecutionContext();
    }

    /**
     * Creates an execution context for given set of properties.
     * 
     * @param props The properties to add to the new context.
     * @return The new context.
     */
    public static ExecutionContext of(final Map<String, Object> props) {
        final MapBasedExecutionContext result = new MapBasedExecutionContext();
        result.data = new HashMap<>(props);
        return result;
    }

    @Override
    public final <T> T get(final String key) {
        return get(key, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> T get(final String key, final T defaultValue) {
        return Optional.ofNullable(getData().get(key)).map(value -> {
            return (T) value;
        }).orElse(defaultValue);
    }

    @Override
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
