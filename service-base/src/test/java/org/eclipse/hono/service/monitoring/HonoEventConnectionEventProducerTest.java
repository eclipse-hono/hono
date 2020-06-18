/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;


/**
 * Verifies behavior of {@link HonoEventConnectionEventProducer}.
 *
 */
@ExtendWith(VertxExtension.class)
class HonoEventConnectionEventProducerTest {

    private HonoEventConnectionEventProducer producer;
    private ConnectionEventProducer.Context context;
    private DownstreamSenderFactory senderFactory;
    private DownstreamSender sender;
    private TenantClientFactory tenantClientFactory;
    private TenantClient tenantClient;
    private TenantObject tenant;

    /**
     */
    @BeforeEach
    void setUp() {
        tenantClient = mock(TenantClient.class);
        tenantClientFactory = mock(TenantClientFactory.class);
        when(tenantClientFactory.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));
        sender = mock(DownstreamSender.class);
        when(sender.send(any(Message.class))).thenReturn(Future.succeededFuture(mock(ProtonDelivery.class)));
        senderFactory = mock(DownstreamSenderFactory.class);
        when(senderFactory.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(sender));
        context = mock(ConnectionEventProducer.Context.class);
        when(context.getMessageSenderClient()).thenReturn(senderFactory);
        when(context.getTenantClientFactory()).thenReturn(tenantClientFactory);
        producer = new HonoEventConnectionEventProducer();
    }

    @Test
    void testConnectedSucceeds(final VertxTestContext ctx) {

        final String tenantId = "tenant";
        final Device authenticatedDevice = new Device(tenantId, "device");
        tenant = new TenantObject()
                .setTenantId(tenantId)
                .setResourceLimits(new ResourceLimits().setMaxTtl(500));
        when(tenantClient.get(anyString())).thenReturn(Future.succeededFuture(tenant));

        producer.connected(context, "device-internal-id", "custom-adapter", authenticatedDevice, new JsonObject())
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    final ArgumentCaptor<Message> event = ArgumentCaptor.forClass(Message.class);
                    verify(sender).send(event.capture());
                    assertThat(event.getValue().getAddress()).isEqualTo(String.format("event/%s", tenantId));
                    assertThat(event.getValue().getTtl()).isEqualTo(500_000);
                    assertThat(event.getValue().getContentType()).isEqualTo(EventConstants.EVENT_CONNECTION_NOTIFICATION_CONTENT_TYPE);
                });
                ctx.completeNow();
            }));
    }

}
