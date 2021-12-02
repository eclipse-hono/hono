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

package org.eclipse.hono.application.client.kafka;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Verifies the behavior of {@link KafkaMessageProperties}.
 */

public class KafkaMessagePropertiesTest {

    private static final String HEADER_KEY = "foo";
    private static final String HEADER_VALUE = "bar";
    private KafkaConsumerRecord<String, Buffer> record;
    private List<KafkaHeader> headers;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        record = mock(KafkaConsumerRecord.class);
        headers = new ArrayList<>();
        when(record.headers()).thenReturn(headers);
    }

    /**
     * Verifies that {@link KafkaMessageProperties#getPropertiesMap()} returns the headers of the record.
     */
    @Test
    public void testThatExpectedValueIsPresent() {
        headers.add(KafkaHeader.header(HEADER_KEY, HEADER_VALUE));

        final Map<String, Object> propertiesMap = new KafkaMessageProperties(record).getPropertiesMap();
        assertThat(propertiesMap).hasSize(1);
        assertThat(propertiesMap.get(HEADER_KEY).toString()).isEqualTo(HEADER_VALUE);

    }

    /**
     * Verifies that the values in the map returned by {@link KafkaMessageProperties#getPropertiesMap()} are of type
     * Buffer.
     */
    @Test
    public void testThatValuesAreOfTypeBuffer() {
        headers.add(KafkaHeader.header(HEADER_KEY, HEADER_VALUE));

        final Object actual = new KafkaMessageProperties(record).getPropertiesMap().get(HEADER_KEY);
        assertThat(actual).isInstanceOf(Buffer.class);
    }

    /**
     * Verifies that the values in the map returned by {@link KafkaMessageProperties#getPropertiesMap()} can be decoded.
     */
    @Test
    public void testThatPropertiesCanBeDecoded() {
        final String intHeaderKey = "int";
        final int intHeaderValue = 1;

        headers.add(KafkaRecordHelper.createKafkaHeader(HEADER_KEY, HEADER_VALUE));
        headers.add(KafkaRecordHelper.createKafkaHeader(intHeaderKey, intHeaderValue));

        final Map<String, Object> propertiesMap = new KafkaMessageProperties(record).getPropertiesMap();

        final String decodedString = KafkaRecordHelper.decode((Buffer) propertiesMap.get(HEADER_KEY), String.class);
        assertThat(decodedString).isEqualTo(HEADER_VALUE);

        final Integer decodedInteger = KafkaRecordHelper.decode((Buffer) propertiesMap.get(intHeaderKey),
                Integer.class);
        assertThat(decodedInteger).isEqualTo(intHeaderValue);
    }

}
