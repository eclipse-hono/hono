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

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests verifying behavior of {@link TenantAmqpEndpoint}.
 */
@RunWith(MockitoJUnitRunner.class)
public class TenantAmqpEndpointTest {

    private static final ResourceIdentifier resource = ResourceIdentifier.from(TenantConstants.TENANT_ENDPOINT,
            Constants.DEFAULT_TENANT, "someresource");

    @Mock
    private EventBus eventBus;
    @Mock
    private Vertx vertx;

    private TenantAmqpEndpoint endpoint;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        when(vertx.eventBus()).thenReturn(eventBus);

        endpoint = new TenantAmqpEndpoint(vertx);
    }

    /**
     * Verifies that the endpoint forwards a request message via the event bus.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testProcessMessageSendsRequestViaEventBus() {

        Message msg = ProtonHelper.message();
        msg.setSubject(TenantConstants.ACTION_GET);
        MessageHelper.addTenantId(msg, Constants.DEFAULT_TENANT);
        MessageHelper.annotate(msg, resource);

        msg.setBody(new AmqpValue(new JsonObject().put("random", 15).encode()));

        endpoint.processRequest(msg, resource, Constants.PRINCIPAL_ANONYMOUS);

        verify(eventBus).send(contains(TenantConstants.EVENT_BUS_ADDRESS_TENANT_IN), any(JsonObject.class),
                any(Handler.class));
    }

    /**
     * Verifies that the JsonObject constructed for a tenant management message on the event bus contains the tenantId
     * as defined in the {@link RequestResponseApiConstants} class.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCredentialsMessageForEventBus() {

        Message msg = ProtonHelper.message();
        msg.setSubject(TenantConstants.ACTION_GET);
        MessageHelper.addTenantId(msg, Constants.DEFAULT_TENANT);
        MessageHelper.annotate(msg, resource);

        final JsonObject tenantMsg = TenantConstants.getTenantMsg(msg);
        assertNotNull(tenantMsg);
        assertTrue(tenantMsg.containsKey(RequestResponseApiConstants.FIELD_TENANT_ID));
    }
}