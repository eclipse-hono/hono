/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.adapter.monitoring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Verifies behavior of {@link HonoEventConnectionEventProducer}.
 *
 */
@ExtendWith(VertxExtension.class)
class HonoEventConnectionEventProducerTest {

    private HonoEventConnectionEventProducer producer;
    private ConnectionEventProducer.Context context;
    private EventSender sender;
    private TenantClient tenantClient;
    private TenantObject tenant;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        tenantClient = mock(TenantClient.class);
        sender = mock(EventSender.class);
        when(sender.sendEvent(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                anyString(),
                any(),
                any(),
                any())).thenReturn(Future.succeededFuture());
        context = mock(ConnectionEventProducer.Context.class);
        when(context.getMessageSenderClient()).thenReturn(sender);
        when(context.getTenantClient()).thenReturn(tenantClient);
        producer = new HonoEventConnectionEventProducer();
    }

    @Test
    void testConnectedSucceeds(final VertxTestContext ctx) {

        final String tenantId = "tenant";
        final Device authenticatedDevice = new Device(tenantId, "device");
        tenant = new TenantObject(tenantId, true)
                .setResourceLimits(new ResourceLimits().setMaxTtl(500));
        when(tenantClient.get(anyString(), any())).thenReturn(Future.succeededFuture(tenant));

        producer.connected(context, "device-internal-id", "custom-adapter", authenticatedDevice, new JsonObject(), null)
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    verify(sender).sendEvent(
                            eq(tenant),
                            any(RegistrationAssertion.class),
                            eq(EventConstants.EVENT_CONNECTION_NOTIFICATION_CONTENT_TYPE),
                            any(),
                            any(),
                            any());
                });
                ctx.completeNow();
            }));
    }

}
