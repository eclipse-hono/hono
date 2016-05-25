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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.telemetry.TelemetryMessageFilter;
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
    public void testVerifyDetectsWrongPrefix() {
        // GIVEN a registration message with an address not starting with the "registration" prefix
        final ResourceIdentifier messageAddress = getResourceIdentifier("wrongPrefix", MY_TENANT, MY_DEVICE);
        final Message msg = givenAMessageHavingRecipient(messageAddress);

        // WHEN receiving the message via a link with target address registration/myTenant
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(TelemetryMessageFilter.verify(linkTarget, messageAddress, msg));
    }

    @Test
    public void testVerifyDetectsTenantIdMismatch() {
        // GIVEN a registration message
        final ResourceIdentifier messageAddress = getResourceIdentifier("anotherTenant", MY_DEVICE);
        final Message msg = givenAMessageHavingRecipient(messageAddress);

        // WHEN receiving the message via a link with target address registration/myTenant
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(RegistrationMessageFilter.verify(linkTarget, messageAddress, msg));
    }

    @Test
    public void testVerifySucceedsForMatchingTenant() {
        // GIVEN a registration message for myDevice
        final String myTenant = MY_TENANT;
        final String myDevice = MY_DEVICE;
        final ResourceIdentifier messageAddress = getResourceIdentifier(myTenant, myDevice);
        final Message msg = givenAMessageHavingRecipient(messageAddress);

        // WHEN receiving the message via a link with matching target address
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation succeeds
        assertTrue(RegistrationMessageFilter.verify(linkTarget, messageAddress, msg));
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
        return getResourceIdentifier(RegistrationConstants.REGISTRATION_ENDPOINT, tenant, device);
    }

    private ResourceIdentifier getResourceIdentifier(final String endpoint, final String tenant, final String device) {
        final StringBuilder resourcePath = new StringBuilder(endpoint).append("/").append(tenant);
        if (device != null) {
            resourcePath.append("/").append(device);
        }
        return ResourceIdentifier.fromString(resourcePath.toString());
    }

    private Message givenAMessageHavingRecipient(final ResourceIdentifier address) {
        final Message msg = ProtonHelper.message(address.toString(), "Hello");
        msg.setApplicationProperties(new ApplicationProperties(new HashMap()));
        msg.getApplicationProperties().getValue().put(MessageHelper.APP_PROPERTY_TENANT_ID, MY_TENANT);
        msg.getApplicationProperties().getValue().put(MessageHelper.APP_PROPERTY_DEVICE_ID, MY_DEVICE);
        return msg;
    }
}
