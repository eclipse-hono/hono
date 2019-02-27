/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.junit.Test;

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
        final Message message = mock(Message.class);
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getName(), is("doThis"));
        assertThat(cmd.getReplyToId(), is(String.format("4711/%s", replyToId)));
        assertThat(cmd.getCorrelationId(), is(correlationId));
        assertFalse(cmd.isOneWay());
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
        message.setReplyTo(replyToId);
        message.setSubject("doThis");
        message.setCorrelationId(correlationId);
        message.setReplyTo(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, replyToId));
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getReplyToId(), is(replyToId));
        assertNotNull(cmd.getCommandMessage());
        assertNotNull(cmd.getCommandMessage().getReplyTo());
        assertThat(cmd.getCommandMessage().getReplyTo(), is(String.format("%s/%s/%s/1%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId)));
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
        message.setReplyTo(replyToId);
        message.setSubject("doThis");
        message.setCorrelationId(correlationId);
        message.setReplyTo(String.format("%s/%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getReplyToId(), is(String.format("4711/%s", replyToId)));
        assertNotNull(cmd.getCommandMessage());
        assertNotNull(cmd.getCommandMessage().getReplyTo());
        assertThat(cmd.getCommandMessage().getReplyTo(), is(String.format("%s/%s/%s/0%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId)));
    }

    /**
     * Verifies that a command can be created from a valid message that has an empty reply-to property.
     * Verifies that the replyToId is {@code null} and the command reports that it is a one-way command.
     */
    @Test
    public void testFromMessageSucceedsWithoutReplyTo() {
        final String correlationId = "the-correlation-id";
        final Message message = mock(Message.class);
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getName(), is("doThis"));
        assertThat(cmd.getCorrelationId(), is(correlationId));
        assertNull(cmd.getReplyToId());
        assertTrue(cmd.isOneWay());
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
        when(message.getApplicationProperties()).thenReturn(new ApplicationProperties(applicationProperties));
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getApplicationProperties(), is(notNullValue()));
        assertThat(cmd.getApplicationProperties().size(), is(2));
        assertThat(cmd.getApplicationProperties().get("deviceId"), is("4711"));
        assertThat(cmd.getApplicationProperties().get("tenantId"), is("DEFAULT_TENANT"));
    }

    /**
     * Verifies that a command can be created from a valid message with no application properties is valid.
     */
    @Test
    public void testFromMessageSucceedsWithNoApplicationProperties() {
        final String replyToId = "the-reply-to-id";
        final String correlationId = "the-correlation-id";
        final Message message = mock(Message.class);
        when(message.getApplicationProperties()).thenReturn(null);
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        final Command cmd = Command.from(message, Constants.DEFAULT_TENANT, "4711");
        assertTrue(cmd.isValid());
        assertThat(cmd.getApplicationProperties(), is(nullValue()));
    }

    /**
     * Verifies that a command cannot be created from a message that neither
     * contains a message nor correlation ID.
     */
    @Test
    public void testFromMessageFailsForMissingCorrelationId() {
        final String replyToId = "the-reply-to-id";
        final Message message = mock(Message.class);
        when(message.getSubject()).thenReturn("doThis");
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, "4711", replyToId));
        assertFalse(Command.from(message, Constants.DEFAULT_TENANT, "4711").isValid());
    }

    /**
     * Verifies that a command cannot be created from a message that contains
     * a malformed reply-to address.
     */
    @Test
    public void testFromMessageFailsForMalformedReplyToAddress() {
        final String correlationId = "the-correlation-id";
        final Message message = mock(Message.class);
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, "4711", Constants.DEFAULT_TENANT));
        assertFalse(Command.from(message, Constants.DEFAULT_TENANT, "4711").isValid());
    }

    /**
     * Verifies that a command cannot be created from a message that contains
     * a reply-to address that does not match the target device.
     */
    @Test
    public void testFromMessageFailsForNonMatchingReplyToAddress() {
        final String replyToId = "the-reply-to-id";
        final String correlationId = "the-correlation-id";
        final Message message = mock(Message.class);
        when(message.getSubject()).thenReturn("doThis");
        when(message.getCorrelationId()).thenReturn(correlationId);
        when(message.getReplyTo()).thenReturn(String.format("%s/%s",
                CommandConstants.COMMAND_ENDPOINT, replyToId));
        assertFalse(Command.from(message, Constants.DEFAULT_TENANT, "4712").isValid());
    }
}
