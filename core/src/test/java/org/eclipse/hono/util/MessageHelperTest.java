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

package org.eclipse.hono.util;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.junit.Test;

import io.vertx.core.json.DecodeException;
import io.vertx.proton.ProtonHelper;

/**
 * Tests MessageHelper.
 */
public class MessageHelperTest {

    /**
     * Verifies that the helper adds JMS vendor properties for
     * non-empty content type.
     */
    @Test
    public void testAddJmsVendorPropertiesAddsContentType() {

        final Message msg = ProtonHelper.message();
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

        final Message msg = ProtonHelper.message();
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

        final Message msg = ProtonHelper.message();
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

        final Message msg = ProtonHelper.message();
        msg.setContentEncoding("");
        MessageHelper.addJmsVendorProperties(msg);
        assertNull(msg.getApplicationProperties());
    }

    /**
     * Verifies that the helper properly handles malformed JSON payload.
     */
    @Test(expected = DecodeException.class)
    public void testGetJsonPayloadHandlesMalformedJson() {

        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(new byte[] { 0x01, 0x02, 0x03, 0x04 }))); // not JSON
        MessageHelper.getJsonPayload(msg);
    }

    /**
     * Verifies that the helper does not throw an exception when reading
     * invalid UTF-8 from a message's payload.
     */
    @Test
    public void testGetPayloadAsStringHandlesNonCharacterPayload() {

        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(new byte[] { (byte) 0xc3, (byte) 0x28 })));
        assertNotNull(MessageHelper.getPayloadAsString(msg));

        msg.setBody(new Data(new Binary(new byte[] { (byte) 0xf0, (byte) 0x28, (byte) 0x8c, (byte) 0xbc })));
        assertNotNull(MessageHelper.getPayloadAsString(msg));
    }

    /**
     * Verifies that the helper does not throw an exception when trying to
     * read payload as JSON from an empty Data section.
     */
    @Test
    public void testGetJsonPayloadHandlesEmptyDataSection() {

        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(new byte[0])));
        assertNull(MessageHelper.getJsonPayload(msg));
    }
}
