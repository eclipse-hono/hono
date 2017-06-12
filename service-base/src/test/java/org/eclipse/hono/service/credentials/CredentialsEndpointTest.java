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
 *
 */

package org.eclipse.hono.service.credentials;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.MessageHelper;
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
 * Tests verifying behavior of {@link CredentialsEndpoint}.
 */
@RunWith(MockitoJUnitRunner.class)
public class CredentialsEndpointTest {

    private static final ResourceIdentifier resource = ResourceIdentifier.from(CredentialsConstants.CREDENTIALS_ENDPOINT, Constants.DEFAULT_TENANT, null);

    @Mock private EventBus eventBus;
    @Mock private Vertx    vertx;

    private CredentialsEndpoint endpoint;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        when(vertx.eventBus()).thenReturn(eventBus);

        endpoint = new CredentialsEndpoint(vertx);
    }

    /**
     * Verifies that the endpoint forwards a request message via the event bus.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testProcessMessageSendsRequestViaEventBus() {

        Message msg = ProtonHelper.message();
        msg.setSubject(CredentialsConstants.OPERATION_GET);
        MessageHelper.addDeviceId(msg, "4711");
        MessageHelper.addTenantId(msg, Constants.DEFAULT_TENANT);
        msg.setBody(new AmqpValue(new JsonObject().put("temp", 15).encode()));

        endpoint.processRequest(msg, resource, Constants.PRINCIPAL_ANONYMOUS);

        verify(eventBus).send(contains(CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_IN), any(JsonObject.class), any(Handler.class));
    }
}
