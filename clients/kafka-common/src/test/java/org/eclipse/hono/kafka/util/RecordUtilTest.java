/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.kafka.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.Json;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Verifies the behavior of {@link RecordUtil}.
 */
public class RecordUtilTest {

    /**
     * Verifies that <em>getHeaderValue</em> returns a header value of type String.
     */
    @Test
    public void testGetHeaderValueWithStringValue() {
        final String headerName = "header";
        final String headerValue = "value";
        final List<KafkaHeader> headers = List.of(KafkaHeader.header(headerName, headerValue));
        assertThat(RecordUtil.getHeaderValue(headers, headerName, String.class)).isEqualTo(headerValue);
    }

    /**
     * Verifies that <em>getHeaderValue</em> returns a header value of type String.
     */
    @Test
    public void testGetHeaderValueWithBooleanValue() {
        final String headerName = "header";
        final boolean headerValue = true;
        final List<KafkaHeader> headers = List.of(KafkaHeader.header(headerName, Json.encode(headerValue)));
        assertThat(RecordUtil.getHeaderValue(headers, headerName, Boolean.class)).isEqualTo(headerValue);
    }

    /**
     * Verifies that <em>getHeaderValue</em> returns {@code null} for multiple
     * headers with matching name.
     */
    @Test
    public void testGetHeaderValueWithDuplicateHeader() {
        final String headerName = "header";
        final String headerValue = "value";
        final List<KafkaHeader> headers = List.of(KafkaHeader.header(headerName, headerValue),
                KafkaHeader.header(headerName, headerValue));
        assertThat(RecordUtil.getHeaderValue(headers, headerName, String.class)).isNull();
    }
}
