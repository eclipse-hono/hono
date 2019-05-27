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

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.TriTuple;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.net.HttpURLConnection;
import java.time.Duration;

import static org.eclipse.hono.client.impl.VertxMockSupport.anyHandler;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests verifying behavior of {@link CredentialsClientImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CredentialsClientImplTest {

    /**
     * Time out test cases after 5 seconds.
     */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(5);

    private ProtonSender sender;
    private CredentialsClientImpl client;
    private ExpiringValueCache<Object, CredentialsResult<CredentialsObject>> cache;
    private Tracer tracer;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        final SpanContext spanContext = mock(SpanContext.class);

        span = mock(Span.class);
        when(span.context()).thenReturn(spanContext);
        final Tracer.SpanBuilder spanBuilder = HonoClientUnitTestHelper.mockSpanBuilder(span);

        tracer = mock(Tracer.class);
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);

        final HonoConnection connection = HonoClientUnitTestHelper.mockHonoConnection(mock(Vertx.class),
                new RequestResponseClientConfigProperties());
        when(connection.getTracer()).thenReturn(tracer);

        sender = HonoClientUnitTestHelper.mockProtonSender();
        cache = mock(ExpiringValueCache.class);
        client = new CredentialsClientImpl(connection, "tenant", sender, HonoClientUnitTestHelper.mockProtonReceiver());
    }

    /**
     * Verifies that the client retrieves credentials from the Device Registration service if no cache is configured.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetCredentialsInvokesServiceIfNoCacheConfigured(final TestContext ctx) {

        final String authId = "test-auth";
        final String credentialsType = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;
        final JsonObject credentialsObject = newCredentialsResult("device", authId);
        final Message response = ProtonHelper.message(credentialsObject.encode());
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));

        // WHEN getting credential information information
        final Async assertion = ctx.async();
        client.get(credentialsType, authId).setHandler(ctx.asyncAssertSuccess(result -> assertion.complete()));

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), anyHandler());
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final Message sentMessage = messageCaptor.getValue();
        client.handleResponse(delivery, response);

        // THEN the credentials has been retrieved from the service
        assertion.await();
        // and not been put to the cache
        verify(cache, never()).put(any(), any(CredentialsResult.class), any(Duration.class));
        // and the span is finished
        verify(span).finish();

        assertEquals(sentMessage.getSubject(), CredentialsConstants.CredentialsAction.get.toString());
        assertEquals(MessageHelper.getJsonPayload(sentMessage).getString(CredentialsConstants.FIELD_TYPE),
                credentialsType);
        assertEquals(MessageHelper.getJsonPayload(sentMessage).getString(CredentialsConstants.FIELD_AUTH_ID), authId);
    }

    /**
     * Verifies that on a cache miss the adapter retrieves credentials information from the credentials service and puts
     * it to the cache.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetCredentialsAddsResponseToCacheOnCacheMiss(final TestContext ctx) {

        final String authId = "test-auth";
        final String credentialsType = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;

        // GIVEN an adapter with an empty cache
        client.setResponseCache(cache);
        final JsonObject credentialsObject = newCredentialsResult("device", authId);
        final JsonObject clientContext = new JsonObject();

        // WHEN getting credentials information
        final Async get = ctx.async();
        client.get(credentialsType, authId, clientContext)
                .setHandler(ctx.asyncAssertSuccess(tenant -> get.complete()));
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        verify(client.sender).send(messageCaptor.capture(), anyHandler());

        final Message response = ProtonHelper.message(credentialsObject.encode());
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        client.handleResponse(delivery, response);

        // THEN the credentials result has been added to the cache.
        get.await();
        verify(cache).put(
                eq(TriTuple.of(CredentialsConstants.CredentialsAction.get,
                        String.format("%s-%s", credentialsType, authId), clientContext.hashCode())),
                any(CredentialsResult.class), any(Duration.class));
        // and the span is finished
        verify(span).finish();
    }

    /**
     * Verifies that credentials is taken from cache, if cache is configured and the cache has this credentials cached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsReturnsValueFromCache(final TestContext ctx) {

        final String authId = "test-auth";
        final String credentialsType = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;

        // GIVEN a client with a cache containing a credentials
        client.setResponseCache(cache);
        final JsonObject credentialsObject = newCredentialsResult("device", authId);
        final CredentialsResult<CredentialsObject> credentialsResult = client
                .getResult(HttpURLConnection.HTTP_OK, RequestResponseApiConstants.CONTENT_TYPE_APPLICATION_JSON,
                        credentialsObject.toBuffer(), null, null);
        when(cache.get(any(TriTuple.class))).thenReturn(credentialsResult);

        // WHEN getting credentials
        client.get(credentialsType, authId)
                .setHandler(ctx.asyncAssertSuccess(result -> {
                    // THEN the credentials is read from the cache
                    ctx.assertEquals(credentialsResult.getPayload(), result);
                    verify(sender, never()).send(any(Message.class), anyHandler());
                    // and the span is finished
                    verify(span).finish();
                }));

    }

    /**
     * Verifies that the client fails if the credentials service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsWithSendError(final TestContext ctx) {

        // GIVEN a client with no credit left
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN getting credentials
        client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "test-auth")
                .setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the invocation fails and the span is marked as erroneous
                    verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                    // and the span is finished
                    verify(span).finish();
                }));
    }

    /**
     * Verifies that the client fails if the credentials service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsWithRejectedRequest(final TestContext ctx) {

        // GIVEN a client with no credit left
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN getting credentials
        client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "test-auth")
                .setHandler(ctx.asyncAssertFailure(t -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST,
                            ((ServiceInvocationException) t).getErrorCode());
                    // THEN the invocation fails and the span is marked as erroneous
                    verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                    // and the span is finished
                    verify(span).finish();
                }));
    }

    private JsonObject newCredentialsResult(final String deviceId, final String authId) {
        return JsonObject.mapFrom(CredentialsObject.fromHashedPassword(
                deviceId,
                authId,
                "$2a$11$gYh52ApJeJcLvKrXHkGm5.xtLf7PVJySmXrt0EvFfLjCfLdIdvoay",
                CredentialsConstants.HASH_FUNCTION_BCRYPT,
                null, null, null));
    }
}
