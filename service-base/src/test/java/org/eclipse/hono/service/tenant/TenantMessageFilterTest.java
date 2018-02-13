/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.tenant;

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.*;
import org.junit.Test;

import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_RESOURCE;
import static org.eclipse.hono.util.TenantConstants.ACTION_GET;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Test verifying that the filter complies with the tenant message format specification.
 */
public class TenantMessageFilterTest {

    private static final String DEFAULT_TENANT = "DEFAULT_TENANT";

    private static final String RANDOM_METADATA = "{ " +
            " type: \"sometype\"," +
            " other: \"field\"" +
            "}";

    @Test
    public void testVerifyDetectsMissingTenant() {
        // GIVEN a valid tenant GET message without an AMQP value
        final Message msg = givenAMessageHavingProperties(ACTION_GET, null);
        msg.setBody(new AmqpValue(new Binary(RANDOM_METADATA.getBytes())));
        msg.setContentType("application/json");

        // WHEN receiving the message via a link with any tenant
        final ResourceIdentifier linkTarget = getResourceIdentifier(DEFAULT_TENANT);

        // THEN message validation fails
        assertFalse(TenantMessageFilter.verify(linkTarget, msg));
    }

    @Test
    public void testVerifySucceedsForValidGetAction() {
        final Message msg = givenAMessageHavingProperties(ACTION_GET, DEFAULT_TENANT);

        msg.setBody(new AmqpValue(new Binary(RANDOM_METADATA.getBytes())));
        msg.setContentType("application/json");

        // WHEN receiving the message via a link with matching target address
        final ResourceIdentifier linkTarget = getResourceIdentifier(DEFAULT_TENANT);

        // THEN message validation succeeds
        assertTrue(TenantMessageFilter.verify(linkTarget, msg));
        assertMessageAnnotationsContainProperties(msg, DEFAULT_TENANT);
    }

//    @SuppressWarnings("unchecked")
//    @Test
//    public void testCredentialsMessageForEventBus() {
//
//        Message msg = ProtonHelper.message();
//        msg.setSubject(CredentialsConstants.OPERATION_GET);
//        MessageHelper.addDeviceId(msg, "4711");
//        MessageHelper.addTenantId(msg, Constants.DEFAULT_TENANT);
//
//        ResourceIdentifier resource = ResourceIdentifier.from(CredentialsConstants.CREDENTIALS_ENDPOINT,
//                Constants.DEFAULT_TENANT, "4711");
//        MessageHelper.annotate(msg, resource);
//
//        final JsonObject credentialsMsg = CredentialsConstants.getCredentialsMsg(msg, "");
//        assertNotNull(credentialsMsg);
//        assertTrue(credentialsMsg.containsKey(RequestResponseApiConstants.FIELD_TENANT_ID));
//        assertTrue(credentialsMsg.containsKey(RequestResponseApiConstants.FIELD_DEVICE_ID));
//    }

    private void assertMessageAnnotationsContainProperties(final Message msg, final String tenantId) {
        assertNotNull(msg.getMessageAnnotations());
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(MessageHelper.APP_PROPERTY_TENANT_ID)),
                is(tenantId));
        final ResourceIdentifier expectedResourceIdentifier = getResourceIdentifier(DEFAULT_TENANT);
        assertThat(msg.getMessageAnnotations().getValue().get(Symbol.valueOf(APP_PROPERTY_RESOURCE)),
                is(expectedResourceIdentifier.toString()));
    }

    private ResourceIdentifier getResourceIdentifier(final String tenant) {
        return ResourceIdentifier.from(TenantConstants.TENANT_ENDPOINT, tenant, null);
    }

    private Message givenAMessageHavingProperties(final String action, final String tenantId) {
        final Message msg = ProtonHelper.message();
        MessageHelper.addTenantId(msg, tenantId);
        msg.setMessageId("msg");
        msg.setReplyTo("reply");
        msg.setSubject(action);
        return msg;
    }
}