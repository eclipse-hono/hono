/**
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
 */


package org.eclipse.hono.client.registry.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.config.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.client.util.AnnotatedCacheKey;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.NotificationType;
import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import com.github.benmanes.caffeine.cache.Cache;

import io.opentracing.Span;
import io.opentracing.Tracer;
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
 * Tests verifying behavior of {@link ProtonBasedDeviceRegistrationClient}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
class ProtonBasedDeviceRegistrationClientTest {

    private HonoConnection connection;
    private ProtonSender sender;
    private ProtonReceiver receiver;
    private ProtonBasedDeviceRegistrationClient client;
    private Cache<Object, RegistrationResult> cache;
    private Span span;
    private Vertx vertx;
    private EventBus eventBus;
    private final ConcurrentMap<Object, RegistrationResult> cacheBackingMap = new ConcurrentHashMap<>();

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {

        span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);

        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        VertxMockSupport.executeBlockingCodeImmediately(vertx);
        receiver = AmqpClientUnitTestHelper.mockProtonReceiver();
        sender = AmqpClientUnitTestHelper.mockProtonSender();

        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx, config, tracer);
        when(connection.connect()).thenReturn(Future.succeededFuture());
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        when(connection.createReceiver(anyString(), any(ProtonQoS.class), any(ProtonMessageHandler.class), VertxMockSupport.anyHandler()))
                .thenReturn(Future.succeededFuture(receiver));
        when(connection.createSender(anyString(), any(ProtonQoS.class), VertxMockSupport.anyHandler()))
            .thenReturn(Future.succeededFuture(sender));

        cache = mock(Cache.class);
        when(cache.asMap()).thenReturn(cacheBackingMap);
    }

    private static JsonObject newRegistrationAssertionResult(final String deviceId) {
        return newRegistrationAssertionResult(deviceId, null);
    }

    private static JsonObject newRegistrationAssertionResult(final String deviceId, final String defaultContentType) {

        final JsonObject result = new JsonObject();
        result.put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        if (defaultContentType != null) {
            result.put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, new JsonObject()
                    .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, defaultContentType));
        }
        return result;
    }

    private void givenAClient(final Cache<Object, RegistrationResult> cache) {
        client = new ProtonBasedDeviceRegistrationClient(
                connection,
                SendMessageSampler.Factory.noop(),
                cache);
    }

    /**
     * Verifies that on a cache miss the client retrieves registration information
     * from the Device Registration service and puts it to the cache.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationAddsResponseToCacheOnCacheMiss(final VertxTestContext ctx) {

        final var registrationAssertion = newRegistrationAssertionResult("myDevice");

        // GIVEN a client with an empty cache
        givenAClient(cache);
        when(cache.getIfPresent(any())).thenReturn(null);

        // WHEN getting registration information
        client.assertRegistration("tenant", "myDevice", null, span.context()).onComplete(ctx.succeeding(result -> {
            ctx.verify(() -> {
                // THEN the registration information has been added to the cache
                final var responseCacheKey = ArgumentCaptor.forClass(AnnotatedCacheKey.class);
                verify(cache).getIfPresent(responseCacheKey.capture());
                assertThat(result.getDeviceId()).isEqualTo("myDevice");
                assertThat(result.getAuthorizedGateways()).isEmpty();
                verify(cache).put(
                        eq(responseCacheKey.getValue()),
                        argThat((RegistrationResult response) -> registrationAssertion.equals(response.getPayload())));
                // and the span is finished
                verify(span).finish();
            });
            ctx.completeNow();
        }));

        final Message request = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        final Message response = ProtonHelper.message();
        AmqpUtils.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        AmqpUtils.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(request.getMessageId());
        AmqpUtils.setJsonPayload(response, registrationAssertion);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(delivery, response);
    }

    /**
     * Verifies that the client retrieves registration information from the
     * Device Registration service if no cache is configured.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationInvokesServiceIfNoCacheConfigured(final VertxTestContext ctx) {

        // GIVEN an adapter with no cache configured
        givenAClient(null);
        final JsonObject registrationAssertion = newRegistrationAssertionResult("device");

        // WHEN getting registration information
        client.assertRegistration("tenant", "device", null, span.context()).onComplete(ctx.succeeding(result -> {
            ctx.verify(() -> {
                // THEN the registration information has been retrieved from the service
                verify(cache, never()).getIfPresent(any());
                assertThat(result.getDeviceId()).isEqualTo("device");
                // and not been put to the cache
                verify(cache, never()).put(any(), any(RegistrationResult.class));
                // and the span is finished
                verify(span).finish();
            });
            ctx.completeNow();
        }));

        final Message request = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        final Message response = ProtonHelper.message();
        response.setCorrelationId(request.getMessageId());
        AmqpUtils.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        AmqpUtils.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        AmqpUtils.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, registrationAssertion.toBuffer());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(delivery, response);
    }

    /**
     * Verifies that registration information is taken from cache.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetRegistrationInfoReturnsValueFromCache(final VertxTestContext ctx) {

        // GIVEN an adapter with a cache containing a registration assertion
        // response for "device"
        givenAClient(cache);
        final JsonObject registrationAssertion = newRegistrationAssertionResult("device");
        final RegistrationResult regResult = RegistrationResult.from(HttpURLConnection.HTTP_OK, registrationAssertion);
        when(cache.getIfPresent(any())).thenReturn(regResult);

        // WHEN getting registration information
        client.assertRegistration("tenant", "device", "gateway", span.context())
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    // THEN the registration information is read from the cache
                    verify(cache).getIfPresent(any());
                    assertThat(result.getDeviceId()).isEqualTo("device");
                    // and no request message is sent to the service
                    verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
                    // and the span is finished
                    verify(span).finish();
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that the client includes the required information in the request
     * message sent to the Device Registration service.
     */
    @Test
    public void testGetRegistrationInfoIncludesRequiredParamsInRequest() {

        // GIVEN an adapter without a cache
        givenAClient(null);

        // WHEN getting registration information
        client.assertRegistration("tenant", "device", "gateway", span.context());

        // THEN the message being sent contains the device ID and the gateway ID
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(AmqpUtils.getDeviceId(sentMessage)).isEqualTo("device");
        assertThat(AmqpUtils.getApplicationProperty(sentMessage, MessageHelper.APP_PROPERTY_GATEWAY_ID, String.class))
                .isEqualTo("gateway");
    }

    /**
     * Verifies that when a client with a cache is started, the client registers itself for notifications of the
     * types {@link AllDevicesOfTenantDeletedNotification} and {@link DeviceChangeNotification}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatNotificationConsumersAreRegistered(final VertxTestContext ctx) {

        // GIVEN a client with a cache
        givenAClient(cache);

        // WHEN starting the client
        client.start()
                .onComplete(ctx.succeeding(ok -> ctx.verify(() -> {

                    // THEN the expected consumers for notifications are registered
                    ctx.verify(() -> {
                        verify(eventBus).consumer(
                                eq(NotificationEventBusSupport.getEventBusAddress(AllDevicesOfTenantDeletedNotification.TYPE)),
                                VertxMockSupport.anyHandler());
                        verify(eventBus).consumer(
                                eq(NotificationEventBusSupport.getEventBusAddress(DeviceChangeNotification.TYPE)),
                                VertxMockSupport.anyHandler());

                        verify(eventBus).consumer(eq(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT), VertxMockSupport.anyHandler());
                        verifyNoMoreInteractions(eventBus);
                    });
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the client removes all registrations of a tenant from the cache if it receives a notification that
     * tenant all devices of the tenant have been deleted.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAllDevicesOfTenantDeletedNotificationRemovesValueFromCache(final VertxTestContext ctx) {
        final String tenantId = "the-tenant-id";

        givenAClient(cache);

        final var notificationHandlerCaptor = getEventBusConsumerHandlerArgumentCaptor(AllDevicesOfTenantDeletedNotification.TYPE);

        final Set<AnnotatedCacheKey<?>> expectedCacheRemovals = new HashSet<>();

        // GIVEN a client with a cache containing device registrations of two tenants
        client.start()
                .compose(v -> addResultToCache("other-tenant", "device-id1", "gateway-id"))
                .compose(v -> addResultToCache(tenantId, "device-id1", "gateway-id"))
                .map(expectedCacheRemovals::add)
                .compose(v -> addResultToCache(tenantId, "device-id2", "gateway-id"))
                .map(expectedCacheRemovals::add)
                .onComplete(ctx.succeeding(ok -> ctx.verify(() -> {

                    // WHEN receiving a notification that all devices of a tenant have been deleted
                    sendViaEventBusMock(new AllDevicesOfTenantDeletedNotification(tenantId, Instant.now()),
                            notificationHandlerCaptor.getValue());

                    // THEN the cache is invalidated for all registrations of the tenant
                    ctx.verify(() -> verify(cache).invalidateAll(expectedCacheRemovals));
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the client removes registrations of a device from the cache if it receives a notification about a
     * change in that device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeviceChangeNotificationRemovesValueFromCache(final VertxTestContext ctx) {
        final String tenantId = "the-tenant-id";
        final String deviceId = "the-device-id";

        givenAClient(cache);

        final var notificationHandlerCaptor = getEventBusConsumerHandlerArgumentCaptor(DeviceChangeNotification.TYPE);

        final Set<AnnotatedCacheKey<?>> expectedCacheRemovals = new HashSet<>();

        // GIVEN a client with a cache containing device registrations of two tenants
        client.start()
                .compose(v -> addResultToCache("other-tenant", deviceId, "gateway-id"))
                .compose(v -> addResultToCache(tenantId, "other-device", "gateway-id"))
                .compose(v -> addResultToCache(tenantId, deviceId, "gateway-id"))
                .map(expectedCacheRemovals::add)
                .compose(v -> addResultToCache(tenantId, deviceId, "other-gateway"))
                .map(expectedCacheRemovals::add)
                .onComplete(ctx.succeeding(ok -> ctx.verify(() -> {

                    // WHEN receiving a notification about a change on the device
                    sendViaEventBusMock(new DeviceChangeNotification(
                                    LifecycleChange.UPDATE, tenantId, deviceId, Instant.now(), false),
                            notificationHandlerCaptor.getValue());

                    // THEN the cache is invalidated for registrations of the changed device and not for other devices
                    ctx.verify(() -> verify(cache).invalidateAll(expectedCacheRemovals));
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the client removes registrations of a gateway from the cache if it receives a notification about a
     * change in that gateway.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeviceChangeNotificationRemovesGatewaysFromCache(final VertxTestContext ctx) {
        final String tenantId = "the-tenant-id";
        final String deviceId = "the-device-id";
        final String gatewayId = "the-device-id";

        givenAClient(cache);

        final var notificationHandlerCaptor = getEventBusConsumerHandlerArgumentCaptor(DeviceChangeNotification.TYPE);

        final Set<AnnotatedCacheKey<?>> expectedCacheRemovals = new HashSet<>();

        // GIVEN a client with a cache containing device registrations of two tenants
        client.start()
                .compose(v -> addResultToCache("other-tenant", deviceId, "gateway-id"))
                .compose(v -> addResultToCache(tenantId, "other-device", "gateway-id"))
                .compose(v -> addResultToCache(tenantId, deviceId, gatewayId))
                .map(expectedCacheRemovals::add)
                .compose(v -> addResultToCache(tenantId, gatewayId, "other-gateway"))
                .map(expectedCacheRemovals::add)
                .onComplete(ctx.succeeding(ok -> ctx.verify(() -> {

                    // WHEN receiving a notification about a change on the gateway device
                    sendViaEventBusMock(new DeviceChangeNotification(
                                    LifecycleChange.UPDATE, tenantId, gatewayId, Instant.now(), false),
                            notificationHandlerCaptor.getValue());

                    // THEN the cache is invalidated for registrations where the device id matches the gateway id or the
                    // device id and no other registrations
                    ctx.verify(() -> verify(cache).invalidateAll(expectedCacheRemovals));
                    ctx.completeNow();
                })));
    }

    private <T extends AbstractNotification> ArgumentCaptor<Handler<io.vertx.core.eventbus.Message<T>>> getEventBusConsumerHandlerArgumentCaptor(
            final NotificationType<T> notificationType) {

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Handler<io.vertx.core.eventbus.Message<T>>> notificationHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(eventBus)
                .consumer(eq(NotificationEventBusSupport.getEventBusAddress(notificationType)), notificationHandlerCaptor.capture());
        return notificationHandlerCaptor;
    }

    private <T extends AbstractNotification> void sendViaEventBusMock(final T notification,
            final Handler<io.vertx.core.eventbus.Message<T>> messageHandler) {

        @SuppressWarnings("unchecked")
        final io.vertx.core.eventbus.Message<T> messageMock = mock(io.vertx.core.eventbus.Message.class);
        when(messageMock.body()).thenReturn(notification);
        messageHandler.handle(messageMock);
    }

    private Future<AnnotatedCacheKey<?>> addResultToCache(final String tenantId, final String deviceId,
            final String gatewayId) {

        final JsonObject registrationAssertion = newRegistrationAssertionResult(deviceId);
        final RegistrationResult registrationResult = RegistrationResult.from(HttpURLConnection.HTTP_OK,
                registrationAssertion);
        when(cache.getIfPresent(any())).thenReturn(registrationResult);

        final ArgumentCaptor<AnnotatedCacheKey<?>> responseCacheKey = ArgumentCaptor.forClass(AnnotatedCacheKey.class);
        when(cache.getIfPresent(responseCacheKey.capture())).thenReturn(registrationResult);

        return client.assertRegistration(tenantId, deviceId, gatewayId, null)
                .map(assertion -> {
                    final AnnotatedCacheKey<?> cacheKey = responseCacheKey.getValue();
                    cacheBackingMap.put(cacheKey, registrationResult);
                    return cacheKey;
                });
    }

}
