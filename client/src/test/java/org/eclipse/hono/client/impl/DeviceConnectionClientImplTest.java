/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Collections;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
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

    private ProtonSender sender;
    private DeviceConnectionClientImpl client;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);

        final Vertx vertx = mock(Vertx.class);
        final ProtonReceiver receiver = HonoClientUnitTestHelper.mockProtonReceiver();
        sender = HonoClientUnitTestHelper.mockProtonSender();

        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        final HonoConnection connection = HonoClientUnitTestHelper.mockHonoConnection(vertx, config, tracer);

        client = new DeviceConnectionClientImpl(connection, Constants.DEFAULT_TENANT, sender, receiver, SendMessageSampler.noop());
    }

    /**
     * Verifies that the client retrieves the result of the <em>get-last-known-gateway</em> operation from the
     * Device Connection service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetLastKnownGatewayForDeviceSuccess(final VertxTestContext ctx) {

        final String gatewayId = "gatewayId";
        final JsonObject getLastGatewayResult = new JsonObject().
                put(DeviceConnectionConstants.FIELD_GATEWAY_ID, gatewayId);

        // WHEN getting the last known gateway
        client.getLastKnownGatewayForDevice("deviceId", span.context())
                .onComplete(ctx.succeeding(resultJson -> {
                    ctx.verify(() -> {
                        // THEN the last known gateway has been retrieved from the service and the span is finished
                        assertThat(resultJson).isNotNull();
                        assertThat(resultJson.getString(DeviceConnectionConstants.FIELD_GATEWAY_ID)).isEqualTo(gatewayId);
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));

        final Message sentMessage = verifySenderSend();
        final Message response = ProtonHelper.message();
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(sentMessage.getMessageId());
        MessageHelper.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, getLastGatewayResult.toBuffer());
        client.handleResponse(mock(ProtonDelivery.class), response);
    }

    /**
     * Verifies that the client handles the response of the <em>set-last-known-gateway</em> operation from the
     * Device Connection service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceSuccess(final VertxTestContext ctx) {

        // WHEN setting the last known gateway
        client.setLastKnownGatewayForDevice("deviceId", "gatewayId", span.context())
                .onComplete(ctx.succeeding(r -> {
                    ctx.verify(() -> {
                        // THEN the response has been handled and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));

        final Message sentMessage = verifySenderSend();
        final Message response = createNoContentResponseMessage(sentMessage.getMessageId());
        client.handleResponse(mock(ProtonDelivery.class), response);
    }

    /**
     * Verifies that the client handles the response of the <em>set-cmd-handling-adapter-instance</em> operation from the
     * Device Connection service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetCommandHandlingAdapterInstance(final VertxTestContext ctx) {

        // WHEN setting the command handling adapter instance
        client.setCommandHandlingAdapterInstance("deviceId", "adapterInstanceId", null, span.context())
                .onComplete(ctx.succeeding(r -> {
                    ctx.verify(() -> {
                        // THEN the response has been handled and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));

        final Message sentMessage = verifySenderSend();
        final Message response = createNoContentResponseMessage(sentMessage.getMessageId());
        client.handleResponse(mock(ProtonDelivery.class), response);
    }

    /**
     * Verifies that the client handles the response of the <em>remove-cmd-handling-adapter-instance</em> operation from the
     * Device Connection service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstance(final VertxTestContext ctx) {

        // WHEN removing the command handling adapter instance
        client.removeCommandHandlingAdapterInstance("deviceId", "adapterInstanceId", span.context())
                .onComplete(ctx.succeeding(r -> {
                    ctx.verify(() -> {
                        // THEN the response has been handled and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));

        final Message sentMessage = verifySenderSend();
        final Message response = createNoContentResponseMessage(sentMessage.getMessageId());
        client.handleResponse(mock(ProtonDelivery.class), response);
    }

    /**
     * Verifies that the client handles the response of the <em>get-cmd-handling-adapter-instances</em> operation from the
     * Device Connection service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstances(final VertxTestContext ctx) {

        final String adapterInstanceId = "adapterInstanceId";
        final String deviceId = "4711";

        final JsonArray adapterInstancesArray = new JsonArray();
        adapterInstancesArray
                .add(new JsonObject().put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, adapterInstanceId)
                        .put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId));
        final JsonObject adapterInstancesResult = new JsonObject().
                put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES, adapterInstancesArray);

        // WHEN getting the command handling adapter instances
        client.getCommandHandlingAdapterInstances(deviceId, Collections.emptyList(), span.context())
                .onComplete(ctx.succeeding(resultJson -> {
                    ctx.verify(() -> {
                        // THEN the response has been handled and the span is finished
                        assertThat(resultJson).isEqualTo(adapterInstancesResult);
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));

        final Message sentMessage = verifySenderSend();
        final Message response = ProtonHelper.message();
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(sentMessage.getMessageId());
        MessageHelper.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, adapterInstancesResult.toBuffer());
        client.handleResponse(mock(ProtonDelivery.class), response);
    }

    /**
     * Verifies that a client invocation of the <em>get-last-known-gateway</em> operation fails
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
     * Verifies that a client invocation of the <em>set-last-known-gateway</em> operation fails
     * if the device connection service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceFailsWithSendError(final VertxTestContext ctx) {

        // GIVEN a client with no credit left 
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN setting last known gateway information
        client.setLastKnownGatewayForDevice("deviceId", "gatewayId", span.context())
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
     * Verifies that a client invocation of the <em>set-cmd-handling-adapter-instance</em> operation fails
     * if the device connection service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetCommandHandlingAdapterInstanceFailsWithSendError(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN setting the command handling adapter instance
        client.setCommandHandlingAdapterInstance("deviceId", "adapterInstanceId", null, span.context())
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
     * Verifies that a client invocation of the <em>remove-cmd-handling-adapter-instance</em> operation fails
     * if the device connection service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstanceFailsWithSendError(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN removing the command handling adapter instance
        client.removeCommandHandlingAdapterInstance("deviceId", "adapterInstanceId", span.context())
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
     * Verifies that a client invocation of the <em>remove-cmd-handling-adapter-instance</em> operation
     * fails if a <em>PRECON_FAILED</em> response was returned from the device connection service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstanceForNotFoundEntry(final VertxTestContext ctx) {

        // WHEN removing the command handling adapter instance
        client.removeCommandHandlingAdapterInstance("deviceId", "gatewayId", span.context())
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the response has been handled and the span is finished
                        assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(HttpURLConnection.HTTP_PRECON_FAILED);
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));

        final Message sentMessage = verifySenderSend();
        final Message response = ProtonHelper.message();
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_PRECON_FAILED);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(sentMessage.getMessageId());
        client.handleResponse(mock(ProtonDelivery.class), response);
    }

    /**
     * Verifies that a client invocation of the <em>get-cmd-handling-adapter-instances</em> operation fails
     * if the device connection service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesFailsWithSendError(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN getting the command handling adapter instances
        client.getCommandHandlingAdapterInstances("deviceId", Collections.emptyList(), span.context())
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
     * Verifies that a client invocation of the <em>get-last-known-gateway</em> operation fails
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
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN getting last known gateway information
        client.getLastKnownGatewayForDevice("deviceId", span.context())
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
     * Verifies that a client invocation of the <em>set-last-known-gateway</em> operation fails
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
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN setting last known gateway information
        client.setLastKnownGatewayForDevice("deviceId", "gatewayId", span.context())
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
     * Verifies that a client invocation of the <em>set-cmd-handling-adapter-instance</em> operation fails
     * if the device connection service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetCommandHandlingAdapterInstanceFailsWithRejectedRequest(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN setting the command handling adapter instance
        client.setCommandHandlingAdapterInstance("deviceId", "adapterInstanceId", null, span.context())
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
     * Verifies that a client invocation of the <em>remove-cmd-handling-adapter-instance</em> operation fails
     * if the device connection service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstanceFailsWithRejectedRequest(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN removing the command handling adapter instance
        client.removeCommandHandlingAdapterInstance("deviceId", "adapterInstanceId", span.context())
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
     * Verifies that a client invocation of the <em>get-cmd-handling-adapter-instances</em> operation fails
     * if the device connection service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesFailsWithRejectedRequest(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN getting the command handling adapter instances
        client.getCommandHandlingAdapterInstances("deviceId", Collections.emptyList(), span.context())
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
     * Verifies that the client includes the required information in the <em>get-last-known-gateway</em> operation
     * request message sent to the device connection service.
     */
    @Test
    public void testGetLastKnownGatewayForDeviceIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";

        // WHEN getting last known gateway information
        client.getLastKnownGatewayForDevice(deviceId, span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = verifySenderSend();
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(DeviceConnectionConstants.DeviceConnectionAction.GET_LAST_GATEWAY.getSubject());
        assertThat(MessageHelper.getJsonPayload(sentMessage)).isNull();
    }

    /**
     * Verifies that the client includes the required information in the <em>set-last-known-gateway</em> operation
     * request message sent to the device connection service.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";
        final String gatewayId = "gatewayId";

        // WHEN setting last known gateway information
        client.setLastKnownGatewayForDevice(deviceId, gatewayId, span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = verifySenderSend();
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_GATEWAY_ID, String.class))
                        .isEqualTo(gatewayId);
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(DeviceConnectionConstants.DeviceConnectionAction.SET_LAST_GATEWAY.getSubject());
        assertThat(MessageHelper.getJsonPayload(sentMessage)).isNull();
    }

    /**
     * Verifies that the client includes the required information in the <em>set-cmd-handling-adapter-instance</em> operation
     * request message sent to the device connection service.
     */
    @Test
    public void testSetCommandHandlingAdapterInstanceIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";

        // WHEN setting the command handling adapter instance
        client.setCommandHandlingAdapterInstance(deviceId, "adapterInstanceId", null, span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = verifySenderSend();
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, String.class))
                        .isEqualTo("adapterInstanceId");
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_LIFESPAN, Integer.class))
                        .isEqualTo(Integer.valueOf(-1));
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(DeviceConnectionConstants.DeviceConnectionAction.SET_CMD_HANDLING_ADAPTER_INSTANCE.getSubject());
        assertThat(MessageHelper.getJsonPayload(sentMessage)).isNull();
    }

    /**
     * Verifies that the client includes the required information in the <em>set-cmd-handling-adapter-instance</em> operation
     * request message sent to the device connection service, including the lifespan parameter.
     */
    @Test
    public void testSetCommandHandlingAdapterInstanceWithLifespanIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";
        final int lifespanSeconds = 20;

        // WHEN setting the command handling adapter instance
        client.setCommandHandlingAdapterInstance(deviceId, "adapterInstanceId",
                Duration.ofSeconds(lifespanSeconds), span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = verifySenderSend();
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, String.class))
                .isEqualTo("adapterInstanceId");
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_LIFESPAN, Integer.class))
                .isEqualTo(lifespanSeconds);
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(DeviceConnectionConstants.DeviceConnectionAction.SET_CMD_HANDLING_ADAPTER_INSTANCE.getSubject());
        assertThat(MessageHelper.getJsonPayload(sentMessage)).isNull();
    }

    /**
     * Verifies that the client includes the required information in the <em>remove-cmd-handling-adapter-instance</em> operation
     * request message sent to the device connection service.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstanceIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";
        final String adapterInstanceId = "adapterInstanceId";

        // WHEN removing the command handling adapter instance
        client.removeCommandHandlingAdapterInstance(deviceId, adapterInstanceId, span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = verifySenderSend();
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(MessageHelper.getApplicationProperty(sentMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, String.class))
                        .isEqualTo(adapterInstanceId);
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(DeviceConnectionConstants.DeviceConnectionAction.REMOVE_CMD_HANDLING_ADAPTER_INSTANCE.getSubject());
        assertThat(MessageHelper.getJsonPayload(sentMessage)).isNull();
    }

    /**
     * Verifies that the client includes the required information in the <em>get-cmd-handling-adapter-instances</em> operation
     * request message sent to the device connection service.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesIncludesRequiredInformationInRequest() {

        final String deviceId = "deviceId";
        final String gatewayId = "gw-1";

        // WHEN getting last known gateway information
        client.getCommandHandlingAdapterInstances(deviceId, Collections.singletonList(gatewayId), span.context());

        // THEN the message being sent contains the device ID in its properties
        final Message sentMessage = verifySenderSend();
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo(deviceId);
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(DeviceConnectionConstants.DeviceConnectionAction.GET_CMD_HANDLING_ADAPTER_INSTANCES.getSubject());
        // and the 'via' gateway ID in the payload
        final JsonObject msgJsonPayload = MessageHelper.getJsonPayload(sentMessage);
        assertThat(msgJsonPayload).isNotNull();
        final JsonArray gatewaysJsonArray = msgJsonPayload.getJsonArray(DeviceConnectionConstants.FIELD_GATEWAY_IDS);
        assertThat(gatewaysJsonArray.getList().iterator().next()).isEqualTo(gatewayId);
    }

    private Message createNoContentResponseMessage(final Object correlationId) {
        final Message response = ProtonHelper.message();
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_NO_CONTENT);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(correlationId);
        return response;
    }

    private Message verifySenderSend() {
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), VertxMockSupport.anyHandler());
        return messageCaptor.getValue();
    }

}
