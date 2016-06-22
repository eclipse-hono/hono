/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.registration;

import static org.eclipse.hono.registration.RegistrationConstants.ACTION_GET;
import static org.eclipse.hono.registration.RegistrationConstants.APP_PROPERTY_ACTION;
import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_DEVICE_ID;
import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_RESOURCE_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Test;

import io.vertx.proton.ProtonHelper;

/**
 * Test verifying that the filter complies with the registration message format specification.
 */
public class RegistrationMessageFilterTest {

    private static final String MY_TENANT = "myTenant";
    private static final String MY_DEVICE = "myDevice";

    @Test
    public void testVerifyDetectsTenantIdMismatch() {
        // GIVEN a valid telemetry message with tenant not matching the link target
        final Message msg = givenAMessageHavingProperties(MY_DEVICE, ACTION_GET, "otherTenant");

        // WHEN receiving the message via a link with mismatching tenant
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(RegistrationMessageFilter.verify(linkTarget, msg));
    }

    @Test
    public void testVerifyDetectsMissingDeviceId() {
        // GIVEN a valid telemetry message without device id
        final Message msg = givenAMessageHavingProperties(null, ACTION_GET);

        // WHEN receiving the message via a link with mismatching tenant
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(RegistrationMessageFilter.verify(linkTarget, msg));
    }
    @Test
    public void testVerifyDetectsMissingAction() {
        // GIVEN a valid telemetry message without device id
        final Message msg = givenAMessageHavingProperties(MY_DEVICE, null);

        // WHEN receiving the message via a link with mismatching tenant
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(RegistrationMessageFilter.verify(linkTarget, msg));
    }

    @Test
    public void testVerifySucceedsWhenTenantIsOmitted() {
        // GIVEN a telemetry message for myDevice
        final Message msg = givenAMessageHavingProperties(MY_DEVICE, ACTION_GET);

        // WHEN receiving the message via a link with matching target address
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation succeeds
        assertTrue(RegistrationMessageFilter.verify(linkTarget, msg));
        assertMessageAnnotationsContainTenantAndDeviceId(msg, MY_TENANT, MY_DEVICE);
    }

    @Test
    public void testVerifySucceedsForMatchingTenant() {
        // GIVEN a telemetry message for myDevice
        final Message msg = givenAMessageHavingProperties(MY_DEVICE, ACTION_GET, MY_TENANT);

        // WHEN receiving the message via a link with matching target address
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation succeeds
        assertTrue(RegistrationMessageFilter.verify(linkTarget, msg));
        assertMessageAnnotationsContainTenantAndDeviceId(msg, MY_TENANT, MY_DEVICE);
    }

    private void assertMessageAnnotationsContainTenantAndDeviceId(final Message msg, final String tenantId,
            final String deviceId) {
        assertNotNull(msg.getMessageAnnotations());
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(MessageHelper.APP_PROPERTY_TENANT_ID)),
                is(tenantId));
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(APP_PROPERTY_DEVICE_ID)),
                is(deviceId));
        final ResourceIdentifier expectedResourceIdentifier = getResourceIdentifier(MY_TENANT, MY_DEVICE);
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(APP_PROPERTY_RESOURCE_ID)),
                is(expectedResourceIdentifier.toString()));
    }

    private ResourceIdentifier getResourceIdentifier(final String tenant) {
        return getResourceIdentifier(tenant, null);
    }

    private ResourceIdentifier getResourceIdentifier(final String tenant, final String device) {
        return getResourceIdentifier(TelemetryConstants.TELEMETRY_ENDPOINT, tenant, device);
    }

    private ResourceIdentifier getResourceIdentifier(final String endpoint, final String tenant, final String device) {
        final StringBuilder resourcePath = new StringBuilder(endpoint).append("/").append(tenant);
        if (device != null) {
            resourcePath.append("/").append(device);
        }
        return ResourceIdentifier.fromString(resourcePath.toString());
    }

    private Message givenAMessageHavingProperties(final String deviceId, final String action) {
        return givenAMessageHavingProperties(deviceId, action, null);
    }

    private Message givenAMessageHavingProperties(final String deviceId, final String action, final String tenantId) {
        final Message msg = ProtonHelper.message("Hello");
        msg.setMessageId("msg");
        MessageHelper.addDeviceId(msg, deviceId);
        MessageHelper.addProperty(msg, APP_PROPERTY_ACTION, action);
        if (tenantId != null) {
            MessageHelper.addTenantId(msg, tenantId);
        }
        return msg;
    }
}
