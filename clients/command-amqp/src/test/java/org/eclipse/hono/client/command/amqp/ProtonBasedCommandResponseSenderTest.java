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

package org.eclipse.hono.client.command.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.Commands;
import org.eclipse.hono.client.command.amqp.ProtonBasedCommandResponseSender;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.test.TracingMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link ProtonBasedCommandResponseSender}.
 *
 */
@ExtendWith(VertxExtension.class)
public class ProtonBasedCommandResponseSenderTest {

    private static final String CORRELATION_ID = "the-correlation-id";
    private static final String TENANT_ID = "tenant";
    private static final String DEVICE_ID = "4711";
    private static final String REPLY_TO_ID = "the-reply-to-id";

    private ProtonBasedCommandResponseSender sender;
    private ProtonSender protonSender;
    private HonoConnection connection;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);

        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(mock(EventBus.class));

        final ClientConfigProperties clientConfigProperties = new ClientConfigProperties();
        connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx, clientConfigProperties, tracer);
        when(connection.isConnected()).thenReturn(Future.succeededFuture());
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        protonSender = AmqpClientUnitTestHelper.mockProtonSender();
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.succeededFuture(protonSender));

        sender = new ProtonBasedCommandResponseSender(connection, SendMessageSampler.Factory.noop(), false);
    }

    /**
     * Verifies that a ClientErrorException when creating an AMQP sender is returned as a server error
     * on the <em>sendCommandResponse</em> invocation.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSenderClientCreationErrorIsMappedToServerError(final VertxTestContext ctx) {

        // GIVEN a scenario where creating the AMQP sender always fails with a client error
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.failedFuture(new ClientErrorException(
                HttpURLConnection.HTTP_NOT_FOUND, "cannot open sender")));

        // WHEN sending a command response message
        final CommandResponse commandResponse = CommandResponse.fromRequestId(
                Commands.getRequestId(CORRELATION_ID, REPLY_TO_ID, DEVICE_ID),
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        sender.sendCommandResponse(commandResponse, span.context())
                .onComplete(ctx.failing(thr -> {
                    ctx.verify(() -> {
                        // THEN the invocation is failed with a server error
                        assertThat(thr).isInstanceOf(ServiceInvocationException.class);
                        assertThat(((ServiceInvocationException) thr).getErrorCode())
                                .isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                    });
                    ctx.completeNow();
                }));
    }

}
