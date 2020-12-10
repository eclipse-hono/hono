/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.management.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Filter.Operator;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;


/**
 * Tests verifying behavior of {@link Filter}.
 *
 */
class FilterTest {

    @Test
    void testDecodingSucceeds() {

        // String property
        Filter filter = Json.decodeValue("{\"field\":\"/manufacturer\",\"value\":\"ACME*\"}", Filter.class);
        assertThat(filter.getField().toString()).isEqualTo("/manufacturer");
        assertThat(filter.getValue()).isEqualTo("ACME*");
        assertThat(filter.getOperator()).isEqualTo(Operator.eq);

        // Number property
        filter = Json.decodeValue("{\"field\":\"/since\",\"value\":1991,\"op\":\"eq\"}", Filter.class);
        assertThat(filter.getField().toString()).isEqualTo("/since");
        assertThat(filter.getValue()).isEqualTo(1991);
        assertThat(filter.getOperator()).isEqualTo(Operator.eq);

        // Boolean property
        filter = Json.decodeValue("{\"field\":\"/available\",\"value\":true}", Filter.class);
        assertThat(filter.getField().toString()).isEqualTo("/available");
        assertThat(filter.getValue()).isEqualTo(Boolean.TRUE);
        assertThat(filter.getOperator()).isEqualTo(Operator.eq);
    }

    @Test
    void testDecodingFailsForMissingField() {

        assertThatThrownBy(() -> Json.decodeValue("{\"value\":\"ACME*\"}", Filter.class))
            .isInstanceOf(DecodeException.class);
    }

    @Test
    void testDecodingFailsForMissingValue() {

        assertThatThrownBy(() -> Json.decodeValue("{\"field\":\"/manufacturer\"}", Filter.class))
            .isInstanceOf(DecodeException.class);
    }

    @Test
    void testDecodingFailsForUnknownField() {

        assertThatThrownBy(() -> Json.decodeValue(
                "{\"field\":\"/manufacturer\",\"value\":\"ACME*\",\"unknown\":10}",
                Filter.class))
            .isInstanceOf(DecodeException.class);
    }

    @Test
    void testDecodingFailsForUnknownOperator() {

        assertThatThrownBy(() -> Json.decodeValue(
                "{\"field\":\"/manufacturer\",\"value\":\"ACME*\",\"op\":\"unknown\"}",
                Filter.class))
            .isInstanceOf(DecodeException.class);
    }

    @Test
    void testDecodingFailsForNonJsonPointer() {

        assertThatThrownBy(() -> Json.decodeValue(
                "{\"field\":\"manufacturer\",\"value\":\"ACME*\"}",
                Filter.class))
            .isInstanceOf(DecodeException.class);
    }
}
