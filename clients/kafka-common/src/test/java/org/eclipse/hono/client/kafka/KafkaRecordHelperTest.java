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

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.hono.util.QoS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Verifies the behavior of {@link KafkaRecordHelper}.
 */
public class KafkaRecordHelperTest {

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
     * Verifies that {@link KafkaRecordHelper#decode(Buffer, Class)} returns {@code null} if the parameter is null.
     */
    @Test
    public void testThatDecodeReturnsNullIfNoBufferPresent() {
        assertThat(KafkaRecordHelper.decode((Buffer) null, String.class)).isNull();
    }

    /**
     * Verifies that {@link KafkaRecordHelper#decode(KafkaHeader, Class)} returns {@code null} if the parameter is
     * null.
     */
    @Test
    public void testThatDecodeReturnsNullIfNoHeaderPresent() {
        assertThat(KafkaRecordHelper.decode((KafkaHeader) null, String.class)).isNull();
    }

    /**
     * Verifies that {@link KafkaRecordHelper#getHeaderValue(List, String, Class)} returns the value of a String
     * header.
     */
    @Test
    public void testGetStringHeader() {
        final String stringValue = "a-value";
        headers.add(KafkaRecordHelper.createKafkaHeader(KEY, stringValue));

        assertThat(KafkaRecordHelper.getHeaderValue(headers, KEY, String.class))
                .isEqualTo(Optional.of(stringValue));
    }

    /**
     * Verifies that {@link KafkaRecordHelper#getHeaderValue(List, String, Class)} returns the value of an Integer
     * header.
     */
    @Test
    public void testGetIntegerHeader() {
        final int intValue = 1;
        headers.add(KafkaRecordHelper.createKafkaHeader(KEY, intValue));

        assertThat(KafkaRecordHelper.getHeaderValue(headers, KEY, Integer.class)).isEqualTo(Optional.of(intValue));
    }


    /**
     * Verifies that {@link KafkaRecordHelper#getHeaderValue(List, String, Class)} returns a header value 
     * of type Boolean.
     */
    @Test
    public void testGetHeaderValueWithBooleanValue() {
        final boolean booleanValue = true;
        headers.add(KafkaRecordHelper.createKafkaHeader(KEY, booleanValue));

        assertThat(KafkaRecordHelper.getHeaderValue(headers, KEY, Boolean.class)).isEqualTo(Optional.of(booleanValue));
    }

    /**
     * Verifies that {@link KafkaRecordHelper#decode(KafkaHeader, Class)} returns {@code null} if the value of the
     * header cannot be decoded as JSON and is not expected to be a String.
     */
    @Test
    public void testThatDecodeReturnsNullForWrongSerialisation() {
        assertThat(KafkaRecordHelper.decode(KafkaHeader.header(KEY, "{invalid: json}"), Object.class)).isNull();
    }

    /**
     * Verifies that {@link KafkaRecordHelper#getHeaderValue(List, String, Class)} returns an empty optional if the key
     * is not present in the given headers.
     */
    @Test
    public void testThatNonExistingHeaderReturnsEmptyOptional() {
        assertThat(KafkaRecordHelper.getHeaderValue(null, "foo", String.class).isEmpty()).isTrue();
        assertThat(KafkaRecordHelper.getHeaderValue(headers, "foo", String.class).isEmpty()).isTrue();
    }

    /**
     * Verifies that {@link KafkaRecordHelper#getHeaderValue(List, String, Class)} returns an empty optional if the
     * value of the header cannot be decoded as JSON and is not expected to be a String.
     */
    @Test
    public void testThatWrongSerialisationReturnsEmptyOptional() {
        headers.add(KafkaHeader.header(KEY, "{invalid: json}"));
        assertThat(KafkaRecordHelper.getHeaderValue(headers, KEY, Object.class).isEmpty()).isTrue();
    }

    /**
     * Verifies that {@link KafkaRecordHelper#getHeaderValue(List, String, Class)} returns an empty optional if the
     * value of the header is not of the expected type.
     */
    @Test
    public void testThatWrongTypeReturnsEmptyOptional() {
        headers.add(KafkaRecordHelper.createKafkaHeader(KEY, "notANumber"));
        assertThat(KafkaRecordHelper.getHeaderValue(headers, KEY, Integer.class).isEmpty()).isTrue();
    }

    /**
     * Verifies that {@link KafkaRecordHelper#getContentType(List)} returns the content type.
     */
    @Test
    public void testGetContentType() {
        final String contentType = "application/json";
        headers.add(KafkaRecordHelper.createKafkaHeader("content-type", contentType));

        assertThat(KafkaRecordHelper.getContentType(headers)).isEqualTo(Optional.of(contentType));
    }

    /**
     * Verifies that {@link KafkaRecordHelper#getQoS(List)} returns the QoS.
     */
    @Test
    public void testGetQoS() {
        final QoS qos = QoS.AT_MOST_ONCE;
        headers.add(KafkaRecordHelper.createKafkaHeader("qos", qos.ordinal()));

        assertThat(KafkaRecordHelper.getQoS(headers)).isEqualTo(Optional.of(qos));
    }

    /**
     * Verifies that {@link KafkaRecordHelper#isTtlElapsed(List)} returns {@code false} if the TTL is still in the
     * future.
     */
    @Test
    public void testThatTtlIsNotElapsed() {

        headers.add(KafkaRecordHelper.createKafkaHeader("ttl", 5000L));
        headers.add(KafkaRecordHelper.createKafkaHeader("creation-time", Instant.now().toEpochMilli()));

        assertThat(KafkaRecordHelper.isTtlElapsed(headers)).isFalse();
    }

    /**
     * Verifies that {@link KafkaRecordHelper#isTtlElapsed(List)} returns {@code true} if the TTL is in the past.
     */
    @Test
    public void testIsTtlElapsed() {

        headers.add(KafkaRecordHelper.createKafkaHeader("ttl", 5000L));
        headers.add(KafkaRecordHelper.createKafkaHeader("creation-time", Instant.now().minusSeconds(6).toEpochMilli()));

        assertThat(KafkaRecordHelper.isTtlElapsed(headers)).isTrue();
    }


    /**
     * Verifies that <em>getHeaderValue</em> returns first value in case of multiple
     * headers with matching name.
     */
    @Test
    public void testGetHeaderValueWithDuplicateHeaders() {
        final String stringValue1 = "First";
        final String stringValue2 = "Second";
        final String stringValue3 = "Third";
        headers.add(KafkaRecordHelper.createKafkaHeader(KEY, stringValue1));
        headers.add(KafkaRecordHelper.createKafkaHeader(KEY, stringValue2));
        headers.add(KafkaRecordHelper.createKafkaHeader(KEY, stringValue3));

        assertThat(KafkaRecordHelper.getHeaderValue(headers, KEY, String.class))
                .isEqualTo(Optional.of(stringValue1));
    }
}
