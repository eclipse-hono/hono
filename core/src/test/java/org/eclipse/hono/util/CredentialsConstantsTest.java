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

import static org.hamcrest.CoreMatchers.is; 
import static org.junit.Assert.*;

import org.apache.qpid.proton.message.Message;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;


/**
 * Tests verifying behavior of {@link CredentialsConstants}.
 *
 */
public class CredentialsConstantsTest {

    /**
     * Verifies that the JSON message created from an AMQP request message
     * contains all required properties.
     */
    @Test
    public void testCredentialsMessageForEventBus() {

        final Message msg = ProtonHelper.message();
        msg.setSubject(CredentialsConstants.OPERATION_GET);
        final ResourceIdentifier target = ResourceIdentifier.from(
                CredentialsConstants.CREDENTIALS_ENDPOINT, Constants.DEFAULT_TENANT, null);

        final JsonObject credentialsMsg = CredentialsConstants.getCredentialsMsg(msg, target);

        assertThat(credentialsMsg.getString(CredentialsConstants.FIELD_TENANT_ID), is(Constants.DEFAULT_TENANT));
        assertFalse(credentialsMsg.containsKey(CredentialsConstants.FIELD_DEVICE_ID));
        assertThat(credentialsMsg.getString(MessageHelper.SYS_PROPERTY_SUBJECT), is(CredentialsConstants.OPERATION_GET));
    }
}
