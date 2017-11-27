/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.util.UUID;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.message.Message;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;

/**
 * Tests MessageHelper.
 */
public class MessageHelperTest {

    private static final String ULONG_VALUE = "17916881237904312345";
    private static final String MSG_ID_STRING = "message-1234";
    private static final UUID MSG_ID_UUID = UUID.randomUUID();
    private static final UnsignedLong MSG_ID_ULONG = UnsignedLong.valueOf(ULONG_VALUE);
    private static final Binary MSG_ID_BINARY = new Binary(MSG_ID_STRING.getBytes(UTF_8));

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForString() {

        final JsonObject jsonString = MessageHelper.encodeIdToJson(MSG_ID_STRING);
        final Object id = MessageHelper.decodeIdFromJson(jsonString);
        assertThat(id, is(MSG_ID_STRING));
    }

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForUnsignedLong() {

        final JsonObject json  = MessageHelper.encodeIdToJson(MSG_ID_ULONG);
        final Object id = MessageHelper.decodeIdFromJson(json);
        assertThat(id, is(MSG_ID_ULONG));
    }

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForUUID() {

        final JsonObject json   = MessageHelper.encodeIdToJson(MSG_ID_UUID);
        final Object id = MessageHelper.decodeIdFromJson(json);
        assertThat(id, is(MSG_ID_UUID));
    }

    /**
     * Verifies that the helper properly encodes message IDs of supported types.
     */
    @Test
    public void testEncodeIdToJsonForBinary() {

        final JsonObject json = MessageHelper.encodeIdToJson(MSG_ID_BINARY);
        final Object id = MessageHelper.decodeIdFromJson(json);
        assertThat(id, is(MSG_ID_BINARY));
    }

    /**
     * Verifies that the helper adds JMS vendor properties for
     * non-empty content type.
     */
    @Test
    public void testAddJmsVendorPropertiesAddsContentType() {

        Message msg = ProtonHelper.message();
        msg.setContentType("application/json");
        MessageHelper.addJmsVendorProperties(msg);
        assertThat(msg.getApplicationProperties().getValue().get(MessageHelper.JMS_VENDOR_PROPERTY_CONTENT_TYPE), is("application/json"));
    }

    /**
     * Verifies that the helper adds JMS vendor properties for
     * non-empty content encoding.
     */
    @Test
    public void testAddJmsVendorPropertiesAddsContentEncoding() {

        Message msg = ProtonHelper.message();
        msg.setContentEncoding("gzip");
        MessageHelper.addJmsVendorProperties(msg);
        assertThat(msg.getApplicationProperties().getValue().get(MessageHelper.JMS_VENDOR_PROPERTY_CONTENT_ENCODING), is("gzip"));
    }

    /**
     * Verifies that the helper does not add JMS vendor properties for
     * empty content type.
     */
    @Test
    public void testAddJmsVendorPropertiesRejectsEmptyContentType() {

        Message msg = ProtonHelper.message();
        msg.setContentType("");
        MessageHelper.addJmsVendorProperties(msg);
        assertNull(msg.getApplicationProperties());
    }

    /**
     * Verifies that the helper does not add JMS vendor properties for
     * empty content encoding.
     */
    @Test
    public void testAddJmsVendorPropertiesRejectsEmptyContentEncoding() {

        Message msg = ProtonHelper.message();
        msg.setContentEncoding("");
        MessageHelper.addJmsVendorProperties(msg);
        assertNull(msg.getApplicationProperties());
    }
}
