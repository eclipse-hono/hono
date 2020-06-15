/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.example.protocolgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link RequestId}.
 */
public class RequestIdTest {

    private static final String REPLY_ID = "999";
    private static final String CORRELATION_ID = "888";
    private static final String REQUEST_ID = "03888999";
    private static final int MAX_LENGTH = 255;

    /**
     * Verifies that encoding a correlation id and reply id (from a reply-to address) results in the expected request
     * id.
     */
    @Test
    public void testEncode() {
        final String replyTo = "command_response/test-tenant/test-device/" + REPLY_ID;

        assertThat(RequestId.encode(replyTo, CORRELATION_ID)).isEqualTo("03888999");

    }

    /**
     * Verifies that decoding the request id results in the expected reply id and correlation id.
     */
    @Test
    public void testDecode() {

        final RequestId requestId = RequestId.decode(REQUEST_ID);
        assertThat(requestId.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(requestId.getReplyId()).isEqualTo(REPLY_ID);
    }

    /**
     * Verifies that trying to encode too long correlation ids result in the expected exception.
     */
    @Test
    public void testMaxCorrelationIdLength() {

        // GIVEN a correlation id that is longer than the max length allowed ...
        final char[] tooManyChars = new char[MAX_LENGTH + 1];
        Arrays.fill(tooManyChars, '8');
        final String tooLongCorrelationId = new String(tooManyChars);

        // ... AND a correlation id that is 1 character shorter
        final char[] maxChars = Arrays.copyOf(tooManyChars, MAX_LENGTH);
        final String maxCorrelationId = new String(maxChars);

        // WHEN encoding the short correlation id THEN no exception is thrown ...
        RequestId.encode("command_response/test-tenant/test-device/" + REPLY_ID, maxCorrelationId);

        // ... WHEN encoding longer correlation id THEN the expected exception is thrown
        assertThrows(IllegalArgumentException.class,
                () -> RequestId.encode("command_response/test-tenant/test-device/" + REPLY_ID, tooLongCorrelationId));
    }
}
