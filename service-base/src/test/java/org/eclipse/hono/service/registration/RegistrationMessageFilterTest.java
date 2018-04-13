/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.registration;

import static org.eclipse.hono.util.RegistrationConstants.ACTION_GET;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Test;

import io.vertx.proton.ProtonHelper;

/**
 * Test verifying that the filter complies with the registration message format specification.
 */
public class RegistrationMessageFilterTest {

    private static final String MY_TENANT = "myTenant";
    private static final String MY_DEVICE = "myDevice";

    /**
     * Verifies that a request that contains another device ID than the link it has been
     * sent on does not pass the filter.
     */
    @Test
    public void testVerifyDetectsDeviceIdMismatch() {
        // GIVEN a registration message with device id not matching the link target
        final Message msg = givenAMessageHavingProperties(MY_DEVICE + "_1", ACTION_GET);

        // WHEN receiving the message via a link
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT, MY_DEVICE);

        // THEN message validation fails
        assertFalse(RegistrationMessageFilter.verify(linkTarget, msg));
    }

    /**
     * Verifies that a request that does not contain a device ID
     * does not pass the filter.
     */
    @Test
    public void testVerifyDetectsMissingDeviceId() {
        // GIVEN a registration message lacking the device id
        final Message msg = givenAMessageHavingProperties(null, ACTION_GET);

        // WHEN receiving the message via a link
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(RegistrationMessageFilter.verify(linkTarget, msg));
    }

    /**
     * Verifies that a request that does not contain a subject
     * does not pass the filter.
     */
    @Test
    public void testVerifyDetectsMissingAction() {
        // GIVEN a registration message lacking a valid subject
        final Message msg = givenAMessageHavingProperties(MY_DEVICE, null);

        // WHEN receiving the message via a link
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(RegistrationMessageFilter.verify(linkTarget, msg));
    }

    /**
     * Verifies that a request with a tenant-level target address containing
     * all required properties passes the filter.
     */
    @Test
    public void testVerifySucceedsForTenantOnlyLinkTarget() {
        // GIVEN a valid registration message for myDevice
        final Message msg = givenAMessageHavingProperties(MY_DEVICE, ACTION_GET);

        // WHEN receiving the message via a link with matching tenant-level target address
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation succeeds
        assertTrue(RegistrationMessageFilter.verify(linkTarget, msg));
    }

    /**
     * Verifies that a valid request that contains the same device ID as the link it
     * has been sent on passes the filter.
     */
    @Test
    public void testVerifySucceedsForMatchingDevice() {
        // GIVEN a registration message for myDevice
        final Message msg = givenAMessageHavingProperties(MY_DEVICE, ACTION_GET);

        // WHEN receiving the message via a link with matching device-level address
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT, MY_DEVICE);

        // THEN message validation succeeds
        assertTrue(RegistrationMessageFilter.verify(linkTarget, msg));
    }

    private static ResourceIdentifier getResourceIdentifier(final String tenant) {
        return getResourceIdentifier(tenant, null);
    }

    private static ResourceIdentifier getResourceIdentifier(final String tenant, final String device) {
        return ResourceIdentifier.from(RegistrationConstants.REGISTRATION_ENDPOINT, tenant, device);
    }

    private static Message givenAMessageHavingProperties(final String deviceId, final String action) {
        final Message msg = ProtonHelper.message();
        msg.setMessageId("msg-id");
        msg.setReplyTo("reply");
        msg.setSubject(action);
        if (deviceId != null) {
            MessageHelper.addDeviceId(msg, deviceId);
        }
        return msg;
    }
}
