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

package org.eclipse.hono.client.command.amqp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.Test;

import io.vertx.proton.ProtonHelper;

/**
 * Tests verifying behavior of {@link ProtonBasedCommand}.
 *
 */
public class ProtonBasedCommandTest {

    /**
     * Verifies that a command can be created from a valid message.
     */
    @Test
    public void testFromMessageSucceeds() {
        final String replyToId = "the-reply-to-id";
        final String correlationId = "the-correlation-id";
        final Message message = ProtonHelper.message("input data");
        message.setAddress(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        message.setSubject("doThis");
        message.setCorrelationId(correlationId);
        message.setReplyTo(String.format("%s/%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        final ProtonBasedCommand cmd = ProtonBasedCommand.from(message);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo("doThis");
        assertThat(cmd.getTenant()).isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(cmd.getGatewayOrDeviceId()).isEqualTo("4711");
        assertThat(cmd.getDeviceId()).isEqualTo("4711");
        assertThat(cmd.getReplyToId()).isEqualTo(String.format("4711/%s", replyToId));
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertFalse(cmd.isOneWay());
    }

    /**
     * Verifies that a command can be created from a valid message representing a routed command
     * message with a <em>via</em> property containing a gateway identifier.
     */
    @Test
    public void testFromRoutedCommandMessageSucceeds() {
        final String gatewayId = "gw-1";
        final String targetDeviceId = "4711";
        final String replyToId = "the-reply-to-id";
        final String correlationId = "the-correlation-id";
        final Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put("via", gatewayId);
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        when(message.getApplicationProperties()).thenReturn(new ApplicationProperties(applicationProperties));
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        final ProtonBasedCommand cmd = ProtonBasedCommand.fromRoutedCommandMessage(message);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo("doThis");
        assertThat(cmd.getTenant()).isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(cmd.getGatewayOrDeviceId()).isEqualTo(gatewayId);
        assertThat(cmd.getGatewayId()).isEqualTo(gatewayId);
        assertThat(cmd.getDeviceId()).isEqualTo(targetDeviceId);
        assertThat(cmd.getReplyToId()).isEqualTo(String.format("%s/%s", targetDeviceId, replyToId));
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertFalse(cmd.isOneWay());
    }

    /**
     * Verifies that a command can be created from a valid message having no device-id as part of the reply-to address.
     */
    @Test
    public void testForReplyToWithoutDeviceId() {
        final String replyToId = "the-reply-to-id";
        final String correlationId = "the-correlation-id";
        final Message message = ProtonHelper.message("input data");
        message.setAddress(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        message.setSubject("doThis");
        message.setCorrelationId(correlationId);
        message.setReplyTo(String.format("%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, replyToId));
        final ProtonBasedCommand cmd = ProtonBasedCommand.from(message);
        assertTrue(cmd.isValid());
        assertThat(cmd.getReplyToId()).isEqualTo(replyToId);
    }

    /**
     * Verifies that a command can be created from a valid message having device-id as part of the reply-to address.
     */
    @Test
    public void testForReplyToWithDeviceId() {
        final String replyToId = "the-reply-to-id";
        final String correlationId = "the-correlation-id";
        final Message message = ProtonHelper.message("input data");
        message.setAddress(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        message.setSubject("doThis");
        message.setCorrelationId(correlationId);
        message.setReplyTo(String.format("%s/%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        final ProtonBasedCommand cmd = ProtonBasedCommand.from(message);
        assertTrue(cmd.isValid());
        assertThat(cmd.getReplyToId()).isEqualTo(String.format("4711/%s", replyToId));
    }

    /**
     * Verifies that a command can be created from a valid message that has an empty reply-to property.
     * Verifies that the replyToId is {@code null} and the command reports that it is a one-way command.
     */
    @Test
    public void testFromMessageSucceedsWithoutReplyTo() {
        final String correlationId = "the-correlation-id";
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        final ProtonBasedCommand cmd = ProtonBasedCommand.from(message);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo("doThis");
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertThat(cmd.getReplyToId()).isNull();
        assertTrue(cmd.isOneWay());
    }

    /**
     * Verifies that a command can be created from a valid message that has an empty reply-to property
     * and no message-id and correlation-id properties.
     * Verifies that the replyToId is {@code null} and the command reports that it is a one-way command.
     */
    @Test
    public void testFromMessageSucceedsWithoutReplyToAndCorrelationId() {
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        when(message.getSubject()).thenReturn("doThis");
        final ProtonBasedCommand cmd = ProtonBasedCommand.from(message);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo("doThis");
        assertThat(cmd.getCorrelationId()).isNull();
        assertThat(cmd.getReplyToId()).isNull();
        assertTrue(cmd.isOneWay());
    }

    /**
     * Verifies that a command can be created from a message with message id but no correlation id.
     */
    @Test
    public void testFromMessageSucceedsWithMessageIdButNoCorrelationId() {
        final String replyToId = "the-reply-to-id";
        final String messageId = "the-message-id";
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        when(message.getApplicationProperties()).thenReturn(null);
        when(message.getSubject()).thenReturn("doThis");
        when(message.getMessageId()).thenReturn(messageId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        final ProtonBasedCommand cmd = ProtonBasedCommand.from(message);
        assertTrue(cmd.isValid());
        assertThat(cmd.getCorrelationId()).isEqualTo(messageId);
    }

    /**
     * Verifies that a command can be created from a valid message with no application properties is valid.
     */
    @Test
    public void testFromMessageSucceedsWithNoApplicationProperties() {
        final String replyToId = "the-reply-to-id";
        final String correlationId = "the-correlation-id";
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        when(message.getApplicationProperties()).thenReturn(null);
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        final ProtonBasedCommand cmd = ProtonBasedCommand.from(message);
        assertTrue(cmd.isValid());
    }

    /**
     * Verifies that a valid command cannot be created from a message that has an unsupported body section.
     */
    @Test
    public void testFromMessageFailsForUnsupportedMessageBody() {
        final String replyToId = "the-reply-to-id";
        final String correlationId = "the-correlation-id";
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        when(message.getApplicationProperties()).thenReturn(null);
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));

        Section body = new AmqpValue(5L);
        when(message.getBody()).thenReturn(body);
        ProtonBasedCommand command = ProtonBasedCommand.from(message);
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("body");
        // assert tenant/device information can be retrieved nonetheless
        assertThat(command.getTenant()).isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(command.getGatewayOrDeviceId()).isEqualTo("4711");
        assertThat(command.getDeviceId()).isEqualTo("4711");


        body = new AmqpSequence(Collections.singletonList("test"));
        when(message.getBody()).thenReturn(body);
        command = ProtonBasedCommand.from(message);
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("body");
        // assert tenant/device information can be retrieved nonetheless
        assertThat(command.getTenant()).isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(command.getGatewayOrDeviceId()).isEqualTo("4711");
        assertThat(command.getDeviceId()).isEqualTo("4711");
    }

    /**
     * Verifies that a valid command cannot be created from a message that neither
     * contains a message nor correlation ID.
     */
    @Test
    public void testFromMessageFailsForMissingCorrelationOrMessageId() {
        final String replyToId = "the-reply-to-id";
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        when(message.getSubject()).thenReturn("doThis");
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        final ProtonBasedCommand command = ProtonBasedCommand.from(message);
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("correlation-id");
        // assert tenant/device information can be retrieved nonetheless
        assertThat(command.getTenant()).isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(command.getGatewayOrDeviceId()).isEqualTo("4711");
        assertThat(command.getDeviceId()).isEqualTo("4711");
    }

    /**
     * Verifies that a command cannot be created from a message that contains
     * a 'to' address without the device-id part.
     */
    @Test
    public void testFromMessageFailsForMissingDeviceIdInAddress() {

        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT));
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn("the-correlation-id");
        assertThrows(IllegalArgumentException.class, () -> {
            ProtonBasedCommand.from(message);
        });
    }

    /**
     * Verifies that a valid command cannot be created from a message that contains
     * a reply-to address with the wrong tenant.
     */
    @Test
    public void testFromMessageFailsForReplyToWithWrongTenant() {
        final String correlationId = "the-correlation-id";
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, "wrong_tenant", "4711"));
        final ProtonBasedCommand command = ProtonBasedCommand.from(message);
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("reply-to");
        // assert tenant/device information can be retrieved nonetheless
        assertThat(command.getTenant()).isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(command.getGatewayOrDeviceId()).isEqualTo("4711");
        assertThat(command.getDeviceId()).isEqualTo("4711");
    }

    /**
     * Verifies that a valid command cannot be created from a message that contains
     * a reply-to address without a reply id.
     */
    @Test
    public void testFromMessageFailsForReplyToWithoutReplyId() {
        final String correlationId = "the-correlation-id";
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT));
        final ProtonBasedCommand command = ProtonBasedCommand.from(message);
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("reply-to");
        // assert tenant/device information can be retrieved nonetheless
        assertThat(command.getTenant()).isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(command.getGatewayOrDeviceId()).isEqualTo("4711");
        assertThat(command.getDeviceId()).isEqualTo("4711");
    }

    /**
     * Verifies the return value of getInvalidCommandReason().
     */
    @Test
    public void testGetInvalidCommandReason() {
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        when(message.getReplyTo()).thenReturn("invalid-reply-to");
        final ProtonBasedCommand command = ProtonBasedCommand.from(message);
        assertFalse(command.isValid());
        // verify the returned validation error contains all missing fields
        assertThat(command.getInvalidCommandReason()).contains("subject");
        assertThat(command.getInvalidCommandReason()).contains("correlation-id");
        assertThat(command.getInvalidCommandReason()).contains("reply-to");
    }
}
