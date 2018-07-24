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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.apache.qpid.proton.message.Message;
import org.junit.Test;

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

}
