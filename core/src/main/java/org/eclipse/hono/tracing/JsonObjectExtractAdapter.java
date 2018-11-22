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

package org.eclipse.hono.tracing;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;

import io.opentracing.propagation.TextMap;
import io.vertx.core.json.JsonObject;

/**
 * An adapter for extracting properties from a JSON object.
 *
 */
public class JsonObjectExtractAdapter implements TextMap {

    private final JsonObject jsonObject;
    private final String propertiesObjectKey;

    /**
     * Creates an adapter for a JSON object.
     * 
     * @param jsonObject The JSON object.
     * @param propertiesObjectKey The key under which the properties object is stored in the JSON object.
     */
    public JsonObjectExtractAdapter(final JsonObject jsonObject, final String propertiesObjectKey) {
        this.jsonObject = Objects.requireNonNull(jsonObject);
        this.propertiesObjectKey = Objects.requireNonNull(propertiesObjectKey);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {

        final JsonObject propertiesContainer = jsonObject.getJsonObject(propertiesObjectKey);
        if (propertiesContainer == null) {
            return Collections.emptyIterator();
        }
        final Iterator<Entry<String, Object>> propertiesIterator = propertiesContainer.iterator();
        return new Iterator<Entry<String, String>>() {

            @Override
            public boolean hasNext() {
                return propertiesIterator.hasNext();
            }

            @Override
            public Entry<String, String> next() {
                final Entry<String, Object> nextEntry = propertiesIterator.next();
                return new AbstractMap.SimpleEntry<>(nextEntry.getKey(),
                        nextEntry.getValue().toString());
            }
        };
    }

    @Override
    public void put(final String key, final String value) {
        throw new UnsupportedOperationException();
    }

}
