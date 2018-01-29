/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.tenant;

import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_RESOURCE;
import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_TENANT_ID;
import static org.eclipse.hono.util.TenantConstants.Action;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantConstants;
import org.junit.Test;

import io.vertx.proton.ProtonHelper;

/**
 * Test verifying that the filter complies with the tenant message format specification.
 */
public class TenantMessageFilterTest {

    private static final String DEFAULT_TENANT = "DEFAULT_TENANT";

    /**
     * Verifies that {@link TenantMessageFilter#verify(ResourceIdentifier, Message)} detects if the mandatory tenantId
     * property is missing.
     */
    @Test
    public void testVerifyDetectsMissingTenantProperty() {
        // GIVEN a valid tenant GET message without an AMQP value
        final Message msg = givenAMessageHavingProperties(Action.ACTION_GET);
        // WHEN receiving the message via a link with any tenant
        final ResourceIdentifier linkTarget = getResourceIdentifier(DEFAULT_TENANT);

        // THEN message validation fails
        assertFalse(TenantMessageFilter.verify(linkTarget, msg));
    }

    /**
     * Verifies that {@link TenantMessageFilter#verify(ResourceIdentifier, Message)} succeeds for a valid message.
     */
    @Test
    public void testVerifySucceedsForValidGetAction() {
        // GIVEN a tenant GET message for tenant DEFAULT_TENANT
        final Message msg = givenAMessageHavingProperties(Action.ACTION_GET);
        MessageHelper.addProperty(msg, APP_PROPERTY_TENANT_ID, DEFAULT_TENANT);
        // WHEN receiving the message via a link with matching target address
        final ResourceIdentifier linkTarget = getResourceIdentifier(DEFAULT_TENANT);

        // THEN message validation succeeds
        assertTrue(TenantMessageFilter.verify(linkTarget, msg));
        assertMessageAnnotationsContainProperties(msg, DEFAULT_TENANT);
    }

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

    private Message givenAMessageHavingProperties(final Action action) {
        final Message msg = ProtonHelper.message();
        msg.setMessageId("msg");
        msg.setReplyTo("reply");
        msg.setSubject(action.toString());
        return msg;
    }
}
