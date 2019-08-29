/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.client.impl;

import static org.eclipse.hono.client.impl.VertxMockSupport.anyHandler;
import static org.eclipse.hono.util.Constants.DEFAULT_TENANT;
import static org.eclipse.hono.util.DeviceConnectionConstants.DeviceConnectionAction.GET_LAST_GATEWAY;
import static org.eclipse.hono.util.DeviceConnectionConstants.DeviceConnectionAction.SET_LAST_GATEWAY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.tag.Tags;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@link DeviceConnectionClientImpl}.
 *
 */
@ExtendWith(VertxExtension.class)
public class DeviceConnectionClientImplTest {

    private Vertx vertx;
    private ProtonSender sender;
    private DeviceConnectionClientImpl client;
    private Tracer tracer;
    private Span span;
    private HonoConnection connection;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        final SpanContext spanContext = mock(SpanContext.class);

        span = mock(Span.class);
        when(span.context()).thenReturn(spanContext);
        final SpanBuilder spanBuilder = HonoClientUnitTestHelper.mockSpanBuilder(span);

        tracer = mock(Tracer.class);
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);

        vertx = mock(Vertx.class);
        final ProtonReceiver receiver = HonoClientUnitTestHelper.mockProtonReceiver();
        sender = HonoClientUnitTestHelper.mockProtonSender();

        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        connection = HonoClientUnitTestHelper.mockHonoConnection(vertx, config);
        when(connection.getTracer()).thenReturn(tracer);

        client = new DeviceConnectionClientImpl(connection, DEFAULT_TENANT, sender, receiver);
    }

    /**
     * Verifies that the client retrieves the result of the <em>get last known gateway</em> operation from the
     * Device Connection service.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetLastKnownGatewayForDeviceSuccess(final VertxTestContext ctx) {

        final JsonObject getLastGatewayResult = newGetLastGatewayResult("gatewayId");

        // WHEN getting the last known gateway
        client.getLastKnownGatewayForDevice("deviceId", span.context())
                .setHandler(ctx.succeeding(r -> {
                    // THEN the last known gateway has been retrieved from the service and the span is finished
                    verify(span).finish();
                    ctx.completeNow();
                }));

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message response = ProtonHelper.message(getLastGatewayResult.encode());
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);
    }

    /**
     * Verifies that the client handles the response of the <em>set last known gateway</em> operation from the
     * Device Connection service.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSetLastKnownGatewayForDeviceSuccess(final VertxTestContext ctx) {

        // WHEN setting the last known gateway
        client.setLastKnownGatewayForDevice("deviceId", "gatewayId", span.context())
                .setHandler(ctx.succeeding(r -> {
                    // THEN the response for setting the last known gateway has been handled by the service
                    // and the span is finished
                    verify(span).finish();
                    ctx.completeNow();
                }));

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message response = ProtonHelper.message();
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_NO_CONTENT);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);
    }

    /**
     * Verifies that a client invocation of the <em>get last known gateway</em> operation fails
     * if the device connection service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetLastKnownGatewayForDeviceFailsWithSendError(final VertxTestContext ctx) {

        // GIVEN a client with no credit left 
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN getting last known gateway information
        client.getLastKnownGatewayForDevice("deviceId", span.context())
                .setHandler(ctx.failing(t -> {
                    // THEN the invocation fails and the span is marked as erroneous
                    verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                    // and the span is finished
                    verify(span).finish();
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a client invocation of the <em>set last known gateway</em> operation fails
     * if the device connection service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceFailsWithSendError(final VertxTestContext ctx) {

        // GIVEN a client with no credit left 
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN getting last known gateway information
        client.setLastKnownGatewayForDevice("deviceId", "gatewayId", span.context())
                .setHandler(ctx.failing(t -> {
                    // THEN the invocation fails and the span is marked as erroneous
                    verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                    // and the span is finished
                    verify(span).finish();
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a client invocation of the <em>get last known gateway</em> operation fails
     * if the device connection service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetLastKnownGatewayForDeviceFailsWithRejectedRequest(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN getting last known gateway information
        client.getLastKnownGatewayForDevice("deviceId", span.context())
                .setHandler(ctx.failing(t -> {
                    assertEquals(HttpURLConnection.HTTP_BAD_REQUEST,
                            ((ServiceInvocationException) t).getErrorCode());
                    // THEN the invocation fails and the span is marked as erroneous
                    verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                    // and the span is finished
                    verify(span).finish();
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a client invocation of the <em>set last known gateway</em> operation fails
     * if the device connection service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceFailsWithRejectedRequest(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN getting last known gateway information
        client.setLastKnownGatewayForDevice("deviceId", "gatewayId", span.context())
                .setHandler(ctx.failing(t -> {
                    assertEquals(HttpURLConnection.HTTP_BAD_REQUEST,
                            ((ServiceInvocationException) t).getErrorCode());
                    // THEN the invocation fails and the span is marked as erroneous
                    verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                    // and the span is finished
                    verify(span).finish();
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the client includes the required information in the <em>get last known gateway</em> operation
     * request message sent to the device connection service.
     */
    @Test
    public void testGetLastKnownGatewayForDeviceIncludesRequiredInformationInRequest() {

        // WHEN getting last known gateway information
        client.getLastKnownGatewayForDevice("deviceId", span.context());

        // THEN the message being sent contains the device ID in its properties
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), anyHandler());
        final Message sentMessage = messageCaptor.getValue();
        assertThat(MessageHelper.getDeviceId(sentMessage), is("deviceId"));
        assertThat(sentMessage.getMessageId(), is(notNullValue()));
        assertThat(sentMessage.getSubject(), is(GET_LAST_GATEWAY.getSubject()));
        assertNull(MessageHelper.getJsonPayload(sentMessage));
    }

    /**
     * Verifies that the client includes the required information in the <em>set last known gateway</em> operation
     * request message sent to the device connection service.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceIncludesRequiredInformationInRequest() {

        // WHEN getting last known gateway information
        client.setLastKnownGatewayForDevice("deviceId", "gatewayId", span.context());

        // THEN the message being sent contains the device ID in its properties
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), anyHandler());
        final Message sentMessage = messageCaptor.getValue();
        assertThat(MessageHelper.getDeviceId(sentMessage), is("deviceId"));
        assertThat(sentMessage.getMessageId(), is(notNullValue()));
        assertThat(sentMessage.getSubject(), is(SET_LAST_GATEWAY.getSubject()));
        assertNull(MessageHelper.getJsonPayload(sentMessage));
    }

    private JsonObject newGetLastGatewayResult(final String gatewayId) {
        return new JsonObject().
                put(DeviceConnectionConstants.FIELD_GATEWAY_ID, gatewayId);
    }
}
