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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.message.Message;
import org.junit.Test;

import io.vertx.core.json.JsonObject;


/**
 * Tests verifying behavior of {@link RequestResponseApiConstants}.
 *
 */
public class RequestResponseApiConstantsTest {

    private static final String ULONG_VALUE = "17916881237904312345";
    private static final String MSG_ID_STRING = "message-1234";
    private static final UUID MSG_ID_UUID = UUID.randomUUID();
    private static final UnsignedLong MSG_ID_ULONG = UnsignedLong.valueOf(ULONG_VALUE);
    private static final Binary MSG_ID_BINARY = new Binary(MSG_ID_STRING.getBytes(StandardCharsets.UTF_8));

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForBinary() {

        final JsonObject json = RequestResponseApiConstants.encodeIdToJson(MSG_ID_BINARY);
        final Object id = RequestResponseApiConstants.decodeIdFromJson(json);
        assertThat(id, is(MSG_ID_BINARY));
    }

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForString() {

        final JsonObject jsonString = RequestResponseApiConstants.encodeIdToJson(MSG_ID_STRING);
        final Object id = RequestResponseApiConstants.decodeIdFromJson(jsonString);
        assertThat(id, is(MSG_ID_STRING));
    }

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForUnsignedLong() {

        final JsonObject json  = RequestResponseApiConstants.encodeIdToJson(MSG_ID_ULONG);
        final Object id = RequestResponseApiConstants.decodeIdFromJson(json);
        assertThat(id, is(MSG_ID_ULONG));
    }

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForUUID() {

        final JsonObject json   = RequestResponseApiConstants.encodeIdToJson(MSG_ID_UUID);
        final Object id = RequestResponseApiConstants.decodeIdFromJson(json);
        assertThat(id, is(MSG_ID_UUID));
    }

    /**
     * Verifies that the AMQP reply created by the helper from a JSON response
     * contains a cache control property.
     */
    @Test
    public void testGetAmqpReplyAddsCacheDirective() {

        // GIVEN a response that is not supposed to be cached by a client
        final CacheDirective directive = CacheDirective.noCacheDirective();
        final String correlationId = "message-id";
        final JsonObject response = RequestResponseApiConstants.getServiceReplyAsJson(
                200, "my-tenant", "my-device", null, directive);
        response.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, RegistrationConstants.encodeIdToJson(correlationId));

        // WHEN creating the AMQP message for the response
        final Message reply = RequestResponseApiConstants.getAmqpReply("endpoint", response);

        // THEN the message contains the corresponding cache control property
        assertThat(MessageHelper.getCacheDirective(reply), is(directive.toString()));
    }
}
