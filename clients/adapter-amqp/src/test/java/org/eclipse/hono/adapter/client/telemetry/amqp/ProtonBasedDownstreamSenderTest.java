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

package org.eclipse.hono.adapter.client.telemetry.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link ProtonBasedDownstreamSender}.
 *
 */
@ExtendWith(VertxExtension.class)
public class ProtonBasedDownstreamSenderTest {

    private ProtonBasedDownstreamSender sender;
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

        sender = new ProtonBasedDownstreamSender(connection, SendMessageSampler.Factory.noop(), true, false);
    }

    /**
     * Verifies that a ClientErrorException when creating an AMQP sender is returned as a server error
     * on the <em>sendEvent</em> invocation.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSenderClientCreationErrorIsMappedToServerErrorOnSendEvent(final VertxTestContext ctx) {

        // GIVEN a scenario where creating the AMQP sender always fails with a client error
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.failedFuture(new ClientErrorException(
                HttpURLConnection.HTTP_NOT_FOUND, "cannot open sender")));

        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        final RegistrationAssertion device = new RegistrationAssertion("4711");
        // WHEN sending an event message
        sender.sendEvent(tenant, device, null, null, null, span.context())
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

    /**
     * Verifies that a ClientErrorException when creating an AMQP sender is returned as a server error
     * on the <em>sendTelemetry</em> invocation.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSenderClientCreationErrorIsMappedToServerErrorOnSendTelemetry(final VertxTestContext ctx) {

        // GIVEN a scenario where creating the AMQP sender always fails with a client error
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.failedFuture(new ClientErrorException(
                HttpURLConnection.HTTP_NOT_FOUND, "cannot open sender")));

        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        final RegistrationAssertion device = new RegistrationAssertion("4711");
        // WHEN sending a telemetry message
        sender.sendTelemetry(tenant, device, QoS.AT_MOST_ONCE, null, null, null, span.context())
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

    /**
     * Verifies that the proton message being transferred when sending an event is marked as durable.
     */
    @Test
    public void testSendEventMarksMessageAsDurable() {

        // WHEN sending an event
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        final RegistrationAssertion device = new RegistrationAssertion("4711");
        sender.sendEvent(tenant, device, "text/plain", Buffer.buffer("hello"), null, span.context());
        verify(protonSender).send(argThat(Message::isDurable), VertxMockSupport.anyHandler());
    }
}
