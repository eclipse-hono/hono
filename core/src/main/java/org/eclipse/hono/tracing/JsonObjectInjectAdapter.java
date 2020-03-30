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

import java.util.Objects;

import io.opentracing.propagation.TextMapInject;
import io.vertx.core.json.JsonObject;

/**
 * An adapter for injecting properties into a JSON object.
 *
 */
public class JsonObjectInjectAdapter implements TextMapInject {

    private final JsonObject jsonObject;

    /**
     * Creates an adapter for a JSON object.
     * 
     * @param jsonObject The JSON object.
     */
    public JsonObjectInjectAdapter(final JsonObject jsonObject) {
        this.jsonObject = Objects.requireNonNull(jsonObject);
    }

    @Override
    public void put(final String key, final String value) {
        jsonObject.put(key, value);
    }
}
