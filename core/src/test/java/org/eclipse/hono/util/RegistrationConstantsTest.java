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
import static org.junit.Assert.assertThat;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;


/**
 * Tests verifying behavior of {@link RegistrationConstants}.
 *
 */
public class RegistrationConstantsTest {

    /**
     * Verifies that the JSON message created from an AMQP request message
     * contains all required information.
     */
    @Test
    public void testGetRegistrationMessageIncludesRelevantInformation() {

        final JsonObject payload = new JsonObject().put("manufacturer", "bumlux");
        final Message msg = ProtonHelper.message();
        msg.setSubject(RegistrationConstants.ACTION_REGISTER);
        MessageHelper.addDeviceId(msg, "4711");
        msg.setBody(new Data(new Binary(payload.toBuffer().getBytes())));
        final ResourceIdentifier target = ResourceIdentifier.from(RegistrationConstants.REGISTRATION_ENDPOINT, Constants.DEFAULT_TENANT, null);

        final JsonObject registrationJsonMessage = RegistrationConstants.getRegistrationMsg(msg, target);

        assertThat(registrationJsonMessage.getString(MessageHelper.SYS_PROPERTY_SUBJECT), is(RegistrationConstants.ACTION_REGISTER));
        assertThat(registrationJsonMessage.getString(RegistrationConstants.FIELD_TENANT_ID), is(Constants.DEFAULT_TENANT));
        assertThat(registrationJsonMessage.getString(RegistrationConstants.FIELD_DEVICE_ID), is("4711"));
        assertThat(registrationJsonMessage.getJsonObject(RegistrationConstants.FIELD_PAYLOAD), is(payload));
    }
}
