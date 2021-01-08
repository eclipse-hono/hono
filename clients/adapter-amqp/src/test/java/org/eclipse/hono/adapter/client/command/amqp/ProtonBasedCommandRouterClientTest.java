/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.adapter.client.command.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandRouterConstants.CommandRouterAction;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
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
 * Tests verifying behavior of {@link ProtonBasedTenantCommandRouterClient}.
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
        connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx, config, tracer);
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        when(connection.createReceiver(anyString(), any(ProtonQoS.class), any(ProtonMessageHandler.class), VertxMockSupport.anyHandler()))
                .thenReturn(Future.succeededFuture(receiver));
        when(connection.createSender(anyString(), any(ProtonQoS.class), VertxMockSupport.anyHandler()))
            .thenReturn(Future.succeededFuture(sender));
        client = new ProtonBasedCommandRouterClient(connection, SendMessageSampler.Factory.noop(), new ProtocolAdapterProperties());
    }

    /**
     * Verifies that the client handles the response of the <em>set-last-known-gateway</em> operation from the
     * Command Router service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceSuccess(final VertxTestContext ctx) {

        // WHEN setting the last known gateway
        client.setLastKnownGatewayForDevice("tenant", "deviceId", "gatewayId", span.context())
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
        client.registerCommandConsumer("tenant", "deviceId", "adapterInstanceId", null, span.context())
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
    public void testUnregisterCommandConsumer(final VertxTestContext ctx) {

        // WHEN unregistering the command consumer
        client.unregisterCommandConsumer("tenant", "deviceId", "adapterInstanceId", span.context())
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
     * Verifies that a client invocation of the <em>set-last-known-gateway</em> operation fails
     * if the command router service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceFailsWithSendError(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN setting last known gateway information
        client.setLastKnownGatewayForDevice("tenant", "deviceId", "gatewayId", span.context())
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
        client.registerCommandConsumer("tenant", "deviceId", "adapterInstanceId", null, span.context())
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
        client.unregisterCommandConsumer("tenant", "deviceId", "adapterInstanceId", span.context())
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
        client.unregisterCommandConsumer("tenant", "deviceId", "gatewayId", span.context())
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
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_PRECON_FAILED);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(sentMessage.getMessageId());
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(mock(ProtonDelivery.class), response);
    }

    /**
     * Verifies that a client invocation of the <em>set-last-known-gateway</em> operation fails
     * if the command router service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceFailsWithRejectedRequest(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN setting last known gateway information
        client.setLastKnownGatewayForDevice("tenant", "deviceId", "gatewayId", span.context())
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

        // GIVEN a client with no credit left
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN registering the command consumer
        client.registerCommandConsumer("tenant", "deviceId", "adapterInstanceId", null, span.context())
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

        // GIVEN a client with no credit left
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN unregistering the command consumer
        client.unregisterCommandConsumer("tenant", "deviceId", "adapterInstanceId", span.context())
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
    public void testSetLastKnownGatewayForDeviceIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";
        final String gatewayId = "gatewayId";

        // WHEN setting last known gateway information
        client.setLastKnownGatewayForDevice("tenant", deviceId, gatewayId, span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_GATEWAY_ID, String.class))
                .isEqualTo(gatewayId);
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(CommandRouterAction.SET_LAST_KNOWN_GATEWAY.getSubject());
        assertThat(MessageHelper.getJsonPayload(sentMessage)).isNull();
    }

    /**
     * Verifies that the client includes the required information in the <em>register-command-consumer</em> operation
     * request message sent to the command router service.
     */
    @Test
    public void testRegisterCommandConsumerIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";

        // WHEN registering the command consumer
        client.registerCommandConsumer("tenant", deviceId, "adapterInstanceId", null, span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, String.class))
                .isEqualTo("adapterInstanceId");
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_LIFESPAN, Integer.class))
                .isEqualTo(Integer.valueOf(-1));
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(CommandRouterAction.REGISTER_COMMAND_CONSUMER.getSubject());
        assertThat(MessageHelper.getJsonPayload(sentMessage)).isNull();
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
        client.registerCommandConsumer("tenant", deviceId, "adapterInstanceId",
                Duration.ofSeconds(lifespanSeconds), span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, String.class))
                .isEqualTo("adapterInstanceId");
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_LIFESPAN, Integer.class))
                .isEqualTo(lifespanSeconds);
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(CommandRouterAction.REGISTER_COMMAND_CONSUMER.getSubject());
        assertThat(MessageHelper.getJsonPayload(sentMessage)).isNull();
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
        client.unregisterCommandConsumer("tenant", deviceId, adapterInstanceId, span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, String.class))
                .isEqualTo(adapterInstanceId);
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(CommandRouterAction.UNREGISTER_COMMAND_CONSUMER.getSubject());
        assertThat(MessageHelper.getJsonPayload(sentMessage)).isNull();
    }

    private Message createNoContentResponseMessage(final Object correlationId) {
        final Message response = ProtonHelper.message();
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_NO_CONTENT);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(correlationId);
        return response;
    }
}
