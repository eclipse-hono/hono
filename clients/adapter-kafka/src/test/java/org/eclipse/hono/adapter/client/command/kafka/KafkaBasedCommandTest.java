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

package org.eclipse.hono.adapter.client.command.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.hono.kafka.client.HonoTopic;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Verifies behavior of {@link KafkaBasedCommand}.
 *
 */
public class KafkaBasedCommandTest {

    /**
     * Verifies that a command can be created from a valid record.
     */
    @Test
    public void testFromRecordSucceeds() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String correlationId = "the-correlation-id";
        final String deviceId = "4711";
        final String subject = "doThis";

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId,
                getHeaders(deviceId, subject, correlationId));
        final KafkaBasedCommand cmd = KafkaBasedCommand.from(commandRecord, deviceId);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo(subject);
        assertThat(cmd.getDeviceId()).isEqualTo(deviceId);
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertFalse(cmd.isOneWay());
    }

    /**
     * Verifies that a command can be created from a valid record, containing a <em>device_id</em> header
     * with a value differing from the device id given in the KafkaBasedCommand factory method.
     */
    @Test
    public void testFromRecordSucceedsWithDifferingDeviceId() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String correlationId = "the-correlation-id";
        final String gatewayId = "gw-1";
        final String targetDeviceId = "4711";
        final String subject = "doThis";

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, targetDeviceId,
                getHeaders(targetDeviceId, subject, correlationId));
        final KafkaBasedCommand cmd = KafkaBasedCommand.from(commandRecord, gatewayId);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo(subject);
        assertThat(cmd.getDeviceId()).isEqualTo(targetDeviceId);
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertFalse(cmd.isOneWay());
    }

    /**
     * Verifies that a command can be created from a valid record that has no <em>correlation-id</em> header.
     * Verifies that the command reports that it is a one-way command.
     */
    @Test
    public void testFromRecordSucceedsWithoutCorrelationId() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String deviceId = "4711";
        final String subject = "doThis";

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId, getHeaders(deviceId, subject));
        final KafkaBasedCommand cmd = KafkaBasedCommand.from(commandRecord, deviceId);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo("doThis");
        assertThat(cmd.getCorrelationId()).isNull();
        assertTrue(cmd.isOneWay());
    }

    /**
     * Verifies that a command cannot be created from a record that doesn't contain a <em>device_id</em> header.
     */
    @Test
    public void testFromRecordFailsForRecordWithoutDeviceId() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String deviceId = "4711";
        final String subject = "doThis";

        final List<KafkaHeader> headers = List.of(KafkaHeader.header(MessageHelper.SYS_PROPERTY_SUBJECT, subject));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId, headers);
        final KafkaBasedCommand command = KafkaBasedCommand.from(commandRecord, deviceId);
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("device");
    }

    /**
     * Verifies that a command cannot be created from a record that doesn't contain a <em>subject</em> header.
     */
    @Test
    public void testFromRecordFailsForRecordWithoutSubject() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String deviceId = "4711";

        final List<KafkaHeader> headers = List.of(KafkaHeader.header(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId, headers);
        final KafkaBasedCommand command = KafkaBasedCommand.from(commandRecord, deviceId);
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("subject");
    }

    /**
     * Verifies that a command cannot be created from a record that doesn't have the <em>device_id</em> header
     * value as key.
     */
    @Test
    public void testFromRecordFailsForRecordWithWrongKey() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String deviceId = "4711";

        final List<KafkaHeader> headers = List.of(KafkaHeader.header(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, "other_key", headers);
        final KafkaBasedCommand command = KafkaBasedCommand.from(commandRecord, deviceId);
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("key");
    }


    /**
     * Verifies the return value of getInvalidCommandReason().
     */
    @Test
    public void testGetInvalidCommandReason() {
        final String topic = "unsupported_topic";
        final String deviceId = "4711";

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId, Collections.emptyList());
        final KafkaBasedCommand command = KafkaBasedCommand.from(commandRecord, deviceId);
        assertFalse(command.isValid());
        // verify the returned validation error contains all missing/invalid fields
        assertThat(command.getInvalidCommandReason()).contains("device");
        assertThat(command.getInvalidCommandReason()).contains("subject");
        assertThat(command.getInvalidCommandReason()).contains("topic");
    }

    private List<KafkaHeader> getHeaders(final String deviceId, final String subject) {
        return List.of(
                KafkaHeader.header(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId),
                KafkaHeader.header(MessageHelper.SYS_PROPERTY_SUBJECT, subject)
        );
    }

    private List<KafkaHeader> getHeaders(final String deviceId, final String subject, final String correlationId) {
        final List<KafkaHeader> headers = new ArrayList<>(getHeaders(deviceId, subject));
        headers.add(KafkaHeader.header(MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId));
        return headers;
    }

    @SuppressWarnings("unchecked")
    private KafkaConsumerRecord<String, Buffer> getCommandRecord(final String topic, final String key, final List<KafkaHeader> headers) {
        final KafkaConsumerRecord<String, Buffer> consumerRecord = mock(KafkaConsumerRecord.class);
        when(consumerRecord.headers()).thenReturn(headers);
        when(consumerRecord.topic()).thenReturn(topic);
        when(consumerRecord.key()).thenReturn(key);
        return consumerRecord;
    }
}
