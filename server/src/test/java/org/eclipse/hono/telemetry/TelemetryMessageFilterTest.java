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
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
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
        Message msg = givenAMessageHavingRecipient("wrongPrefix/myTenant/myDevice");

        // WHEN receiving the message via a link with target address telemetry/myTenant
        String tenantFromLink = MY_TENANT;

        // THEN message validation fails
        assertFalse(TelemetryMessageFilter.verify(tenantFromLink, msg));
    }

    @Test
    public void testVerifyDetectsTenantIdMismatch() {
        // GIVEN a telemetry message
        Message msg = givenAMessageHavingRecipient("telemetry/anotherTenant/myDevice");

        // WHEN receiving the message via a link with target address telemetry/myTenant
        String tenantFromLink = MY_TENANT;

        // THEN message validation fails
        assertFalse(TelemetryMessageFilter.verify(tenantFromLink, msg));
    }

    @Test
    public void testVerifySucceedsForMatchingTenant() {
        // GIVEN a telemetry message for myDevice
        String myTenant = MY_TENANT;
        String myDevice = MY_DEVICE;
        Message msg = givenAMessageHavingRecipient(String.format("telemetry/%s/%s", myTenant, myDevice));

        // WHEN receiving the message via a link with matching target address
        String tenantFromLink = MY_TENANT;

        // THEN message validation succeeds
        assertTrue(TelemetryMessageFilter.verify(tenantFromLink, msg));
        assertMessageAnnotationsContainTenantAndDeviceId(msg, myTenant, myDevice);
    }

    @Test
    public void testVerifySucceedsForDefaultTenant() {
        // GIVEN a telemetry message for default tenant
        String myDevice = MY_DEVICE;
        Message msg = givenAMessageHavingRecipient(String.format("telemetry/%s", myDevice));

        // WHEN receiving the message via a link with target address telemetry/DEFAULT_TENANT
        String tenantFromLink = Constants.DEFAULT_TENANT;

        // THEN message validation succeeds
        assertTrue(TelemetryMessageFilter.verify(tenantFromLink, msg, true));
        assertMessageAnnotationsContainTenantAndDeviceId(msg, Constants.DEFAULT_TENANT, myDevice);
    }

    private void assertMessageAnnotationsContainTenantAndDeviceId(final Message msg, final String tenantId,
            final String deviceId) {
        assertNotNull(msg.getMessageAnnotations());
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(MessageHelper.APP_PROPERTY_TENANT_ID)),
                is(tenantId));
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(MessageHelper.APP_PROPERTY_DEVICE_ID)),
                is(deviceId));
    }

    private Message givenAMessageHavingRecipient(final String address) {
        Message msg = ProtonHelper.message(address, "Hello");
        return msg;
    }
}
