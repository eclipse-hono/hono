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
 * Tests CredentialsConstants.
 */
public class CredentialsConstantTest {
    private static final ResourceIdentifier resource = ResourceIdentifier.from(CredentialsConstants.CREDENTIALS_ENDPOINT, Constants.DEFAULT_TENANT, "4711");

    /**
     * Verifies that the JsonObject constructed for a credentials message on the event bus contains the tenantId and
     * deviceId as defined in the {@link RequestResponseApiConstants} class.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCredentialsMessageForEventBus() {

        final Message msg = ProtonHelper.message();
        msg.setSubject(CredentialsConstants.OPERATION_GET);
        MessageHelper.addDeviceId(msg, "4711");
        MessageHelper.addTenantId(msg, Constants.DEFAULT_TENANT);
        MessageHelper.annotate(msg, resource);

        final JsonObject credentialsMsg = CredentialsConstants.getCredentialsMsg(msg);
        assertNotNull(credentialsMsg);
        assertTrue(credentialsMsg.containsKey(RequestResponseApiConstants.FIELD_TENANT_ID));
        assertTrue(credentialsMsg.containsKey(RequestResponseApiConstants.FIELD_DEVICE_ID));
    }

}
