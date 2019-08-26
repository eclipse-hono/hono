/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.time.Duration;

import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantConstants.TenantAction;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.eclipse.hono.util.TriTuple;
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
 * Tests verifying behavior of {@link TenantClientImpl}.
 *
 */
@ExtendWith(VertxExtension.class)
public class TenantClientImplTest {

    private Vertx vertx;
    private ProtonSender sender;
    private TenantClientImpl client;
    private ExpiringValueCache<Object, TenantResult<TenantObject>> cache;
    private Tracer tracer;
    private Span span;
    private HonoConnection connection;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
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

        cache = mock(ExpiringValueCache.class);
        client = new TenantClientImpl(connection, sender, receiver);
    }

    /**
     * Verifies that the client retrieves registration information from the
     * Device Registration service if no cache is configured.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetTenantInvokesServiceIfNoCacheConfigured(final VertxTestContext ctx) {

        // GIVEN an adapter with no cache configured
        client.setResponseCache(null);
        final JsonObject tenantResult = newTenantResult("tenant");

        // WHEN getting tenant information by ID
        client.get("tenant").setHandler(ctx.succeeding(r -> {
            // THEN the registration information has been retrieved from the service
            // and not been put to the cache
            verify(cache, never()).put(any(), any(TenantResult.class), any(Duration.class));
            // and the span is finished
            verify(span).finish();
            ctx.completeNow();
        }));

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message response = ProtonHelper.message(tenantResult.encode());
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);
    }

    /**
     * Verifies that on a cache miss the adapter retrieves tenant information
     * from the Tenant service and puts it to the cache.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetTenantAddsInfoToCacheOnCacheMiss(final VertxTestContext ctx) {

        // GIVEN an adapter with an empty cache
        client.setResponseCache(cache);
        final JsonObject tenantResult = newTenantResult("tenant");

        // WHEN getting tenant information
        client.get("tenant").setHandler(ctx.succeeding(tenant -> {
            // THEN the tenant result has been added to the cache
            verify(cache).put(eq(TriTuple.of(TenantAction.get, "tenant", null)), any(TenantResult.class), any(Duration.class));
            // and the span is finished
            verify(span).finish();
            ctx.completeNow();
        }));

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), anyHandler());
        final Message response = ProtonHelper.message(tenantResult.encode());
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);
    }

    /**
     * Verifies that tenant information is taken from cache if cache is configured and the cache has this tenant
     * information cached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantReturnsValueFromCache(final VertxTestContext ctx) {

        // GIVEN a client with a cache containing a tenant
        client.setResponseCache(cache);

        final JsonObject tenantJsonObject = newTenantResult("tenant");
        final TenantResult<TenantObject> tenantResult = client.getResult(
                HttpURLConnection.HTTP_OK, "application/json", tenantJsonObject.toBuffer(), null, null);

        when(cache.get(any(TriTuple.class))).thenReturn(tenantResult);

        // WHEN getting tenant information
        client.get("tenant").setHandler(ctx.succeeding(result -> {
            // THEN the tenant information is read from the cache
            assertEquals(tenantResult.getPayload(), result);
            // and no request message is sent to the service
            verify(sender, never()).send(any(Message.class), anyHandler());
            // and the span is finished
            verify(span).finish();
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the client fails if the Tenant service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantFailsWithSendError(final VertxTestContext ctx) {

        // GIVEN a client with no credit left 
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN getting tenant information
        client.get("tenant").setHandler(ctx.failing(t -> {
            // THEN the invocation fails and the span is marked as erroneous
            verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
            // and the span is finished
            verify(span).finish();
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the client fails if the Tenant service cannot be reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantFailsWithRejectedRequest(final VertxTestContext ctx) {

        // GIVEN a client with no credit left
        final ProtonDelivery update = mock(ProtonDelivery.class);
        when(update.getRemoteState()).thenReturn(new Rejected());
        when(update.remotelySettled()).thenReturn(true);
        when(sender.send(any(Message.class), anyHandler())).thenAnswer(invocation -> {
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(update);
            return mock(ProtonDelivery.class);
        });

        // WHEN getting tenant information
        client.get("tenant").setHandler(ctx.failing(t -> {
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
            // THEN the invocation fails and the span is marked as erroneous
            verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
            // and the span is finished
            verify(span).finish();
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the client includes the required information in the request
     * message sent to the Tenant service.
     */
    @Test
    public void testGetTenantByCaUsesRFC2253SubjectDn() {

        // GIVEN an adapter

        // WHEN getting tenant information for a subject DN
        final X500Principal dn = new X500Principal("CN=ca, OU=Hono, O=Eclipse");
        client.get(dn);

        // THEN the message being sent contains the subject DN in RFC 2253 format in the
        // payload
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), anyHandler());
        final Message sentMessage = messageCaptor.getValue();
        final JsonObject payload = MessageHelper.getJsonPayload(sentMessage);
        assertThat(payload.getString(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN), is("CN=ca,OU=Hono,O=Eclipse"));
    }

    /**
     * Verifies that the client includes the required information in the request
     * message sent to the Tenant service.
     */
    @Test
    public void testGetTenantIncludesRequiredInformationInRequest() {

        // GIVEN an adapter without a cache

        // WHEN getting tenant information
        client.get("tenant");

        // THEN the message being sent contains the tenant ID as search criteria
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), anyHandler());
        final Message sentMessage = messageCaptor.getValue();
        assertNull(MessageHelper.getTenantId(sentMessage));
        assertThat(sentMessage.getMessageId(), is(notNullValue()));
        assertThat(sentMessage.getSubject(), is(TenantConstants.TenantAction.get.toString()));
        assertThat(MessageHelper.getJsonPayload(sentMessage).getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID), is("tenant"));
    }

    private JsonObject newTenantResult(final String tenantId) {

        final JsonObject returnObject = new JsonObject().
                put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId).
                put(TenantConstants.FIELD_ENABLED, true);
        return returnObject;
    }
}
