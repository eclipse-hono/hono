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

package org.eclipse.hono.client.telemetry.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
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

        span = NoopSpan.INSTANCE;
        final Tracer tracer = NoopTracerFactory.create();
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(mock(EventBus.class));

        connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx, new ClientConfigProperties(), tracer);
        when(connection.isConnected()).thenReturn(Future.succeededFuture());
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());

        protonSender = AmqpClientUnitTestHelper.mockProtonSender();
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.succeededFuture(protonSender));

        sender = new ProtonBasedDownstreamSender(connection, SendMessageSampler.Factory.noop(), true, false);
    }

    /**
     * Verifies that a ClientErrorException that occurs when creating an AMQP sender link is mapped to a
     * ServerErrorException with status 503 by the <em>sendEvent</em> method.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSenderClientCreationErrorIsMappedToServerErrorOnSendEvent(final VertxTestContext ctx) {

        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        final RegistrationAssertion device = new RegistrationAssertion("4711");

        testSenderClientCreationErrorIsMappedToServerErrorOnSending(
                ctx,
                () -> sender.sendEvent(tenant, device, null, null, null, span.context()));
    }

    /**
     * Verifies that a ClientErrorException that occurs when creating an AMQP sender link is mapped to a
     * ServerErrorException with status 503 by the <em>sendTelemetry</em> method.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSenderClientCreationErrorIsMappedToServerErrorOnSendTelemetry(final VertxTestContext ctx) {

        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        final RegistrationAssertion device = new RegistrationAssertion("4711");

        testSenderClientCreationErrorIsMappedToServerErrorOnSending(
                ctx,
                () -> sender.sendTelemetry(tenant, device, QoS.AT_MOST_ONCE, null, null, null, span.context()));
    }

    private void testSenderClientCreationErrorIsMappedToServerErrorOnSending(
            final VertxTestContext ctx,
            final Supplier<Future<Void>> sendMethod) {

        // GIVEN a scenario where creating the AMQP sender always fails with a client error
        when(connection.createSender(anyString(), any(), any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(
                        HttpURLConnection.HTTP_NOT_FOUND, "cannot open sender")));

        // WHEN sending a message
        sendMethod.get()
                .onComplete(ctx.failing(thr -> {
                    ctx.verify(() -> {
                        // THEN the invocation is failed with a server error
                        assertThat(thr).isInstanceOf(ServerErrorException.class);
                        assertThat(((ServerErrorException) thr).getErrorCode())
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

    /**
     * Verifies that a TTL values in the <em>properties</em> parameter of
     * {@link org.eclipse.hono.client.telemetry.EventSender#sendEvent(TenantObject, RegistrationAssertion, String, Buffer, Map, io.opentracing.SpanContext)}
     * are correctly taken as seconds and set as milliseconds at the message.
     */
    @Test
    public void testThatTtlIsSetInMilliseconds() {

        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        final RegistrationAssertion device = new RegistrationAssertion("4711");

        // WHEN sending an event with properties that contain a TTL in seconds
        final Map<String, Object> props = new HashMap<>();
        props.put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 2);
        sender.sendEvent(tenant, device, "text/plain", Buffer.buffer("hello"), props, span.context());

        // THEN the TTL at the message is correctly set in milliseconds
        verify(protonSender).send(argThat(message -> message.getTtl() == 2000), VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that a downstream telemetry message always contains a creation-time
     * and a time-to-live as defined at the tenant level.
     *
     * @param qosLevel The quality of service used for sending the message.
     * @param expectedTtl The time to live (in millis) expected to be set on the message.
     */
    @ParameterizedTest
    @CsvSource(value = { "AT_MOST_ONCE,10000", "AT_LEAST_ONCE,20000" })
    public void testDownstreamTelemetryMessageHasCreationTimeAndTtl(
            final QoS qosLevel,
            final long expectedTtl) {

        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setResourceLimits(new ResourceLimits()
                .setMaxTtlTelemetryQoS0(10L)
                .setMaxTtlTelemetryQoS1(20L));
        final RegistrationAssertion device = new RegistrationAssertion("4711");

        // WHEN sending a message without specifying any properties
        sender.sendTelemetry(tenant, device, qosLevel, "text/plain", Buffer.buffer("hello"), null, span.context());

        // THEN the message contains a creation-time
        verify(protonSender).send(
                argThat(message -> message.getCreationTime() > 0 && message.getTtl() == expectedTtl),
                VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that a downstream event always contains a creation-time
     * and a time-to-live as defined at the tenant level.
     */
    @Test
    public void testDownstreamEventHasCreationTimeAndTtl() {

        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        final RegistrationAssertion device = new RegistrationAssertion("4711");
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(60L));

        // WHEN sending a message without specifying any properties
        sender.sendEvent(tenant, device, "text/plain", Buffer.buffer("hello"), null, span.context());

        // THEN the message contains a creation-time
        verify(protonSender).send(argThat(
                message -> message.getCreationTime() > 0 && message.getTtl() == 60000L),
                VertxMockSupport.anyHandler());
    }

}
