/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.management;

import static org.junit.jupiter.api.Assertions.assertAll;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.service.management.Sort.Direction;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;


/**
 * Tests verifying behavior of {@link Sort}.
 *
 */
public class SortTest {

    /**
     * Verifies that sort options can be created from JSON.
     */
    @Test
    public void testDecodingOfSortOptionsSucceeds() {

        final var options = new JsonObject().put("field", "/name").put("direction", "desc");
        final Sort sortOption = options.mapTo(Sort.class);
        assertAll(
                () -> assertThat(sortOption.getDirection()).isEqualTo(Direction.DESC),
                () -> assertThat(sortOption.getField().toString()).isEqualTo("/name"));
    }

}
