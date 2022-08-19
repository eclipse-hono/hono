/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.config.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.CommandRouterConstants;
import org.eclipse.hono.util.CommandRouterConstants.CommandRouterAction;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link ProtonBasedCommandRouterClient}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class ProtonBasedCommandRouterClientTest {

    private HonoConnection connection;
    private ProtonSender sender;
    private ProtonReceiver receiver;
    private ProtonBasedCommandRouterClient client;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);

        final EventBus eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        receiver = AmqpClientUnitTestHelper.mockProtonReceiver();
        sender = AmqpClientUnitTestHelper.mockProtonSender();

        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        config.setRequestTimeout(0); // don't let requestTimeout timer be started
        connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx, config, tracer);
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        when(connection.createReceiver(anyString(), any(ProtonQoS.class), any(ProtonMessageHandler.class), VertxMockSupport.anyHandler()))
                .thenReturn(Future.succeededFuture(receiver));
        when(connection.createSender(anyString(), any(ProtonQoS.class), VertxMockSupport.anyHandler()))
            .thenReturn(Future.succeededFuture(sender));
        client = new ProtonBasedCommandRouterClient(connection, SendMessageSampler.Factory.noop());
    }

    /**
     * Verifies that the client handles the response of the <em>set-last-known-gateway</em> operation from the
     * Command Router service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewaysSuccess(final VertxTestContext ctx) {

        // WHEN setting the last known gateway
        client.setLastKnownGateways("tenant", Map.of("deviceId", "gatewayId"), span.context())
                .onComplete(ctx.succeeding(r -> {
                    ctx.verify(() -> {
                        // THEN the response has been handled and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));

        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        final Message response = createNoContentResponseMessage(sentMessage.getMessageId());
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(mock(ProtonDelivery.class), response);
    }

    /**
     * Verifies that the client handles the response of the <em>register-command-consumer</em> operation from the
     * Command Router service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRegisterCommandConsumer(final VertxTestContext ctx) {

        // WHEN registering the command consumer
        client.registerCommandConsumer("tenant", "deviceId", false, "adapterInstanceId", null, span.context())
                .onComplete(ctx.succeeding(r -> {
                    ctx.verify(() -> {
                        // THEN the response has been handled and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));

        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        final Message response = createNoContentResponseMessage(sentMessage.getMessageId());
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(mock(ProtonDelivery.class), response);
    }

    /**
     * Verifies that the client handles the response of the <em>unregister-command-consumer</em> operation from the
     * Command Router service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUnregisterCommandConsumers(final VertxTestContext ctx) {

        // WHEN unregistering the command consumer
        client.unregisterCommandConsumer("tenant", "deviceId", false, "adapterInstanceId", span.context())
                .onComplete(ctx.succeeding(r -> {
                    ctx.verify(() -> {
                        // THEN the response has been handled and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));

        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        final Message response = createNoContentResponseMessage(sentMessage.getMessageId());
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(mock(ProtonDelivery.class), response);
    }

    /**
     * Verifies that multiple client invocations of the <em>set-last-known-gateway</em> operation result in batch
     * requests to the command router service.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceDoesBatchRequests() {

        final String tenantId1 = "tenantId1";
        final String tenantId2 = "tenantId2";
        final String tenantId3 = "tenantId3";
        final String deviceId1 = "deviceId1";
        final String deviceId2 = "deviceId2";
        final String gatewayId = "gatewayId";

        final AtomicReference<Handler<Long>> timerHandlerRef = new AtomicReference<>();
        when(connection.getVertx().setTimer(
                eq(ProtonBasedCommandRouterClient.SET_LAST_KNOWN_GATEWAY_UPDATE_INTERVAL_MILLIS),
                VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<Long> handler = invocation.getArgument(1);
            timerHandlerRef.set(handler);
            return 1L;
        });

        // WHEN setting last known gateway information for multiple devices (before the update timer interval has elapsed)
        client.setLastKnownGatewayForDevice(tenantId3, deviceId1, gatewayId, span.context());
        client.setLastKnownGatewayForDevice(tenantId1, deviceId1, gatewayId, span.context());
        client.setLastKnownGatewayForDevice(tenantId1, deviceId2, gatewayId, span.context());
        client.setLastKnownGatewayForDevice(tenantId2, deviceId1, gatewayId, span.context());
        client.setLastKnownGatewayForDevice(tenantId2, deviceId2, gatewayId, span.context());

        assertThat(timerHandlerRef.get()).isNotNull();
        timerHandlerRef.get().handle(1L);

        // THEN there are requests being sent for each of the tenants in the order of the setLastKnownGatewayForDevice invocations
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender, times(3)).send(messageCaptor.capture(), VertxMockSupport.anyHandler());
        final List<Message> sentMessages = messageCaptor.getAllValues();

        assertThat(sentMessages.size()).isEqualTo(3);

        // first message for tenant3 - as single device request
        assertThat(sentMessages.get(0).getAddress())
                .isEqualTo(String.format("%s/%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, tenantId3));
        assertThat(AmqpUtils.getDeviceId(sentMessages.get(0))).isEqualTo(deviceId1);

        assertThat(sentMessages.get(1).getAddress())
                .isEqualTo(String.format("%s/%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, tenantId1));
        final JsonObject jsonPayloadSecondMsg = AmqpUtils.getJsonPayload(sentMessages.get(1));
        assertThat(jsonPayloadSecondMsg).isNotNull();
        assertThat(jsonPayloadSecondMsg.getString(deviceId1)).isEqualTo(gatewayId);
        assertThat(jsonPayloadSecondMsg.getString(deviceId2)).isEqualTo(gatewayId);

        assertThat(sentMessages.get(2).getAddress())
                .isEqualTo(String.format("%s/%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, tenantId2));
        final JsonObject jsonPayloadThirdMsg = AmqpUtils.getJsonPayload(sentMessages.get(2));
        assertThat(jsonPayloadThirdMsg).isNotNull();
        assertThat(jsonPayloadThirdMsg.getString(deviceId1)).isEqualTo(gatewayId);
        assertThat(jsonPayloadThirdMsg.getString(deviceId2)).isEqualTo(gatewayId);
    }

    /**
     * Verifies that multiple client invocations of the <em>set-last-known-gateway</em> operation result
     * in batch requests to the command router service, with the requests having a limited size.
     */
    @Test
    public void testSetLastKnownGatewayRequestsDontExceedSizeLimit() {

        final String tenantId = "tenantId";
        final String gatewayId = "gatewayId";
        final int numDevices = ProtonBasedCommandRouterClient.SET_LAST_KNOWN_GATEWAY_UPDATE_MAX_ENTRIES + 1;

        final AtomicReference<Handler<Long>> timerHandlerRef = new AtomicReference<>();
        when(connection.getVertx().setTimer(
                eq(ProtonBasedCommandRouterClient.SET_LAST_KNOWN_GATEWAY_UPDATE_INTERVAL_MILLIS),
                VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<Long> handler = invocation.getArgument(1);
            timerHandlerRef.set(handler);
            return 1L;
        });

        // WHEN setting last known gateway information for multiple devices (before the update timer interval has elapsed)
        IntStream.range(0, numDevices).forEach(idx -> {
            client.setLastKnownGatewayForDevice(tenantId, "device_" + idx, gatewayId, span.context());
        });

        assertThat(timerHandlerRef.get()).isNotNull();
        timerHandlerRef.get().handle(1L);

        // THEN there is first one message being sent with the max number of entries
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);

        assertThat(sentMessage.getAddress())
                .isEqualTo(String.format("%s/%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, tenantId));
        final JsonObject jsonPayloadFirstMsg = AmqpUtils.getJsonPayload(sentMessage);
        assertThat(jsonPayloadFirstMsg).isNotNull();
        assertThat(jsonPayloadFirstMsg.size()).isEqualTo(ProtonBasedCommandRouterClient.SET_LAST_KNOWN_GATEWAY_UPDATE_MAX_ENTRIES);
        IntStream.range(0, ProtonBasedCommandRouterClient.SET_LAST_KNOWN_GATEWAY_UPDATE_MAX_ENTRIES).forEach(idx -> {
            assertThat(jsonPayloadFirstMsg.getString("device_" + idx)).isEqualTo(gatewayId);
        });

        // trigger completion of request
        final Message response = createNoContentResponseMessage(sentMessage.getMessageId());
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(mock(ProtonDelivery.class), response);

        // AND THEN there is a 2nd message being sent with the remaining entries (just one)
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender, times(2)).send(messageCaptor.capture(), VertxMockSupport.anyHandler());
        final Message sentMessage2 = messageCaptor.getAllValues().get(1);

        assertThat(sentMessage2.getAddress())
                .isEqualTo(String.format("%s/%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, tenantId));
        assertThat(AmqpUtils.getDeviceId(sentMessage2)).isEqualTo("device_100");
    }

    /**
     * Verifies that a new <em>set-last-known-gateway</em> request is triggered right away if
     * the preceding one took longer than the interval in which these kinds of requests should be sent.
     */
    @Test
    public void testSetLastKnownGatewaysIsCalledRightAwayAfterPreviousRequestTookLongerThanInternal() {

        final String tenantId = "tenantId";
        final String gatewayId = "gatewayId";

        final List<Handler<Long>> timerHandlers = new LinkedList<>();
        when(connection.getVertx().setTimer(
                eq(ProtonBasedCommandRouterClient.SET_LAST_KNOWN_GATEWAY_UPDATE_INTERVAL_MILLIS),
                VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<Long> handler = invocation.getArgument(1);
            timerHandlers.add(handler);
            return 1L;
        });

        // WHEN setting last known gateway information
        client.setLastKnownGatewayForDevice(tenantId, "deviceId", gatewayId, span.context());

        assertThat(timerHandlers.size()).isEqualTo(1);
        timerHandlers.get(0).handle(1L);

        // THEN there is first one message being sent
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);

        assertThat(sentMessage.getAddress())
                .isEqualTo(String.format("%s/%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, tenantId));
        assertThat(AmqpUtils.getDeviceId(sentMessage)).isEqualTo("deviceId");

        // AND WHEN a gateway gets set for another device while the first request hasn't finished
        client.setLastKnownGatewayForDevice(tenantId, "deviceId2", gatewayId, span.context());
        // AND the request takes longer than the update interval
        client.setClock(Clock.offset(Clock.systemUTC(),
                Duration.ofMillis(ProtonBasedCommandRouterClient.SET_LAST_KNOWN_GATEWAY_UPDATE_INTERVAL_MILLIS)));

        // THEN upon completion of the request...
        final Message response = createNoContentResponseMessage(sentMessage.getMessageId());
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(mock(ProtonDelivery.class), response);

        // ... a new request gets sent right away for the other device
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender, times(2)).send(messageCaptor.capture(), VertxMockSupport.anyHandler());
        final Message sentMessage2 = messageCaptor.getAllValues().get(1);

        assertThat(sentMessage2.getAddress())
                .isEqualTo(String.format("%s/%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, tenantId));
        assertThat(AmqpUtils.getDeviceId(sentMessage2)).isEqualTo("deviceId2");
    }

    /**
     * Verifies that a new <em>set-last-known-gateway</em> request is scheduled after the defined update interval
     * has elapsed, taking the processing time of the previous request into account.
     */
    @Test
    public void testSetLastKnownGatewayRequestsAreTriggeredAfterDefinedIntervalElapsed() {

        final String tenantId = "tenantId";
        final String gatewayId = "gatewayId";

        final List<Handler<Long>> timerHandlers = new LinkedList<>();
        when(connection.getVertx().setTimer(
                longThat(delay -> 0 < delay
                        && delay <= ProtonBasedCommandRouterClient.SET_LAST_KNOWN_GATEWAY_UPDATE_INTERVAL_MILLIS),
                VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<Long> handler = invocation.getArgument(1);
            timerHandlers.add(handler);
            return 1L;
        });

        // WHEN setting last known gateway information
        client.setLastKnownGatewayForDevice(tenantId, "deviceId", gatewayId, span.context());

        assertThat(timerHandlers.size()).isEqualTo(1);
        timerHandlers.get(0).handle(1L);

        // THEN there is first one message being sent
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);

        assertThat(sentMessage.getAddress())
                .isEqualTo(String.format("%s/%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, tenantId));
        assertThat(AmqpUtils.getDeviceId(sentMessage)).isEqualTo("deviceId");

        // AND WHEN a gateway gets set for another device while the first request hasn't finished
        client.setLastKnownGatewayForDevice(tenantId, "deviceId2", gatewayId, span.context());
        final Duration firstRequestProcessingDuration = Duration.ofMillis(10);
        client.setClock(Clock.offset(Clock.systemUTC(), firstRequestProcessingDuration));

        // THEN upon completion of the request...
        final Message response = createNoContentResponseMessage(sentMessage.getMessageId());
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(mock(ProtonDelivery.class), response);

        // ... sending a new request for the other device gets scheduled
        final ArgumentCaptor<Long> timerDelayCaptor = ArgumentCaptor.forClass(Long.class);
        verify(connection.getVertx(), times(2)).setTimer(timerDelayCaptor.capture(), VertxMockSupport.anyHandler());
        assertThat(timerDelayCaptor.getValue()).isGreaterThan(firstRequestProcessingDuration.toMillis());

        // AND WHEN the timer completes
        assertThat(timerHandlers.size()).isEqualTo(2);
        timerHandlers.get(1).handle(1L);

        // THEN the second request gets sent
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender, times(2)).send(messageCaptor.capture(), VertxMockSupport.anyHandler());
        final Message sentMessage2 = messageCaptor.getAllValues().get(1);

        assertThat(sentMessage2.getAddress())
                .isEqualTo(String.format("%s/%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, tenantId));
        assertThat(AmqpUtils.getDeviceId(sentMessage2)).isEqualTo("deviceId2");
    }

    /**
     * Verifies that a client invocation of the <em>register-command-consumer</em> operation fails
     * if the command router service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRegisterCommandConsumerFailsWithSendError(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN registering the command consumer
        client.registerCommandConsumer("tenant", "deviceId", false, "adapterInstanceId", null, span.context())
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the invocation fails and the span is marked as erroneous
                        verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                        // and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a client invocation of the <em>unregister-command-consumer</em> operation fails
     * if the command router service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUnregisterCommandConsumerFailsWithSendError(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN unregistering the command consumer
        client.unregisterCommandConsumer("tenant", "deviceId", false, "adapterInstanceId", span.context())
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the invocation fails and the span is marked as erroneous
                        verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                        // and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a client invocation of the <em>unregister-command-consumer</em> operation
     * fails if a <em>PRECON_FAILED</em> response was returned from the command router service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUnregisterCommandConsumerForNotFoundEntry(final VertxTestContext ctx) {

        // WHEN unregistering the command consumer
        client.unregisterCommandConsumer("tenant", "deviceId", false, "gatewayId", span.context())
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the response has been handled and the span is finished
                        assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(HttpURLConnection.HTTP_PRECON_FAILED);
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));

        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        final Message response = ProtonHelper.message();
        AmqpUtils.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_PRECON_FAILED);
        AmqpUtils.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(sentMessage.getMessageId());
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(mock(ProtonDelivery.class), response);
    }

    /**
     * Verifies that a client invocation of the <em>set-last-known-gateway</em> operation fails if the command router
     * service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewaysFailsWithRejectedRequest(final VertxTestContext ctx) {

        // GIVEN a remote peer returning a Rejected disposition
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN setting last known gateway information
        client.setLastKnownGateways("tenant", Map.of("deviceId", "gatewayId"), span.context())
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                        // THEN the invocation fails and the span is marked as erroneous
                        verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                        // and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a client invocation of the <em>register-command-consumer</em> operation fails
     * if the command router service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRegisterCommandConsumerFailsWithRejectedRequest(final VertxTestContext ctx) {

        // GIVEN a remote peer returning a Rejected disposition
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN registering the command consumer
        client.registerCommandConsumer("tenant", "deviceId", false, "adapterInstanceId", null, span.context())
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                        // THEN the invocation fails and the span is marked as erroneous
                        verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                        // and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a client invocation of the <em>unregister-command-consumer</em> operation fails
     * if the command router service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUnregisterCommandConsumerFailsWithRejectedRequest(final VertxTestContext ctx) {

        // GIVEN a remote peer returning a Rejected disposition
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN unregistering the command consumer
        client.unregisterCommandConsumer("tenant", "deviceId", false, "adapterInstanceId", span.context())
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                        // THEN the invocation fails and the span is marked as erroneous
                        verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                        // and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the client includes the required information in the <em>set-last-known-gateway</em> operation
     * request message sent to the command router service.
     */
    @Test
    public void testSetLastKnownGatewaysForSingleDeviceIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";
        final String gatewayId = "gatewayId";

        // WHEN setting last known gateway information
        client.setLastKnownGateways("tenant", Map.of(deviceId, gatewayId), span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(AmqpUtils.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(AmqpUtils.getApplicationProperty(sentMessage, MessageHelper.APP_PROPERTY_GATEWAY_ID, String.class))
                .isEqualTo(gatewayId);
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(CommandRouterAction.SET_LAST_KNOWN_GATEWAY.getSubject());
        assertThat(AmqpUtils.getJsonPayload(sentMessage)).isNull();
    }

    /**
     * Verifies that the client includes the required information in the <em>set-last-known-gateway</em> batch operation
     * request message sent to the command router service.
     */
    @Test
    public void testSetLastKnownGatewaysForMultipleDevicesIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";
        final String deviceId2 = "deviceId2";
        final String gatewayId = "gatewayId";

        // WHEN setting last known gateway information for multiple devices
        client.setLastKnownGateways("tenant", Map.of(deviceId, gatewayId, deviceId2, gatewayId), span.context());

        // THEN the message being sent contains the device IDs in its payload
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(AmqpUtils.getDeviceId(sentMessage)).isNull();
        assertThat(AmqpUtils.getApplicationProperty(sentMessage, MessageHelper.APP_PROPERTY_GATEWAY_ID, String.class))
                .isNull();
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(CommandRouterAction.SET_LAST_KNOWN_GATEWAY.getSubject());
        final JsonObject jsonPayload = AmqpUtils.getJsonPayload(sentMessage);
        assertThat(jsonPayload).isNotNull();
        assertThat(jsonPayload.getString(deviceId)).isEqualTo(gatewayId);
        assertThat(jsonPayload.getString(deviceId2)).isEqualTo(gatewayId);
    }

    /**
     * Verifies that the client includes the required information in the <em>register-command-consumer</em> operation
     * request message sent to the command router service.
     */
    @Test
    public void testRegisterCommandConsumerIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";

        // WHEN registering the command consumer
        client.registerCommandConsumer("tenant", deviceId, false, "adapterInstanceId", null, span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(AmqpUtils.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(AmqpUtils.getApplicationProperty(
                sentMessage,
                CommandConstants.MSG_PROPERTY_ADAPTER_INSTANCE_ID,
                String.class))
            .isEqualTo("adapterInstanceId");
        assertThat(AmqpUtils.getApplicationProperty(
                sentMessage,
                MessageHelper.APP_PROPERTY_LIFESPAN,
                Integer.class))
            .isEqualTo(Integer.valueOf(-1));
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(CommandRouterAction.REGISTER_COMMAND_CONSUMER.getSubject());
        assertThat(AmqpUtils.getJsonPayload(sentMessage)).isNull();
    }

    /**
     * Verifies that the client includes the required information in the <em>register-command-consumer</em> operation
     * request message sent to the command router service, including the lifespan parameter.
     */
    @Test
    public void testRegisterCommandConsumerWithLifespanIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";
        final int lifespanSeconds = 20;

        // WHEN registering the command consumer
        client.registerCommandConsumer("tenant", deviceId, false, "adapterInstanceId",
                Duration.ofSeconds(lifespanSeconds), span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(AmqpUtils.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(AmqpUtils.getApplicationProperty(
                sentMessage,
                CommandConstants.MSG_PROPERTY_ADAPTER_INSTANCE_ID,
                String.class))
            .isEqualTo("adapterInstanceId");
        assertThat(AmqpUtils.getApplicationProperty(
                sentMessage,
                MessageHelper.APP_PROPERTY_LIFESPAN,
                Integer.class))
            .isEqualTo(lifespanSeconds);
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(CommandRouterAction.REGISTER_COMMAND_CONSUMER.getSubject());
        assertThat(AmqpUtils.getJsonPayload(sentMessage)).isNull();
    }

    /**
     * Verifies that the client includes the required information in the <em>unregister-command-consumer</em> operation
     * request message sent to the command router service.
     */
    @Test
    public void testUnregisterCommandConsumerIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";
        final String adapterInstanceId = "adapterInstanceId";

        // WHEN unregistering the command consumer
        client.unregisterCommandConsumer("tenant", deviceId, false, adapterInstanceId, span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(AmqpUtils.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(AmqpUtils.getApplicationProperty(
                sentMessage,
                CommandConstants.MSG_PROPERTY_ADAPTER_INSTANCE_ID,
                String.class))
            .isEqualTo(adapterInstanceId);
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(CommandRouterAction.UNREGISTER_COMMAND_CONSUMER.getSubject());
        assertThat(AmqpUtils.getJsonPayload(sentMessage)).isNull();
    }

    private Message createNoContentResponseMessage(final Object correlationId) {
        final Message response = ProtonHelper.message();
        AmqpUtils.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_NO_CONTENT);
        AmqpUtils.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(correlationId);
        return response;
    }
}
