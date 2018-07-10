/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

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
    private static final String REPLY_TO_ID = "the-reply-to-id";

    /**
     * Verifies that a response can be created from a request ID.
     */
    @Test
    public void testFromResponseSucceeds() {
        final CommandResponse resp = CommandResponse.from(
                encode(CORRELATION_ID, REPLY_TO_ID), HttpURLConnection.HTTP_OK);
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

        assertNull(CommandResponse.from(
                "ZZanyString", HttpURLConnection.HTTP_OK));
    }

    /**
     * Verifies that a String that does not contain a hex number as first two chars at the beginning.
     */
    /**
     * Verifies that creating a response from a request ID which contains less characters as indicated
     * by the hex encoded byte at the start position fails.
     */
    @Test
    public void testDecombineIncorrectStringReturnsNullForTooBigNumberAtBeginning() {

        assertNull(CommandResponse.from(
                "FFthisIsLessThan255Characters", HttpURLConnection.HTTP_OK));
    }

    private static String encode(final String correlationId, final String replyToId) {
        return String.format("%02x%s%s", correlationId.length(), correlationId, replyToId);
    }
}
