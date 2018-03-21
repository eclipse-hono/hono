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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;

/**
 * Tests verifying behavior of {@link TenantAmqpEndpoint}.
 */
@RunWith(MockitoJUnitRunner.class)
public class TenantAmqpEndpointTest {

    private static final ResourceIdentifier resource = ResourceIdentifier.from(TenantConstants.TENANT_ENDPOINT, Constants.DEFAULT_TENANT, null);

    @Mock private EventBus eventBus;
    @Mock private Vertx    vertx;

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
    @Test
    public void testProcessMessageSendsRequestViaEventBus() {

        final Message msg = ProtonHelper.message();
        msg.setMessageId("random-id");
        msg.setSubject(TenantConstants.TenantAction.get.toString());
        MessageHelper.addTenantId(msg, Constants.DEFAULT_TENANT);
        MessageHelper.annotate(msg, resource);

        endpoint.processRequest(msg, resource, Constants.PRINCIPAL_ANONYMOUS);

        verify(eventBus).send(eq(TenantConstants.EVENT_BUS_ADDRESS_TENANT_IN), any(JsonObject.class));
    }
}
