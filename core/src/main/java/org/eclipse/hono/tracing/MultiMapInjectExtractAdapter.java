/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tracing;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;

import io.opentracing.propagation.TextMap;
import io.vertx.core.MultiMap;

/**
 * An adapter for injecting/extracting properties to/from a {@code MultiMap} object.
 *
 */
public class MultiMapInjectExtractAdapter implements TextMap {

    private final MultiMap multiMap;

    /**
     * Creates an adapter for a map.
     *
     * @param multiMap The map to inject/extract the properties to/from.
     */
    public MultiMapInjectExtractAdapter(final MultiMap multiMap) {
        this.multiMap = Objects.requireNonNull(multiMap);
    }

    @Override
    public void put(final String key, final String value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        multiMap.add(key, value);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        return multiMap.iterator();
    }
}
