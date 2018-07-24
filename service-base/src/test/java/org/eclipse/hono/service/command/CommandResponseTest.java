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
import static org.hamcrest.CoreMatchers.*;

import java.net.HttpURLConnection;

import org.junit.Test;


/**
 * Verifies behavior of {@link CommandResponse}.
 *
 */
public class CommandResponseTest {

    private static final String CORRELATION_ID = "the-correlation-id";
    private static final String DEVICE_ID = "4711";
    private static final String REPLY_TO_ID = "the-reply-to-id";
    private static final String REPLY_TO_ID_WITH_DEVICE = DEVICE_ID + "/" + REPLY_TO_ID;

    /**
     * Verifies that a response can be created from a request ID.
     */
    @Test
    public void testFromResponseSucceeds() {

        final CommandResponse resp = CommandResponse.from(
                Command.getRequestId(CORRELATION_ID, REPLY_TO_ID, DEVICE_ID),
                DEVICE_ID,
                HttpURLConnection.HTTP_OK);
        assertNotNull(resp);
        assertThat(resp.getCorrelationId(), is(CORRELATION_ID));
        assertThat(resp.getReplyToId(), is(REPLY_TO_ID));
    }

    /**
     * Verifies that creating a response from a request ID which does not contain a hex encoded byte
     * at the start position fails.
     */
    @Test
    public void testFromFailsForMalformedRequestId() {

        // make sure we succeed with a valid length string
        final CommandResponse resp = CommandResponse.from(
                "003anyString", DEVICE_ID, HttpURLConnection.HTTP_OK);
        assertThat(resp.getCorrelationId(), is("any"));
        assertThat(resp.getReplyToId(), is("String"));

        assertNull(CommandResponse.from("0ZZanyString", DEVICE_ID, HttpURLConnection.HTTP_OK));
    }

    /**
     * Verifies that creating a response for an invalid status code fails.
     */
    @Test
    public void testFromFailsForInvalidStatusCode() {

        // make sure we succeed with a valid status code
        final CommandResponse resp = CommandResponse.from(
                "103oneTwo", DEVICE_ID, 200);
        assertThat(resp.getCorrelationId(), is("one"));
        assertThat(resp.getReplyToId(), is(DEVICE_ID + "/Two"));

        assertNull(CommandResponse.from(
                "103oneTwo", DEVICE_ID, 100));
        assertNull(CommandResponse.from(
                "103oneTwo", DEVICE_ID, 310));
        assertNull(CommandResponse.from(
                "103oneTwo", DEVICE_ID, 600));
        assertNull(CommandResponse.from(
                "103oneTwo", DEVICE_ID, null));
    }

    /**
     * Verifies that creating a response from a request ID which contains less characters as indicated
     * by the hex encoded byte at the start position fails.
     */
    @Test
    public void testFailsForIncorrectCorrelationIdLength() {

        final String id = "thisIsLessThan255Characters";
        // make sure we succeed with valid length
        final CommandResponse resp = CommandResponse.from(
                String.format("0%02x%s", 4, id), DEVICE_ID, 200);
        assertThat(resp.getCorrelationId(), is("this"));
        assertThat(resp.getReplyToId(), is("IsLessThan255Characters"));

        assertNull(CommandResponse.from(
                "1FFthisIsLessThan255Characters",
                DEVICE_ID,
                HttpURLConnection.HTTP_OK));
    }

    /**
     * Verifies that device-id is rendered into reply-to-id when the request-id
     * starts with '1'.
     */
    @Test
    public void testDeviceInReply() {
        final CommandResponse resp = CommandResponse.from(
                Command.getRequestId(CORRELATION_ID, REPLY_TO_ID_WITH_DEVICE, DEVICE_ID),
                DEVICE_ID,
                HttpURLConnection.HTTP_OK);
        assertThat(resp.getReplyToId(), is(REPLY_TO_ID_WITH_DEVICE));
    }

    /**
     * Verifies that device-id is NOT rendered into reply-to-id when the request-id
     * starts with a '0'.
     */
    @Test
    public void testDeviceNotInReply() {
        final CommandResponse resp = CommandResponse.from(
                Command.getRequestId(CORRELATION_ID, REPLY_TO_ID, DEVICE_ID),
                DEVICE_ID,
                HttpURLConnection.HTTP_OK);
        assertThat(resp.getReplyToId(), is(REPLY_TO_ID));
    }
}
