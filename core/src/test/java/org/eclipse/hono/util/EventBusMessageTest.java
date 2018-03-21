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

package org.eclipse.hono.util;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.junit.Before;
import org.junit.Test;


/**
 * Tests verifying behavior of {@link EventBusMessage}.
 *
 */
public class EventBusMessageTest {

    private static final String ULONG_VALUE = "17916881237904312345";
    private static final String MSG_ID_STRING = "message-1234";
    private static final UUID MSG_ID_UUID = UUID.randomUUID();
    private static final UnsignedLong MSG_ID_ULONG = UnsignedLong.valueOf(ULONG_VALUE);
    private static final Binary MSG_ID_BINARY = new Binary(MSG_ID_STRING.getBytes(StandardCharsets.UTF_8));

    private EventBusMessage message;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setup() {
        message = EventBusMessage.forStatusCode(200);
    }

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForBinary() {

        message.setCorrelationId(MSG_ID_BINARY);
        final Object id = message.getCorrelationId();
        assertThat(id, is(MSG_ID_BINARY));
    }

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForString() {

        message.setCorrelationId(MSG_ID_STRING);
        final Object id = message.getCorrelationId();
        assertThat(id, is(MSG_ID_STRING));
    }

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForUnsignedLong() {

        message.setCorrelationId(MSG_ID_ULONG);
        final Object id = message.getCorrelationId();
        assertThat(id, is(MSG_ID_ULONG));
    }

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForUUID() {

        message.setCorrelationId(MSG_ID_UUID);
        final Object id = message.getCorrelationId();
        assertThat(id, is(MSG_ID_UUID));
    }

    /**
     * Verifies that all required properties are copied to a response
     * created for a request.
     */
    @Test
    public void testReplyToCopiesRequiredProperties() {

        message = EventBusMessage.forOperation("get").setCorrelationId("4711").setReplyToAddress("reply");
        final EventBusMessage response = message.getResponse(200);
        assertTrue(response.hasResponseProperties());
        assertThat(response.getOperation(), is("get"));
        assertThat(response.getCorrelationId(), is("4711"));
        assertThat(response.getReplyToAddress(), is("reply"));
    }
}
