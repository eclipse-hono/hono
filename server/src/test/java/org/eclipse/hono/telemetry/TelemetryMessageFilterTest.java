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
package org.eclipse.hono.telemetry;

import static org.junit.Assert.*;

import static org.hamcrest.CoreMatchers.is;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Test;

import io.vertx.proton.ProtonHelper;

/**
 * Test verifying that the filter complies with the telemetry message format specification.
 */
public class TelemetryMessageFilterTest {

    private static final String MY_TENANT = "myTenant";
    private static final String MY_DEVICE = "myDevice";

    @Test
    public void testVerifyDetectsWrongPrefix() {
        // GIVEN a telemetry message with an address not starting with the "telemetry" prefix
        ResourceIdentifier messageAddress = getResourceIdentifier("wrongPrefix", MY_TENANT, MY_DEVICE);
        Message msg = givenAMessageHavingRecipient(messageAddress);

        // WHEN receiving the message via a link with target address telemetry/myTenant
        ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(TelemetryMessageFilter.verify(linkTarget, messageAddress, msg));
    }

    @Test
    public void testVerifyDetectsTenantIdMismatch() {
        // GIVEN a telemetry message
        ResourceIdentifier messageAddress = getResourceIdentifier("anotherTenant", MY_DEVICE);
        Message msg = givenAMessageHavingRecipient(messageAddress);

        // WHEN receiving the message via a link with target address telemetry/myTenant
        ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(TelemetryMessageFilter.verify(linkTarget, messageAddress, msg));
    }

    @Test
    public void testVerifySucceedsForMatchingTenant() {
        // GIVEN a telemetry message for myDevice
        String myTenant = MY_TENANT;
        String myDevice = MY_DEVICE;
        ResourceIdentifier messageAddress = getResourceIdentifier(myTenant, myDevice);
        Message msg = givenAMessageHavingRecipient(messageAddress);

        // WHEN receiving the message via a link with matching target address
        ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation succeeds
        assertTrue(TelemetryMessageFilter.verify(linkTarget, messageAddress, msg));
        assertMessageAnnotationsContainTenantAndDeviceId(msg, myTenant, myDevice);
    }

    private void assertMessageAnnotationsContainTenantAndDeviceId(final Message msg, final String tenantId,
            final String deviceId) {
        assertNotNull(msg.getMessageAnnotations());
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(MessageHelper.APP_PROPERTY_TENANT_ID)),
                is(tenantId));
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(MessageHelper.APP_PROPERTY_DEVICE_ID)),
                is(deviceId));
    }

    private ResourceIdentifier getResourceIdentifier(final String tenant) {
        return getResourceIdentifier(tenant, null);
    }

    private ResourceIdentifier getResourceIdentifier(final String tenant, final String device) {
        return getResourceIdentifier(TelemetryConstants.TELEMETRY_ENDPOINT, tenant, device);
    }

    private ResourceIdentifier getResourceIdentifier(final String endpoint, final String tenant, final String device) {
        StringBuilder resourcePath = new StringBuilder(endpoint).append("/").append(tenant);
        if (device != null) {
            resourcePath.append("/").append(device);
        } 
        return ResourceIdentifier.fromString(resourcePath.toString());
    }

    private Message givenAMessageHavingRecipient(final ResourceIdentifier address) {
        Message msg = ProtonHelper.message(address.toString(), "Hello");
        return msg;
    }
}
