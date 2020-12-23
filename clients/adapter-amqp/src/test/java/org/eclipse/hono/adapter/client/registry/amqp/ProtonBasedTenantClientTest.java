/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.client.registry.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.client.amqp.AmqpClientUnitTestHelper;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
 * Tests verifying behavior of {@link ProtonBasedTenantClient}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
class ProtonBasedTenantClientTest {

    private HonoConnection connection;
    private ProtonSender sender;
    private ProtonReceiver receiver;
    private ProtonBasedTenantClient client;
    private ExpiringValueCache<Object, Object> cache;
    private CacheProvider cacheProvider;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {

        span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);

        final Vertx vertx = mock(Vertx.class);
        receiver = AmqpClientUnitTestHelper.mockProtonReceiver();
        sender = AmqpClientUnitTestHelper.mockProtonSender();

        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx, config, tracer);
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        when(connection.createReceiver(anyString(), any(ProtonQoS.class), any(ProtonMessageHandler.class), VertxMockSupport.anyHandler()))
                .thenReturn(Future.succeededFuture(receiver));
        when(connection.createSender(anyString(), any(ProtonQoS.class), VertxMockSupport.anyHandler()))
            .thenReturn(Future.succeededFuture(sender));

        cache = mock(ExpiringValueCache.class);
        cacheProvider = mock(CacheProvider.class);
        when(cacheProvider.getCache(anyString())).thenReturn(cache);
    }

    private static JsonObject newTenantResult(final String tenantId) {

        final JsonObject returnObject = new JsonObject().
                put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId).
                put(TenantConstants.FIELD_ENABLED, true);
        return returnObject;
    }

    private void givenAClient(final CacheProvider cacheProvider) {
        client = new ProtonBasedTenantClient(
                connection,
                SendMessageSampler.Factory.noop(),
                new ProtocolAdapterProperties(),
                cacheProvider);
    }

    /**
     * Verifies that the client retrieves registration information from the
     * Device Registration service if no cache is configured.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantInvokesServiceIfNoCacheConfigured(final VertxTestContext ctx) {

        // GIVEN an adapter with no cache configured
        givenAClient(null);
        final JsonObject tenantResult = newTenantResult("tenant");

        // WHEN getting tenant information by ID
        client.get("tenant", span.context()).onComplete(ctx.succeeding(tenant -> {
            ctx.verify(() -> {
                // THEN the registration information has been retrieved from the service
                verify(cache, never()).get(any());
                assertThat(tenant).isNotNull();
                assertThat(tenant.getTenantId()).isEqualTo("tenant");
                // and not been put to the cache
                verify(cache, never()).put(any(), any(TenantResult.class), any(Duration.class));
                // and the span is finished
                verify(span).finish();
            });
            ctx.completeNow();
        }));

        final Message request = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        final Message response = ProtonHelper.message();
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(request.getMessageId());
        MessageHelper.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, tenantResult.toBuffer());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(delivery, response);
    }

    /**
     * Verifies that on a cache miss the adapter retrieves tenant information
     * from the Tenant service and puts it to the cache.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantAddsInfoToCacheOnCacheMiss(final VertxTestContext ctx) {

        // GIVEN an adapter with an empty cache
        givenAClient(cacheProvider);
        when(cache.get(any())).thenReturn(null);
        final JsonObject tenantResult = newTenantResult("tenant");

        // WHEN getting tenant information
        client.get("tenant", span.context()).onComplete(ctx.succeeding(tenant -> {
            ctx.verify(() -> {
                // THEN the tenant result has been added to the cache
                final var responseCacheKey = ArgumentCaptor.forClass(Object.class);
                verify(cache).get(responseCacheKey.capture());
                assertThat(tenant).isNotNull();
                assertThat(tenant.getTenantId()).isEqualTo("tenant");
                verify(cache).put(eq(responseCacheKey.getValue()), any(TenantResult.class), any(Duration.class));
                // and the span is finished
                verify(span).finish();
            });
            ctx.completeNow();
        }));

        final Message request = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        final Message response = ProtonHelper.message();
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(request.getMessageId());
        MessageHelper.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, tenantResult.toBuffer());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(delivery, response);
    }

    /**
     * Verifies that tenant information is taken from the cache if a cache is configured and the
     * cache contains an entry for the given tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantReturnsValueFromCache(final VertxTestContext ctx) {

        // GIVEN a client with a cache containing a tenant
        givenAClient(cacheProvider);
        // but no connection to the service
        when(sender.isOpen()).thenReturn(false);
        when(connection.isConnected(anyLong()))
            .thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));

        final JsonObject tenantJsonObject = newTenantResult("tenant");
        final TenantResult<TenantObject> tenantResult = client.getResult(
                HttpURLConnection.HTTP_OK, "application/json", tenantJsonObject.toBuffer(), null, null);

        when(cache.get(any())).thenReturn(tenantResult);

        // WHEN getting tenant information
        client.get("tenant", span.context()).onComplete(ctx.succeeding(result -> {
            ctx.verify(() -> {
                // THEN the tenant information is read from the cache
                verify(cache).get(any());
                assertThat(result).isEqualTo(tenantResult.getPayload());
                // and no request message is sent to the service
                verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
                // and the span is finished
                verify(span).finish();
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the client fails if the Tenant service cannot be reached
     * because the sender link has no credit left.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantFailsIfSenderHasNoCredit(final VertxTestContext ctx) {

        // GIVEN a client with no credit left 
        givenAClient(cacheProvider);
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN getting tenant information
        client.get("tenant", span.context()).onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                // THEN the invocation fails and the span is marked as erroneous
                verify(span).setTag(eq(Tags.ERROR.getKey()), eq(Boolean.TRUE));
                // and the span is finished
                verify(span).finish();
                assertThat(t).isInstanceOf(ServerErrorException.class)
                    .extracting("errorCode").isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the client includes the required information in the request
     * message sent to the Tenant service.
     */
    @Test
    public void testGetTenantByCaUsesRFC2253SubjectDn() {

        // GIVEN a client
        givenAClient(cacheProvider);

        // WHEN getting tenant information for a subject DN
        final X500Principal dn = new X500Principal("CN=ca, OU=Hono, O=Eclipse");
        client.get(dn, span.context());

        // THEN the message being sent contains the subject DN in RFC 2253 format in the
        // payload
        final Message request = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        final JsonObject payload = MessageHelper.getJsonPayload(request);
        assertThat(payload.getString(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN)).isEqualTo("CN=ca,OU=Hono,O=Eclipse");
    }

    /**
     * Verifies that the client includes the required information in the request
     * message sent to the Tenant service.
     */
    @Test
    public void testGetTenantIncludesRequiredInformationInRequest() {

        // GIVEN a client without a cache
        givenAClient(null);

        // WHEN getting tenant information
        client.get("tenant", span.context());

        // THEN the message being sent contains the tenant ID as search criteria
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(MessageHelper.getTenantId(sentMessage)).isNull();
        assertThat(sentMessage.getMessageId()).isNotNull();
        assertThat(sentMessage.getSubject()).isEqualTo(TenantConstants.TenantAction.get.toString());
        assertThat(MessageHelper.getJsonPayload(sentMessage).getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID)).isEqualTo("tenant");
    }
}
