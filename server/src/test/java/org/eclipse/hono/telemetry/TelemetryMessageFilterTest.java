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

import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_RESOURCE;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.service.registration.RegistrationMessageFilter;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Test;

import io.vertx.proton.ProtonHelper;

/**
 * Test verifying that the filter complies with the telemetry message format specification.
 */
public class TelemetryMessageFilterTest {

    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final String MY_TENANT = "myTenant";
    private static final String MY_DEVICE = "myDevice";

    @Test
    public void testVerifyDetectsDeviceIdMismatch() {
        // GIVEN a valid telemetry message with device id not matching the link target
        final Message msg = givenAMessageHavingProperties(MY_DEVICE + "_1", MY_TENANT);

        // WHEN receiving the message via a link with mismatching tenant
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT, MY_DEVICE);

        // THEN message validation fails
        assertFalse(RegistrationMessageFilter.verify(linkTarget, msg));
    }

    @Test
    public void testVerifyDetectsMissingDeviceId() {
        // GIVEN a valid telemetry message without device id
        final Message msg = givenAMessageHavingProperties(null);

        // WHEN receiving the message via a link with mismatching tenant
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(TelemetryMessageFilter.verify(linkTarget, msg));
    }

    @Test
    public void testVerifyDetectsMissingContentType() {
        // GIVEN a valid telemetry message without content type
        final Message msg = givenAMessageHavingProperties(MY_DEVICE, MY_TENANT, null, new byte[]{0x00});

        // WHEN receiving the message via a link with matching tenant
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(TelemetryMessageFilter.verify(linkTarget, msg));
    }

    @Test
    public void testVerifyDetectsMissingBody() {
        // GIVEN a valid telemetry message without body
        final Message msg = givenAMessageHavingProperties(MY_DEVICE, MY_TENANT, CONTENT_TYPE_OCTET_STREAM, null);

        // WHEN receiving the message via a link with matching tenant
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation fails
        assertFalse(TelemetryMessageFilter.verify(linkTarget, msg));
    }

    @Test
    public void testVerifySucceedsForTenantOnlyLinkTarget() {
        // GIVEN a telemetry message for myDevice
        final Message msg = givenAMessageHavingProperties(MY_DEVICE);

        // WHEN receiving the message via a link with matching target address
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT);

        // THEN message validation succeeds
        assertTrue(TelemetryMessageFilter.verify(linkTarget, msg));
        assertMessageAnnotationsContainProperties(msg, MY_TENANT, MY_DEVICE);
    }

    @Test
    public void testVerifySucceedsForMatchingDevice() {
        // GIVEN a telemetry message for myDevice
        final Message msg = givenAMessageHavingProperties(MY_DEVICE, MY_TENANT);

        // WHEN receiving the message via a link with matching target address
        final ResourceIdentifier linkTarget = getResourceIdentifier(MY_TENANT, MY_DEVICE);

        // THEN message validation succeeds
        assertTrue(TelemetryMessageFilter.verify(linkTarget, msg));
        assertMessageAnnotationsContainProperties(msg, MY_TENANT, MY_DEVICE);
    }

    private void assertMessageAnnotationsContainProperties(final Message msg, final String tenantId,
            final String deviceId) {
        assertNotNull(msg.getMessageAnnotations());
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(MessageHelper.APP_PROPERTY_TENANT_ID)),
                is(tenantId));
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(MessageHelper.APP_PROPERTY_DEVICE_ID)),
                is(deviceId));
        final ResourceIdentifier expectedResourceIdentifier = getResourceIdentifier(MY_TENANT, MY_DEVICE);
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(APP_PROPERTY_RESOURCE)),
                is(expectedResourceIdentifier.toString()));
    }

    private ResourceIdentifier getResourceIdentifier(final String tenant) {
        return getResourceIdentifier(tenant, null);
    }

    private ResourceIdentifier getResourceIdentifier(final String tenant, final String device) {
        return ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, tenant, device);
    }

    private Message givenAMessageHavingProperties(final String deviceId) {
        return givenAMessageHavingProperties(deviceId, null);
    }

    private Message givenAMessageHavingProperties(final String deviceId, final String tenantId) {
        return givenAMessageHavingProperties(deviceId, tenantId, CONTENT_TYPE_OCTET_STREAM, new byte[]{0x00});
    }

    private Message givenAMessageHavingProperties(final String deviceId, final String tenantId, final String contentType, final byte[] payload) {
        final Message msg = ProtonHelper.message("Hello");
        MessageHelper.addDeviceId(msg, deviceId);
        if (tenantId != null) {
            MessageHelper.addTenantId(msg, tenantId);
        }
        if (contentType != null) {
            msg.setContentType(contentType);
        }
        if (payload != null) {
            msg.setBody(new Data(new Binary(payload)));
        }
        return msg;
    }
}
