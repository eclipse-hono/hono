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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
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
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import com.github.benmanes.caffeine.cache.Cache;

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
 * Tests verifying behavior of {@link ProtonBasedCredentialsClient}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
class ProtonBasedCredentialsClientTest {

    private HonoConnection connection;
    private ProtonSender sender;
    private ProtonReceiver receiver;
    private ProtonBasedCredentialsClient client;
    private Cache<Object, CredentialsResult<CredentialsObject>> cache;
    private Span span;
    private Vertx vertx;
    private EventBus eventBus;
    private final ConcurrentMap<Object, CredentialsResult<CredentialsObject>> cacheBackingMap = new ConcurrentHashMap<>();

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


    private static JsonObject newCredentialsResult(final String deviceId, final String authId) {
        return JsonObject.mapFrom(CredentialsObject.fromHashedPassword(
                deviceId,
                authId,
                "$2a$11$gYh52ApJeJcLvKrXHkGm5.xtLf7PVJySmXrt0EvFfLjCfLdIdvoay",
                CredentialsConstants.HASH_FUNCTION_BCRYPT,
                null, null, null));
    }

    private void givenAClient(final Cache<Object, CredentialsResult<CredentialsObject>> cache) {
        client = new ProtonBasedCredentialsClient(
                connection,
                SendMessageSampler.Factory.noop(),
                cache);
    }

    /**
     * Verifies that the client retrieves credentials from the Credentials service
     * if no cache is configured.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetCredentialsInvokesServiceIfNoCacheConfigured(final VertxTestContext ctx) {

        // GIVEN a client with no cache configured
        givenAClient(null);

        final String authId = "test-auth";
        final String credentialsType = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;
        final JsonObject credentialsObject = newCredentialsResult("device", authId);

        // WHEN getting credential information information
        client.get("tenant", credentialsType, authId, span.context()).onComplete(ctx.succeeding(credentials -> {
            ctx.verify(() -> {
                // THEN the credentials have been retrieved from the service
                verify(cache, never()).getIfPresent(any());
                assertThat(credentials.getDeviceId()).isEqualTo("device");
                // and put to the cache
                verify(cache, never()).put(any(), any(CredentialsResult.class));
                // and the span is finished
                verify(span).finish();

            });
            ctx.completeNow();
        }));

        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(sentMessage.getSubject()).isEqualTo(CredentialsConstants.CredentialsAction.get.toString());
        assertThat(AmqpUtils.getJsonPayload(sentMessage).getString(CredentialsConstants.FIELD_TYPE))
            .isEqualTo(credentialsType);
        assertThat(AmqpUtils.getJsonPayload(sentMessage).getString(CredentialsConstants.FIELD_AUTH_ID))
            .isEqualTo(authId);
        final Message response = ProtonHelper.message();
        AmqpUtils.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        AmqpUtils.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        AmqpUtils.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, credentialsObject.toBuffer());
        response.setCorrelationId(sentMessage.getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(delivery, response);
    }

    /**
     * Verifies that on a cache miss the adapter retrieves credentials information from the
     * Credentials service and puts it to the cache.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetCredentialsAddsResponseToCacheOnCacheMiss(final VertxTestContext ctx) {

        // GIVEN an adapter with an empty cache
        givenAClient(cache);

        final String authId = "test-auth";
        final String credentialsType = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;
        final JsonObject credentialsObject = newCredentialsResult("device", authId);
        final JsonObject clientContext = new JsonObject();

        // WHEN getting credentials information
        client.get("tenant", credentialsType, authId, clientContext, span.context())
                .onComplete(ctx.succeeding(credentials -> {
                    ctx.verify(() -> {
                        final var responseCacheKey = ArgumentCaptor.forClass(AnnotatedCacheKey.class);
                        verify(cache).getIfPresent(responseCacheKey.capture());
                        assertThat(credentials.getDeviceId()).isEqualTo("device");
                        // THEN the credentials result has been added to the cache.
                        verify(cache).put(
                                eq(responseCacheKey.getValue()),
                                any(CredentialsResult.class));
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
        AmqpUtils.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, credentialsObject.toBuffer());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(delivery, response);
    }

    /**
     * Verifies that Credentials are taken from cache, if cache is configured and the cache has this credentials cached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsReturnsValueFromCache(final VertxTestContext ctx) {

        // GIVEN a client with a cache containing a credentials
        givenAClient(cache);
        final String authId = "test-auth";
        final String credentialsType = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;

        final JsonObject credentialsObject = newCredentialsResult("device", authId);
        final CredentialsResult<CredentialsObject> credentialsResult = client.getResult(
                HttpURLConnection.HTTP_OK,
                MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
                credentialsObject.toBuffer(),
                null,
                null);
        when(cache.getIfPresent(any())).thenReturn(credentialsResult);

        // WHEN getting credentials
        client.get("tenant", credentialsType, authId, span.context())
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        // THEN the credentials are read from the cache
                        verify(cache).getIfPresent(any());
                        assertThat(result).isEqualTo(credentialsResult.getPayload());
                        verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
                        // and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that Credentials are taken from cache, if cache is configured and the cache contains an entry for
     * the given criteria.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsWithClientContextReturnsValueFromCache(final VertxTestContext ctx) {

        // GIVEN a client with a cache containing an entry
        givenAClient(cache);
        final String authId = "test-auth";
        final String credentialsType = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;
        final var bytes = new byte[] {0x01, 0x02};

        final JsonObject credentialsObject = newCredentialsResult("device", authId);
        final CredentialsResult<CredentialsObject> credentialsResult = client.getResult(
                HttpURLConnection.HTTP_OK,
                MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
                credentialsObject.toBuffer(),
                null,
                null);
        when(cache.getIfPresent(any())).thenReturn(credentialsResult);

        final var cacheKey = ArgumentCaptor.forClass(AnnotatedCacheKey.class);
        // WHEN getting credentials with a client context that contains a raw byte array
        final var contextWithByteArray = new JsonObject().put("bytes", bytes);
        client.get("tenant", credentialsType, authId, contextWithByteArray, span.context())
            .onFailure(ctx::failNow)
            .compose(result -> {
                ctx.verify(() -> {
                    // THEN the credentials are read from the cache
                    verify(cache).getIfPresent(cacheKey.capture());
                    assertThat(result).isEqualTo(credentialsResult.getPayload());
                    verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
                    // and the span is finished
                    verify(span).finish();
                });
                // and WHEN getting the same credentials with a client context that contains
                // the Base64 encoding of the byte array
                final var contextWithBase64String = new JsonObject().put("bytes", contextWithByteArray.getValue("bytes"));
                return client.get("tenant", credentialsType, authId, contextWithBase64String, span.context());
            })
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    // THEN same credentials are read from the cache using the same key as before
                    verify(cache, times(2)).getIfPresent(cacheKey.getValue());
                    assertThat(result).isEqualTo(credentialsResult.getPayload());
                    verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
                    // and the span is finished
                    verify(span, times(2)).finish();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the client fails if the credentials service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsWithSendError(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        givenAClient(cache);
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN getting credentials
        client.get("tenant", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "test-auth", span.context())
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
     * Verifies that the client fails if the credentials service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsWithRejectedRequest(final VertxTestContext ctx) {

        // GIVEN a client
        givenAClient(cache);

        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN getting credentials
        client.get("tenant", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "test-auth", span.context())
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(ClientErrorException.class);
                        assertThat(((ClientErrorException) t).getErrorCode())
                                .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                        // THEN the invocation fails and the span is marked as erroneous
                        verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                        // and the span is finished
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that when a client with a cache is started, the client registers itself for notifications of the types
     * {@link AllDevicesOfTenantDeletedNotification}, {@link DeviceChangeNotification} and
     * {@link CredentialsChangeNotification}.
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
                                eq(NotificationEventBusSupport
                                        .getEventBusAddress(AllDevicesOfTenantDeletedNotification.TYPE)),
                                VertxMockSupport.anyHandler());
                        verify(eventBus).consumer(
                                eq(NotificationEventBusSupport.getEventBusAddress(DeviceChangeNotification.TYPE)),
                                VertxMockSupport.anyHandler());
                        verify(eventBus).consumer(
                                eq(NotificationEventBusSupport.getEventBusAddress(CredentialsChangeNotification.TYPE)),
                                VertxMockSupport.anyHandler());

                        verify(eventBus).consumer(eq(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT), VertxMockSupport.anyHandler());
                        verifyNoMoreInteractions(eventBus);
                    });
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the client removes all credentials of a tenant from the cache if it receives a notification that
     * tenant all devices of the tenant have been deleted.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAllDevicesOfTenantDeletedNotificationRemovesValueFromCache(final VertxTestContext ctx) {
        final String tenantId = "the-tenant-id";
        final String deviceId = "the-device-id";

        givenAClient(cache);

        final var notificationHandlerCaptor = getEventBusConsumerHandlerArgumentCaptor(AllDevicesOfTenantDeletedNotification.TYPE);

        final Set<AnnotatedCacheKey<?>> expectedCacheRemovals = new HashSet<>();

        // GIVEN a client with a cache containing credentials of two tenants
        client.start()
                .compose(v -> addResultToCache("other-tenant", deviceId, "other"))
                .compose(v -> addResultToCache(tenantId, deviceId, "auth-id1"))
                .map(expectedCacheRemovals::add)
                .compose(v -> addResultToCache(tenantId, deviceId, "auth-id2"))
                .map(expectedCacheRemovals::add)
                .onComplete(ctx.succeeding(ok -> ctx.verify(() -> {

                    // WHEN receiving a notification that all devices of a tenant have been deleted
                    sendViaEventBusMock(new AllDevicesOfTenantDeletedNotification(tenantId, Instant.now()),
                            notificationHandlerCaptor.getValue());

                    // THEN the cache is invalidated for all credentials of the tenant and not for other tenants
                    ctx.verify(() -> verify(cache).invalidateAll(expectedCacheRemovals));
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the client removes credentials of a device from the cache if it receives a notification about a
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

        // GIVEN a client with a cache containing credentials of two tenants
        client.start()
                .compose(v -> addResultToCache("other-tenant", deviceId, "other"))
                .compose(v -> addResultToCache(tenantId, "other-device", "other"))
                .compose(v -> addResultToCache(tenantId, deviceId, "auth-id1"))
                .map(expectedCacheRemovals::add)
                .compose(v -> addResultToCache(tenantId, deviceId, "auth-id2"))
                .map(expectedCacheRemovals::add)
                .onComplete(ctx.succeeding(ok -> ctx.verify(() -> {

                    // WHEN receiving a notification about a change on the device
                    sendViaEventBusMock(
                            new DeviceChangeNotification(LifecycleChange.UPDATE, tenantId, deviceId, Instant.now(), false),
                            notificationHandlerCaptor.getValue());

                    // THEN the cache is invalidated for all credentials of the changed device and not for other devices
                    ctx.verify(() -> verify(cache).invalidateAll(expectedCacheRemovals));
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the client removes credentials of a device from the cache if it receives a notification about a
     * change in the credentials of that device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCredentialsChangeNotificationRemovesValueFromCache(final VertxTestContext ctx) {
        final String tenantId = "the-tenant-id";
        final String deviceId = "the-device-id";

        givenAClient(cache);

        final var notificationHandlerCaptor = getEventBusConsumerHandlerArgumentCaptor(CredentialsChangeNotification.TYPE);

        final Set<AnnotatedCacheKey<?>> expectedCacheRemovals = new HashSet<>();

        // GIVEN a client with a cache containing credentials of two tenants
        client.start()
                .compose(v -> addResultToCache("other-tenant", deviceId, "other"))
                .compose(v -> addResultToCache(tenantId, deviceId, "auth-id1"))
                .map(expectedCacheRemovals::add)
                .compose(v -> addResultToCache(tenantId, deviceId, "auth-id2"))
                .map(expectedCacheRemovals::add)
                .onComplete(ctx.succeeding(ok -> ctx.verify(() -> {

                    // WHEN receiving a notification about a change on the credentials
                    sendViaEventBusMock(new CredentialsChangeNotification(tenantId, deviceId, Instant.now()),
                            notificationHandlerCaptor.getValue());

                    // THEN the cache is invalidated for all credentials of the changed device and not for other devices
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
            final String authId) {

        final String credentialsType = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;

        final CredentialsResult<CredentialsObject> credentialsResult = CredentialsResult.from(HttpURLConnection.HTTP_OK,
                new CredentialsObject(deviceId, authId, credentialsType));

        final ArgumentCaptor<AnnotatedCacheKey<?>> responseCacheKey = ArgumentCaptor.forClass(AnnotatedCacheKey.class);
        when(cache.getIfPresent(responseCacheKey.capture())).thenReturn(credentialsResult);

        return client.get(tenantId, credentialsType, authId, new JsonObject(), null)
                .map(credentialsObject -> {

                    final AnnotatedCacheKey<?> cacheKey = responseCacheKey.getValue();
                    cacheKey.putAttribute("device-id", deviceId);
                    cacheBackingMap.put(cacheKey, credentialsResult);

                    return cacheKey;
                });
    }

}
