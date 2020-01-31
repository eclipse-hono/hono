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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

/**
 * Tests verifying the behavior of {@link JsonObjectInjectAdapter} and {@link JsonObjectExtractAdapter}.
 *
 */
public class JsonObjectInjectExtractAdapterTest {

    /**
     * Verifies that the same entries injected via the {@code JsonObjectInjectAdapter} are extracted via the
     * {@code JsonObjectExtractAdapter}.
     */
    @Test
    public void testInjectAndExtract() {
        final Map<String, String> testEntries = new HashMap<>();
        testEntries.put("key1", "value1");
        testEntries.put("key2", "value2");

        final JsonObject jsonObject = new JsonObject();
        final JsonObjectInjectAdapter injectAdapter = new JsonObjectInjectAdapter(jsonObject);
        testEntries.forEach((key, value) -> {
            injectAdapter.put(key, value);
        });

        final JsonObjectExtractAdapter extractAdapter = new JsonObjectExtractAdapter(jsonObject);
        extractAdapter.iterator().forEachRemaining(extractedEntry -> {
            assertThat(extractedEntry.getValue()).isEqualTo(testEntries.get(extractedEntry.getKey()));
        });
    }

    /**
     * Verifies that extracting entries from an empty JSON object returns an empty iterator.
     */
    @Test
    public void testExtractWithEmptyIterator() {
        final JsonObject jsonObject = new JsonObject();
        final JsonObjectExtractAdapter extractAdapter = new JsonObjectExtractAdapter(jsonObject);
        assertThat(extractAdapter.iterator().hasNext()).isFalse();
    }
}
