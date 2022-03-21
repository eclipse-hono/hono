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

package org.eclipse.hono.client.command.kafka;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.util.Constants;
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
        final KafkaBasedCommand cmd = KafkaBasedCommand.from(commandRecord);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo(subject);
        assertThat(cmd.getDeviceId()).isEqualTo(deviceId);
        assertThat(cmd.getGatewayOrDeviceId()).isEqualTo(deviceId);
        assertThat(cmd.getGatewayId()).isNull();
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertTrue(cmd.isOneWay());
    }

    /**
     * Verifies that a command can be created from a valid record that represent a request/response command.
     */
    @Test
    public void testFromRecordSucceedsForRequestResponseCommand() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String correlationId = "the-correlation-id";
        final String deviceId = "4711";
        final String subject = "doThis";

        final List<KafkaHeader> headers = new ArrayList<>(getHeaders(deviceId, subject, correlationId));
        headers.add(KafkaRecordHelper.createResponseRequiredHeader(true));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId, headers);
        final KafkaBasedCommand cmd = KafkaBasedCommand.from(commandRecord);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo(subject);
        assertThat(cmd.getDeviceId()).isEqualTo(deviceId);
        assertThat(cmd.getGatewayOrDeviceId()).isEqualTo(deviceId);
        assertThat(cmd.getGatewayId()).isNull();
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertFalse(cmd.isOneWay());
    }

    /**
     * Verifies that a command can be created from a valid record representing a routed command
     * message with a <em>via</em> header containing a gateway identifier.
     */
    @Test
    public void testFromRoutedCommandRecordSucceeds() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, "myAdapterInstanceId").toString();
        final String correlationId = "the-correlation-id";
        final String gatewayId = "gw-1";
        final String targetDeviceId = "4711";
        final String subject = "doThis";

        final List<KafkaHeader> headers = new ArrayList<>(getHeaders(targetDeviceId, subject, correlationId));
        headers.add(KafkaRecordHelper.createViaHeader(gatewayId));
        headers.add(KafkaRecordHelper.createTenantIdHeader(Constants.DEFAULT_TENANT));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, targetDeviceId, headers);
        final KafkaBasedCommand cmd = KafkaBasedCommand.fromRoutedCommandRecord(commandRecord);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo(subject);
        assertThat(cmd.getDeviceId()).isEqualTo(targetDeviceId);
        assertThat(cmd.getGatewayOrDeviceId()).isEqualTo(gatewayId);
        assertThat(cmd.getGatewayId()).isEqualTo(gatewayId);
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertTrue(cmd.isOneWay());
    }

    /**
     * Verifies that a command cannot be created from a record that doesn't contain a <em>tenant_id</em> header
     * if the record is parsed as a command forwarded by the Command Router.
     */
    @Test
    public void testFromRoutedCommandRecordFailsForMissingTenantId() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, "myAdapterInstanceId").toString();
        final String correlationId = "the-correlation-id";
        final String gatewayId = "gw-1";
        final String targetDeviceId = "4711";
        final String subject = "doThis";

        final List<KafkaHeader> headers = new ArrayList<>(getHeaders(targetDeviceId, subject, correlationId));
        headers.add(KafkaRecordHelper.createViaHeader(gatewayId));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, targetDeviceId, headers);
        assertThrows(IllegalArgumentException.class, () -> {
            KafkaBasedCommand.fromRoutedCommandRecord(commandRecord);
        });
    }

    /**
     * Verifies that a command can be created from a valid record that has no <em>response-required</em>
     * and no <em>correlation-id</em> header.
     * Verifies that the command reports that it is a one-way command.
     */
    @Test
    public void testFromRecordSucceedsWithoutResponseRequiredAndCorrelationId() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String deviceId = "4711";
        final String subject = "doThis";

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId, getHeaders(deviceId, subject));
        final KafkaBasedCommand cmd = KafkaBasedCommand.from(commandRecord);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo("doThis");
        assertThat(cmd.getDeviceId()).isEqualTo(deviceId);
        assertThat(cmd.getGatewayOrDeviceId()).isEqualTo(deviceId);
        assertThat(cmd.getGatewayId()).isNull();
        assertThat(cmd.getCorrelationId()).isNull();
        assertTrue(cmd.isOneWay());
    }

    /**
     * Verifies that a valid command cannot be created from a record that has the <em>response-required</em>
     * header set to "true" but has no <em>correlation-id</em> header.
     */
    @Test
    public void testFromRecordFailsForMissingCorrelationIdWithResponseRequired() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String deviceId = "4711";
        final String subject = "doThis";

        final List<KafkaHeader> headers = new ArrayList<>(getHeaders(deviceId, subject));
        headers.add(KafkaRecordHelper.createResponseRequiredHeader(true));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId, headers);
        final KafkaBasedCommand cmd = KafkaBasedCommand.from(commandRecord);
        assertFalse(cmd.isValid());
        assertThat(cmd.getInvalidCommandReason()).contains("correlation-id");
    }

    /**
     * Verifies that a command cannot be created from a record that doesn't contain a <em>device_id</em> header.
     */
    @Test
    public void testFromRecordFailsForRecordWithoutDeviceId() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String deviceId = "4711";
        final String subject = "doThis";

        final List<KafkaHeader> headers = List.of(KafkaRecordHelper.createSubjectHeader(subject));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId, headers);
        assertThrows(IllegalArgumentException.class, () -> {
            KafkaBasedCommand.from(commandRecord);
        });
    }

    /**
     * Verifies that a command cannot be created from a record that doesn't contain a <em>subject</em> header.
     */
    @Test
    public void testFromRecordFailsForRecordWithoutSubject() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String deviceId = "4711";

        final List<KafkaHeader> headers = List.of(KafkaRecordHelper.createDeviceIdHeader(deviceId));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId, headers);
        final KafkaBasedCommand command = KafkaBasedCommand.from(commandRecord);
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

        final List<KafkaHeader> headers = List.of(KafkaRecordHelper.createDeviceIdHeader(deviceId));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, "other_key", headers);
        assertThrows(IllegalArgumentException.class, () -> {
            KafkaBasedCommand.from(commandRecord);
        });
    }


    /**
     * Verifies the return value of getInvalidCommandReason().
     */
    @Test
    public void testGetInvalidCommandReason() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String deviceId = "4711";

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId,
                List.of(KafkaRecordHelper.createDeviceIdHeader(deviceId)));
        final KafkaBasedCommand command = KafkaBasedCommand.from(commandRecord);
        assertFalse(command.isValid());
        // verify the returned validation error contains all missing/invalid fields
        assertThat(command.getInvalidCommandReason()).contains("subject");
    }

    /**
     * Verifies the return value of getDeliveryFailureNotificationProperties().
     */
    @Test
    public void testGetDeliveryFailureNotificationProperties() {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();
        final String correlationId = "the-correlation-id";
        final String deviceId = "4711";
        final String subject = "doThis";

        final List<KafkaHeader> headers = new ArrayList<>(getHeaders(deviceId, subject, correlationId));
        headers.add(KafkaHeader.header(KafkaRecordHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX, "v1"));
        headers.add(KafkaHeader.header(KafkaRecordHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX, "toBeIgnored"));
        headers.add(KafkaHeader.header(KafkaRecordHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX + "-title", "v2"));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(topic, deviceId,
                headers);
        final KafkaBasedCommand cmd = KafkaBasedCommand.from(commandRecord);
        final Map<String, String> deliveryFailureNotificationProperties = cmd.getDeliveryFailureNotificationProperties();
        assertThat(deliveryFailureNotificationProperties.size()).isEqualTo(2);
        assertThat(deliveryFailureNotificationProperties
                .get(KafkaRecordHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX)).isEqualTo("v1");
        assertThat(deliveryFailureNotificationProperties
                .get(KafkaRecordHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX + "-title")).isEqualTo("v2");
    }

    private List<KafkaHeader> getHeaders(final String deviceId, final String subject) {
        return List.of(
                KafkaRecordHelper.createDeviceIdHeader(deviceId),
                KafkaRecordHelper.createSubjectHeader(subject)
        );
    }

    private List<KafkaHeader> getHeaders(final String deviceId, final String subject, final String correlationId) {
        final List<KafkaHeader> headers = new ArrayList<>(getHeaders(deviceId, subject));
        headers.add(KafkaRecordHelper.createCorrelationIdHeader(correlationId));
        return headers;
    }

    private KafkaConsumerRecord<String, Buffer> getCommandRecord(
            final String topic,
            final String key,
            final List<KafkaHeader> headers) {

        return KafkaClientUnitTestHelper.newMockConsumerRecord(topic, key, headers);
    }
}
