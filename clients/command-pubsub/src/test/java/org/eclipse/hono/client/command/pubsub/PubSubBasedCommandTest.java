/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.command.pubsub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.Test;

import com.google.pubsub.v1.PubsubMessage;

/**
 * Verifies behavior of {@link PubSubBasedCommand}.
 *
 */
public class PubSubBasedCommandTest {

    private final String correlationId = "my-correlation-id";
    private final String deviceId = "test-device";
    private final String tenantId = "test-tenant";
    private final String subject = "test-subject";

    /**
     * Verifies that a command can be created from a valid PubsubMessage.
     */
    @Test
    public void testFromMessageSucceeds() {
        final PubsubMessage message = getPubSubMessage(tenantId, deviceId, subject, null, null, null);

        final PubSubBasedCommand command = PubSubBasedCommand.from(message, tenantId);
        assertTrue(command.isValid());
        assertThat(command.getName()).isEqualTo(subject);
        assertThat(command.getDeviceId()).isEqualTo(deviceId);
        assertThat(command.getGatewayOrDeviceId()).isEqualTo(deviceId);
        assertThat(command.getGatewayId()).isNull();
        assertThat(command.getCorrelationId()).isNull();
        assertTrue(command.isOneWay());
    }

    /**
     * Verifies that a command can be created from a valid PubsubMessage that represent a request/response command.
     */
    @Test
    public void testFromMessageSucceedsForRequestResponseCommand() {
        final String responseRequired = "true";
        final PubsubMessage message = getPubSubMessage(tenantId, deviceId, subject, correlationId, responseRequired,
                null);

        final PubSubBasedCommand command = PubSubBasedCommand.from(message, tenantId);
        assertTrue(command.isValid());
        assertThat(command.getName()).isEqualTo(subject);
        assertThat(command.getDeviceId()).isEqualTo(deviceId);
        assertThat(command.getGatewayOrDeviceId()).isEqualTo(deviceId);
        assertThat(command.getGatewayId()).isNull();
        assertThat(command.getCorrelationId()).isEqualTo(correlationId);
        assertFalse(command.isOneWay());
    }

    /**
     * Verifies that a command can be created from a valid PubsubMessage representing a routed command message with a
     * via attribute containing the gateway identifier.
     */
    @Test
    public void testFromRoutedMessageSucceeds() {
        final String gatewayId = "test-gateway";
        final PubsubMessage message = getPubSubMessage(tenantId, deviceId, subject, correlationId, null,
                gatewayId);

        final PubSubBasedCommand command = PubSubBasedCommand.fromRoutedCommandMessage(message);
        assertTrue(command.isValid());
        assertThat(command.getName()).isEqualTo(subject);
        assertThat(command.getDeviceId()).isEqualTo(deviceId);
        assertThat(command.getGatewayOrDeviceId()).isEqualTo(gatewayId);
        assertThat(command.getGatewayId()).isEqualTo(gatewayId);
        assertThat(command.getCorrelationId()).isEqualTo(correlationId);
        assertTrue(command.isOneWay());
    }

    /**
     * Verifies that a command cannot be created from a PubsubMessage that contains an empty attributes map.
     */
    @Test
    public void testFromRoutedMessageFailsForEmptyAttributes() {
        final PubsubMessage message = getPubSubMessage(null, null, null, null, null,
                null);

        assertThrows(IllegalArgumentException.class, () -> {
            PubSubBasedCommand.fromRoutedCommandMessage(message);
        });
    }

    /**
     * Verifies that a command cannot be created from a PubsubMessage that doesn't contain a tenant_id attribute.
     */
    @Test
    public void testFromRoutedMessageFailsForMissingTenantId() {
        final PubsubMessage message = getPubSubMessage(null, deviceId, subject, null, null,
                null);

        assertThrows(IllegalArgumentException.class, () -> {
            PubSubBasedCommand.fromRoutedCommandMessage(message);
        });
    }

    /**
     * Verifies that a command cannot be created from a PubsubMessage that doesn't contain a device_id attribute.
     */
    @Test
    public void testFromRoutedMessageFailsForMissingDeviceId() {
        final PubsubMessage message = getPubSubMessage(tenantId, null, subject, null, null,
                null);

        assertThrows(IllegalArgumentException.class, () -> {
            PubSubBasedCommand.fromRoutedCommandMessage(message);
        });
    }

    /**
     * Verifies that an invalid command is created from a PubsubMessage that does not contain a correlation-id attribute
     * but has response-required set to true.
     */
    @Test
    public void testFromMessageFailsForMissingCorrelationIdWithResponseRequired() {
        final String responseRequired = "true";
        final PubsubMessage message = getPubSubMessage(tenantId, deviceId, subject, null, responseRequired,
                null);

        final PubSubBasedCommand command = PubSubBasedCommand.from(message, tenantId);

        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("correlation-id is not set");
    }

    /**
     * Verifies that an invalid command is created from a PubsubMessage that doesn't contain a subject attribute.
     */
    @Test
    public void testFromMessageFailsForMessageWithoutSubject() {
        final PubsubMessage message = getPubSubMessage(tenantId, deviceId, null, null, null,
                null);

        final PubSubBasedCommand command = PubSubBasedCommand.from(message, tenantId);

        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("subject not set");
    }

    private PubsubMessage getPubSubMessage(final String tenantId, final String deviceId, final String subject,
            final String correlationId, final String responseRequired, final String via) {
        final Map<String, String> attributes = new HashMap<>();
        Optional.ofNullable(deviceId)
                .ifPresent(ok -> attributes.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId));
        Optional.ofNullable(tenantId)
                .ifPresent(ok -> attributes.put( MessageHelper.APP_PROPERTY_TENANT_ID, tenantId));
        Optional.ofNullable(subject)
                .ifPresent(ok -> attributes.put(MessageHelper.SYS_PROPERTY_SUBJECT, subject));
        Optional.ofNullable(responseRequired)
                .ifPresent(ok -> attributes.put(PubSubMessageHelper.PUBSUB_PROPERTY_RESPONSE_REQUIRED, responseRequired));
        Optional.ofNullable(correlationId)
                .ifPresent(ok -> attributes.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId));
        Optional.ofNullable(via)
                .ifPresent(ok -> attributes.put(MessageHelper.APP_PROPERTY_CMD_VIA, via));

        return PubsubMessage.newBuilder().putAllAttributes(attributes).build();
    }
}
