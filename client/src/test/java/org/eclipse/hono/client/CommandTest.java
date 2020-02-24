/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.Test;

/**
 * Verifies behavior of {@link Command}.
 *
 */
public class CommandTest {

    /**
     * Verifies that a command can be created from a valid message.
     * Verifies that the replyToId are build up of all segments behind the tenant.
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
        final boolean replyToContainedDeviceId = true;
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(replyToContainedDeviceId);
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo("doThis");
        assertThat(cmd.getDeviceId()).isEqualTo("4711");
        assertThat(cmd.getOriginalDeviceId()).isEqualTo("4711");
        assertThat(cmd.getReplyToId()).isEqualTo(String.format("4711/%s", replyToId));
        assertThat(cmd.getReplyToEndpoint()).isEqualTo(CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT);
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertFalse(cmd.isOneWay());
        assertThat(cmd.getCommandMessage().getReplyTo()).isEqualTo(String.format("%s/%s/%s/%s%s",
                CommandConstants.COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToOptionsBitFlag, replyToId));
    }

    /**
     * Verifies that a command can be created from a valid message, containing a message address with device id
     * differing from the one given in the command constructor.
     * Verifies that the replyToId are build up of all segments behind the tenant.
     */
    @Test
    public void testFromMessageSucceedsWithDifferingDeviceId() {
        final String gatewayId = "gw-1";
        final String targetDeviceId = "4711";
        final String replyToId = "the-reply-to-id";
        final String correlationId = "the-correlation-id";
        final Message message = ProtonHelper.message("input data");
        message.setAddress(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, targetDeviceId));
        message.setSubject("doThis");
        message.setCorrelationId(correlationId);
        message.setReplyTo(String.format("%s/%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, targetDeviceId, replyToId));
        final boolean replyToContainedDeviceId = true;
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(replyToContainedDeviceId);
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, gatewayId);
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo("doThis");
        assertThat(cmd.getDeviceId()).isEqualTo(gatewayId);
        assertThat(cmd.getOriginalDeviceId()).isEqualTo(targetDeviceId);
        assertThat(cmd.getReplyToId()).isEqualTo(String.format("%s/%s", targetDeviceId, replyToId));
        assertThat(cmd.getReplyToEndpoint()).isEqualTo(CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT);
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertFalse(cmd.isOneWay());
        assertThat(cmd.getCommandMessage().getReplyTo()).isEqualTo(String.format("%s/%s/%s/%s%s",
                CommandConstants.COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToOptionsBitFlag, replyToId));
    }

    /**
     * Verifies that a command can be created from a valid message having no device-id as part of the reply-to address.
     * Verifies that the reply-to address contains the device-id and the reply-id is prefixed with flag 1.
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
        final boolean replyToContainedDeviceId = false;
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(replyToContainedDeviceId);
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getReplyToId()).isEqualTo(replyToId);
        assertThat(cmd.getCommandMessage()).isNotNull();
        assertThat(cmd.getCommandMessage().getReplyTo()).isNotNull();
        assertThat(cmd.getCommandMessage().getReplyTo()).isEqualTo(String.format("%s/%s/%s/%s%s",
                CommandConstants.COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToOptionsBitFlag, replyToId));
    }

    /**
     * Verifies that a command can be created from a valid message having device-id as part of the reply-to address.
     * Verifies that the reply-to address contains the device-id and the reply-id is prefixed with flag 0.
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
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(true);
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getReplyToId()).isEqualTo(String.format("4711/%s", replyToId));
        assertThat(cmd.getCommandMessage()).isNotNull();
        assertThat(cmd.getCommandMessage().getReplyTo()).isNotNull();
        assertThat(cmd.getCommandMessage().getReplyTo()).isEqualTo(String.format("%s/%s/%s/%s%s",
                CommandConstants.COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToOptionsBitFlag, replyToId));
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
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getName()).isEqualTo("doThis");
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertThat(cmd.getReplyToId()).isNull();;
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
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
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
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getCorrelationId()).isEqualTo(messageId);
    }

    /**
     * Verifies that a command can be created from a valid message with application properties.
     * Verifies that the application properties are able to be retrieved from the message.
     */
    @Test
    public void testFromMessageSucceedsWithApplicationProperties() {
        final String replyToId = "the-reply-to-id";
        final String correlationId = "the-correlation-id";
        final Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put("deviceId", "4711");
        applicationProperties.put("tenantId", "DEFAULT_TENANT");
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711"));
        when(message.getApplicationProperties()).thenReturn(new ApplicationProperties(applicationProperties));
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getApplicationProperties()).isNotNull();
        assertThat(cmd.getApplicationProperties()).hasSize(2);
        assertThat(cmd.getApplicationProperties().get("deviceId")).isEqualTo("4711");
        assertThat(cmd.getApplicationProperties().get("tenantId")).isEqualTo("DEFAULT_TENANT");
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
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getApplicationProperties()).isNull();
    }

    /**
     * Verifies that a command cannot be created from a message that neither
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
        final Command command = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("correlation-id");
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
        final Command command = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("address");
    }

    /**
     * Verifies that a command cannot be created from a message that contains
     * a 'to' address with the wrong tenant-id.
     */
    @Test
    public void testFromMessageFailsForAddressWithWrongTenant() {
        final Message message = mock(Message.class);
        when(message.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, "other_tenant", "4711"));
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn("the-correlation-id");
        final Command command = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("address");
    }

    /**
     * Verifies that a command cannot be created from a message that contains
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
        final Command command = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("reply-to");
    }

    /**
     * Verifies that a command cannot be created from a message that contains
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
        final Command command = Command.from(message, Constants.DEFAULT_TENANT, "4712");
        assertFalse(command.isValid());
        assertThat(command.getInvalidCommandReason()).contains("reply-to");
    }

    /**
     * Verifies the return value of getInvalidCommandReason().
     */
    @Test
    public void testGetInvalidCommandReason() {
        final Message message = mock(Message.class);
        when(message.getReplyTo()).thenReturn("invalid-reply-to");
        final Command command = Command.from(message, Constants.DEFAULT_TENANT, "4712");
        assertFalse(command.isValid());
        // verify the returned validation error contains all missing fields
        assertThat(command.getInvalidCommandReason()).contains("address");
        assertThat(command.getInvalidCommandReason()).contains("subject");
        assertThat(command.getInvalidCommandReason()).contains("correlation-id");
        assertThat(command.getInvalidCommandReason()).contains("reply-to");
    }

    /**
     * Verifies the correct encoding and decoding of the bit flag with options relating to the reply-to address.
     */
    @Test
    public void testEncodeDecodeReplyToOptions() {
        final boolean replyToContainedDeviceId = false;
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(replyToContainedDeviceId);
        assertThat(Command.isReplyToContainedDeviceIdOptionSet(replyToOptionsBitFlag)).isEqualTo(replyToContainedDeviceId);

        final boolean replyToContainedDeviceId2 = true;
        final String replyToOptions2 = Command.encodeReplyToOptions(replyToContainedDeviceId2);
        assertThat(Command.isReplyToContainedDeviceIdOptionSet(replyToOptions2)).isEqualTo(replyToContainedDeviceId2);
    }
}
