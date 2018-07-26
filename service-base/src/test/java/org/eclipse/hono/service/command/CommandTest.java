/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.command;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.CoreMatchers.*;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.junit.Test;

import io.vertx.proton.ProtonDelivery;


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
        final Command cmd = Command.from(mock(ProtonDelivery.class), message, Constants.DEFAULT_TENANT, "4711");
        assertNotNull(cmd);
        assertThat(cmd.getName(), is("doThis"));
        assertThat(cmd.getReplyToId(), is(String.format("4711/%s", replyToId)));
        assertThat(cmd.getCorrelationId(), is(correlationId));
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
        assertNull(Command.from(mock(ProtonDelivery.class), message, Constants.DEFAULT_TENANT, "4711"));
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
        assertNull(Command.from(mock(ProtonDelivery.class), message, Constants.DEFAULT_TENANT, "4711"));
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
        assertNull(Command.from(mock(ProtonDelivery.class), message, Constants.DEFAULT_TENANT, "4712"));
    }
}
