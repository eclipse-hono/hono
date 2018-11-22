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

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import io.opentracing.propagation.TextMap;
import io.vertx.core.json.JsonObject;

/**
 * An adapter for injecting properties into a JSON object.
 *
 */
public class JsonObjectInjectAdapter implements TextMap {

    private final JsonObject jsonObject;
    private final String propertiesObjectKey;

    /**
     * Creates an adapter for a JSON object.
     * 
     * @param jsonObject The JSON object.
     * @param propertiesObjectKey The key under which the properties object is to be stored in the JSON object.
     */
    public JsonObjectInjectAdapter(final JsonObject jsonObject, final String propertiesObjectKey) {
        this.jsonObject = Objects.requireNonNull(jsonObject);
        this.propertiesObjectKey = Objects.requireNonNull(propertiesObjectKey);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void put(final String key, final String value) {
        Optional.ofNullable(jsonObject.getJsonObject(propertiesObjectKey)).orElseGet(() -> {
            final JsonObject propertiesContainer = new JsonObject();
            jsonObject.put(propertiesObjectKey, propertiesContainer);
            return propertiesContainer;
        }).put(key, value);
    }
}
