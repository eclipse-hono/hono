/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.List;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.commandrouter.CommandRouterResult;
import org.eclipse.hono.commandrouter.CommandRouterService;
import org.eclipse.hono.util.CommandRouterConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonHelper;

/**
 * Tests verifying behavior of {@link DelegatingCommandRouterAmqpEndpoint}.
 *
 */
@ExtendWith(VertxExtension.class)
class DelegatingCommandRouterAmqpEndpointTest {

    private DelegatingCommandRouterAmqpEndpoint<CommandRouterService> endpoint;
    private CommandRouterService service;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    void setUp() {
        service = mock(CommandRouterService.class);
        when(service.enableCommandRouting(anyList(), any())).thenReturn(
                Future.succeededFuture(CommandRouterResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
        final Vertx vertx = mock(Vertx.class);
        endpoint = new DelegatingCommandRouterAmqpEndpoint<>(vertx, service);
    }

    @Test
    void testProcessEnableCommandRoutingAcceptsValidJsonArray(final VertxTestContext ctx) {
        final Message request = getEnableCommandRoutingRequestMessage();
        endpoint.processEnableCommandRouting(
                request,
                ResourceIdentifier.from(CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, "tenant", null),
                NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> {
                    verify(service).enableCommandRouting(anyList(), any());
                    assertThat(AmqpUtils.getStatus(response)).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
                });
                ctx.completeNow();
            }));
    }

    @Test
    void testEnableCommandRoutingRequestPassesFormalVerification() {
        final var request = getEnableCommandRoutingRequestMessage();
        final var resourceId = ResourceIdentifier.from(CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, "tenant", null);
        assertThat(endpoint.passesFormalVerification(resourceId, request)).isTrue();
    }

    private Message getEnableCommandRoutingRequestMessage() {
        final JsonArray tenants = new JsonArray(List.of("tenant1", "tenant2"));
        final Message request = ProtonHelper.message();
        request.setSubject(CommandRouterConstants.CommandRouterAction.ENABLE_COMMAND_ROUTING.getSubject());
        request.setMessageId("abc");
        request.setReplyTo("reply/to/me");
        request.setBody(new Data(new Binary(tenants.toBuffer().getBytes())));
        return request;
    }
}
