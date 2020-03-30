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

import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;

/**
 * Tests verifying the behavior of {@link MultiMapInjectExtractAdapter}.
 *
 */
public class MultiMapInjectExtractAdapterTest {

    /**
     * Verifies that the same entries injected via the {@code MultiMapInjectAdapter} are extracted via the
     * {@code MultiMapExtractAdapter}.
     */
    @Test
    public void testInjectAndExtract() {
        final Map<String, String> testEntries = new HashMap<>();
        testEntries.put("key1", "value1");
        testEntries.put("key2", "value2");

        final MultiMap multiMap = new CaseInsensitiveHeaders();
        final MultiMapInjectExtractAdapter adapter = new MultiMapInjectExtractAdapter(multiMap);
        testEntries.forEach((key, value) -> {
            adapter.put(key, value);
        });

        adapter.iterator().forEachRemaining(extractedEntry -> {
            assertThat(extractedEntry.getValue()).isEqualTo(testEntries.get(extractedEntry.getKey()));
        });
    }
}
