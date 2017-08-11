/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.service.registration;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;

/**
 * Tests
 */
@RunWith(MockitoJUnitRunner.class)
public class RegistrationAmqpEndpointTest {

    private static final ResourceIdentifier resource = ResourceIdentifier.from(RegistrationConstants.REGISTRATION_ENDPOINT, Constants.DEFAULT_TENANT, null);

    @Mock private EventBus eventBus;
    @Mock private Vertx    vertx;

    private RegistrationAmqpEndpoint endpoint;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        when(vertx.eventBus()).thenReturn(eventBus);

        endpoint = new RegistrationAmqpEndpoint(vertx);
    }

    /**
     * Verifies that the endpoint forwards a request message via the event bus.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testProcessMessageSendsRequestViaEventBus() {

        Message msg = ProtonHelper.message();
        msg.setSubject(RegistrationConstants.ACTION_ASSERT);
        MessageHelper.addDeviceId(msg, "4711");
        MessageHelper.addTenantId(msg, Constants.DEFAULT_TENANT);
        msg.setBody(new AmqpValue(new JsonObject().put("temp", 15).encode()));

        endpoint.processRequest(msg, resource, Constants.PRINCIPAL_ANONYMOUS);

        verify(eventBus).send(contains(RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN), any(JsonObject.class), any(Handler.class));
    }
}
