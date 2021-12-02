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


package org.eclipse.hono.client.registry.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.TriTuple;
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
 * Tests verifying behavior of {@link ProtonBasedCredentialsClientTest}.
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

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {

        span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);

        final EventBus eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
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

        cache = mock(Cache.class);
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
        assertThat(MessageHelper.getJsonPayload(sentMessage).getString(CredentialsConstants.FIELD_TYPE))
            .isEqualTo(credentialsType);
        assertThat(MessageHelper.getJsonPayload(sentMessage).getString(CredentialsConstants.FIELD_AUTH_ID))
            .isEqualTo(authId);
        final Message response = ProtonHelper.message();
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        MessageHelper.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, credentialsObject.toBuffer());
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
                        final var responseCacheKey = ArgumentCaptor.forClass(TriTuple.class);
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
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        MessageHelper.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, credentialsObject.toBuffer());
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
                RequestResponseApiConstants.CONTENT_TYPE_APPLICATION_JSON,
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
                RequestResponseApiConstants.CONTENT_TYPE_APPLICATION_JSON,
                credentialsObject.toBuffer(),
                null,
                null);
        when(cache.getIfPresent(any())).thenReturn(credentialsResult);

        final ArgumentCaptor<TriTuple<?, ?, ?>> cacheKey = ArgumentCaptor.forClass(TriTuple.class);
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

}
