/*
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

package org.eclipse.hono.client.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.hono.client.kafka.KafkaMessageHelper;
import org.eclipse.hono.util.QoS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Verifies the behavior of {@link KafkaMessageHelper}.
 */
public class KafkaMessageHelperTest {

    private static final String KEY = "the-key";

    private List<KafkaHeader> headers;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        headers = new ArrayList<>();
    }

    /**
     * Verifies that {@link KafkaMessageHelper#decode(Buffer, Class)} returns {@code null} if the parameter is null.
     */
    @Test
    public void testThatDecodeReturnsNullIfNoBufferPresent() {
        assertThat(KafkaMessageHelper.decode((Buffer) null, String.class)).isNull();
    }

    /**
     * Verifies that {@link KafkaMessageHelper#decode(KafkaHeader, Class)} returns {@code null} if the parameter is
     * null.
     */
    @Test
    public void testThatDecodeReturnsNullIfNoHeaderPresent() {
        assertThat(KafkaMessageHelper.decode((KafkaHeader) null, String.class)).isNull();
    }

    /**
     * Verifies that {@link KafkaMessageHelper#getHeaderValue(List, String, Class)} returns the value of a String
     * header.
     */
    @Test
    public void testGetStringHeader() {
        final String stringValue = "a-value";
        headers.add(KafkaMessageHelper.createKafkaHeader(KEY, stringValue));

        assertThat(KafkaMessageHelper.getHeaderValue(headers, KEY, String.class))
                .isEqualTo(Optional.of(stringValue));
    }

    /**
     * Verifies that {@link KafkaMessageHelper#getHeaderValue(List, String, Class)} returns the value of an Integer
     * header.
     */
    @Test
    public void testGetIntegerHeader() {
        final int intValue = 1;
        headers.add(KafkaMessageHelper.createKafkaHeader(KEY, intValue));

        assertThat(KafkaMessageHelper.getHeaderValue(headers, KEY, Integer.class)).isEqualTo(Optional.of(intValue));
    }

    /**
     * Verifies that {@link KafkaMessageHelper#decode(KafkaHeader, Class)} returns {@code null} if the value of the
     * header cannot be decoded as JSON and is not expected to be a String.
     */
    @Test
    public void testThatDecodeReturnsNullForWrongSerialisation() {
        assertThat(KafkaMessageHelper.decode(KafkaHeader.header(KEY, "{invalid: json}"), Object.class)).isNull();
    }

    /**
     * Verifies that {@link KafkaMessageHelper#getHeaderValue(List, String, Class)} returns an empty optional if the key
     * is not present in the given headers.
     */
    @Test
    public void testThatNonExistingHeaderReturnsEmptyOptional() {
        assertThat(KafkaMessageHelper.getHeaderValue(null, "foo", String.class)).isEmpty();
        assertThat(KafkaMessageHelper.getHeaderValue(headers, "foo", String.class)).isEmpty();
    }

    /**
     * Verifies that {@link KafkaMessageHelper#getHeaderValue(List, String, Class)} returns an empty optional if the
     * value of the header cannot be decoded as JSON and is not expected to be a String.
     */
    @Test
    public void testThatWrongSerialisationReturnsEmptyOptional() {
        headers.add(KafkaHeader.header(KEY, "{invalid: json}"));
        assertThat(KafkaMessageHelper.getHeaderValue(headers, KEY, Object.class)).isEmpty();
    }

    /**
     * Verifies that {@link KafkaMessageHelper#getHeaderValue(List, String, Class)} returns an empty optional if the
     * value of the header is not of the expected type.
     */
    @Test
    public void testThatWrongTypeReturnsEmptyOptional() {
        headers.add(KafkaMessageHelper.createKafkaHeader(KEY, "notANumber"));
        assertThat(KafkaMessageHelper.getHeaderValue(headers, KEY, Integer.class)).isEmpty();
    }

    /**
     * Verifies that {@link KafkaMessageHelper#getContentType(List)} returns the content type.
     */
    @Test
    public void testGetContentType() {
        final String contentType = "application/json";
        headers.add(KafkaMessageHelper.createKafkaHeader("content-type", contentType));

        assertThat(KafkaMessageHelper.getContentType(headers)).isEqualTo(Optional.of(contentType));
    }

    /**
     * Verifies that {@link KafkaMessageHelper#getQoS(List)} returns the QoS.
     */
    @Test
    public void testGetQoS() {
        final QoS qos = QoS.AT_MOST_ONCE;
        headers.add(KafkaMessageHelper.createKafkaHeader("qos", qos.ordinal()));

        assertThat(KafkaMessageHelper.getQoS(headers)).isEqualTo(Optional.of(qos));
    }
}
