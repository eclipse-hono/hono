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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.client.registry.amqp.ProtonBasedTenantClient;
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

import com.github.benmanes.caffeine.cache.Cache;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
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
    private Cache<Object, TenantResult<TenantObject>> cache;
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

        cache = mock(Cache.class);
    }

    private static JsonObject newTenantResult(final String tenantId) {

        final JsonObject returnObject = new JsonObject().
                put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId).
                put(TenantConstants.FIELD_ENABLED, true);
        return returnObject;
    }

    private void givenAClient(final Cache<Object, TenantResult<TenantObject>> cache) {
        client = new ProtonBasedTenantClient(
                connection,
                SendMessageSampler.Factory.noop(),
                cache);
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
                verify(cache, never()).getIfPresent(any());
                assertThat(tenant).isNotNull();
                assertThat(tenant.getTenantId()).isEqualTo("tenant");
                // and not been put to the cache
                verify(cache, never()).put(any(), any());
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
        givenAClient(cache);
        final JsonObject payload = newTenantResult("tenant");
        when(cache.getIfPresent(any())).thenReturn(null);
        final Checkpoint responseReceived = ctx.checkpoint(2);

        // WHEN getting tenant information

        final Future<TenantObject> requestOne = client.get("tenant", span.context());

        // THEN the client tries to retrieve the tenant from the cache
        final var responseCacheKey = ArgumentCaptor.forClass(Object.class);
        verify(cache).getIfPresent(responseCacheKey.capture());

        // AND sends the request message to the service
        final Message requestOneMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);

        // WHEN getting information for the same tenant in a another request
        // before the first request has completed

        assertThat(requestOne.isComplete()).isFalse();
        final Future<TenantObject> requestTwo = client.get("tenant", span.context());

        // THEN the client tries to retrieve the tenant from the cache again
        verify(cache, times(2)).getIfPresent(responseCacheKey.capture());
        // but does not find it in the cache
        assertThat(requestTwo.isComplete()).isFalse();

        // WHEN the response to the first request is received from the Tenant service
        final Message responseOneMessage = ProtonHelper.message();
        MessageHelper.addProperty(responseOneMessage, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(responseOneMessage, CacheDirective.maxAgeDirective(60));
        responseOneMessage.setCorrelationId(requestOneMessage.getMessageId());
        MessageHelper.setPayload(responseOneMessage, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, payload.toBuffer());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(delivery, responseOneMessage);

        // THEN the response is put to the cache
        verify(cache, times(1)).put(
                eq(responseCacheKey.getValue()),
                argThat(result -> {
                    return result.getPayload().getTenantId().equals("tenant")
                            && result.getCacheDirective().getMaxAge() == 60L;
                }));

        // and both requests succeed
        requestOne.onComplete(ctx.succeeding(tenant -> {
            ctx.verify(() -> {
                assertThat(tenant).isNotNull();
                assertThat(tenant.getTenantId()).isEqualTo("tenant");
            });
            responseReceived.flag();
        }));

        requestTwo.onComplete(ctx.succeeding(tenant -> {
            ctx.verify(() -> {
                assertThat(tenant).isNotNull();
                assertThat(tenant.getTenantId()).isEqualTo("tenant");
            });
            responseReceived.flag();
        }));
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
        givenAClient(cache);
        // but no connection to the service
        when(sender.isOpen()).thenReturn(false);
        when(connection.isConnected(anyLong()))
            .thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));

        final JsonObject tenantJsonObject = newTenantResult("tenant");
        final TenantResult<TenantObject> tenantResult = client.getResult(
                HttpURLConnection.HTTP_OK, "application/json", tenantJsonObject.toBuffer(), null, null);

        when(cache.getIfPresent(any())).thenReturn(tenantResult);

        // WHEN getting tenant information
        client.get("tenant", span.context()).onComplete(ctx.succeeding(result -> {
            ctx.verify(() -> {
                // THEN the tenant information is read from the cache
                verify(cache).getIfPresent(any());
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
        givenAClient(cache);
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
     * Verifies that the client completes a request with the result of
     * another pending request.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantUsesResultFromPendingRequest(final VertxTestContext ctx) {

        // GIVEN a client where there is no credit left
        givenAClient(cache);
        final Promise<Void> isConnectedPromise = Promise.promise();
        when(connection.isConnected(anyLong())).thenReturn(isConnectedPromise.future());
        when(sender.sendQueueFull()).thenReturn(true);

        // WHEN getting tenant information twice before the client is connected
        final Future<TenantObject> firstFuture = client.get("tenant", span.context());
        final Future<TenantObject> secondFuture = client.get("tenant", span.context());

        // THEN after connection establishment ...
        isConnectedPromise.complete();

        final Checkpoint checkpoint = ctx.checkpoint(3);
        final Handler<AsyncResult<TenantObject>> failureCheckHandler = ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(ServerErrorException.class)
                        .extracting("errorCode").isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
            });
            checkpoint.flag();
        });
        // ... both invocations fail
        firstFuture.onComplete(failureCheckHandler);
        secondFuture.onComplete(failureCheckHandler);

        // and credits were only checked once
        verify(sender).sendQueueFull();
        checkpoint.flag();
    }

    /**
     * Verifies that the client fails if the Tenant service cannot be reached
     * because the sender link has no credit left. Also verifies that a subsequent
     * client invocation succeeds if there is credit again.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantSucceedsWhenCreditRestored(final VertxTestContext ctx) {

        // GIVEN a client where there is initially no credit left
        givenAClient(cache);
        final AtomicBoolean isNoCreditLeft = new AtomicBoolean(true);
        doAnswer(invocation -> isNoCreditLeft.get())
                .when(sender).sendQueueFull();

        final Checkpoint checkpoint = ctx.checkpoint(2);

        // WHEN getting tenant information
        client.get("tenant", span.context()).onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                // THEN the invocation fails
                assertThat(t).isInstanceOf(ServerErrorException.class)
                        .extracting("errorCode").isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
            });
            checkpoint.flag();
        }));

        // AND after credits are restored a subsequent invocation to get tenant information succeeds
        isNoCreditLeft.set(false);
        final Future<TenantObject> secondGetFuture = client.get("tenant", span.context());

        final Message requestMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        final Message responseMessage = ProtonHelper.message();
        MessageHelper.addProperty(responseMessage, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(responseMessage, CacheDirective.maxAgeDirective(60));
        responseMessage.setCorrelationId(requestMessage.getMessageId());
        MessageHelper.setPayload(responseMessage, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, newTenantResult("tenant").toBuffer());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(delivery, responseMessage);

        secondGetFuture.onComplete(ctx.succeeding(v -> {
            checkpoint.flag();
        }));
    }

    /**
     * Verifies that the client includes the required information in the request
     * message sent to the Tenant service.
     */
    @Test
    public void testGetTenantByCaUsesRFC2253SubjectDn() {

        // GIVEN a client
        givenAClient(cache);

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
