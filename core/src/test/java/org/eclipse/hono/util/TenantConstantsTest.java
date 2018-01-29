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

package org.eclipse.hono.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.qpid.proton.message.Message;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;

/**
 * Tests TenantConstants.
 */
public class TenantConstantsTest {
    private static final ResourceIdentifier resource = ResourceIdentifier.from(TenantConstants.TENANT_ENDPOINT, Constants.DEFAULT_TENANT, null);

    /**
     * Verifies that the JsonObject constructed for a tenant message (intended for the event bus) contains the tenantId
     * as defined in the {@link RequestResponseApiConstants} class.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testTenantMessageForEventBus() {

        final Message msg = ProtonHelper.message();
        msg.setSubject(TenantConstants.Action.ACTION_GET.toString());
        MessageHelper.addTenantId(msg, Constants.DEFAULT_TENANT);
        MessageHelper.annotate(msg, resource);

        final JsonObject tenantMsg = TenantConstants.getTenantMsg(msg);
        assertNotNull(tenantMsg);
        assertTrue(tenantMsg.containsKey(RequestResponseApiConstants.FIELD_TENANT_ID));
    }
}
