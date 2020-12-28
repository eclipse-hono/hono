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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.client.amqp.AmqpClientUnitTestHelper;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.TriTuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;
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
    private ExpiringValueCache<Object, Object> cache;
    private CacheProvider cacheProvider;
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

        cache = mock(ExpiringValueCache.class);
        cacheProvider = mock(CacheProvider.class);
        when(cacheProvider.getCache(anyString())).thenReturn(cache);
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

    private void givenAClient(final CacheProvider cacheProvider) {
        client = new ProtonBasedDeviceRegistrationClient(
                connection,
                SendMessageSampler.Factory.noop(),
                new ProtocolAdapterProperties(),
                cacheProvider);
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
        givenAClient(cacheProvider);
        when(cache.get(any())).thenReturn(null);

        // WHEN getting registration information
        client.assertRegistration("tenant", "myDevice", null, span.context()).onComplete(ctx.succeeding(result -> {
            ctx.verify(() -> {
                // THEN the registration information has been added to the cache
                final var responseCacheKey = ArgumentCaptor.forClass(TriTuple.class);
                verify(cache).get(responseCacheKey.capture());
                assertThat(result.getDeviceId()).isEqualTo("myDevice");
                assertThat(result.getAuthorizedGateways()).isEmpty();
                verify(cache).put(
                        eq(responseCacheKey.getValue()),
                        argThat((RegistrationResult response) -> registrationAssertion.equals(response.getPayload())),
                        any(Duration.class));
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
        MessageHelper.setJsonPayload(response, registrationAssertion);
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
                verify(cache, never()).get(any());
                assertThat(result.getDeviceId()).isEqualTo("device");
                // and not been put to the cache
                verify(cache, never()).put(any(), any(RegistrationResult.class), any(Duration.class));
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
        MessageHelper.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, registrationAssertion.toBuffer());
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
        givenAClient(cacheProvider);
        final JsonObject registrationAssertion = newRegistrationAssertionResult("device");
        final RegistrationResult regResult = RegistrationResult.from(HttpURLConnection.HTTP_OK, registrationAssertion);
        when(cache.get(any())).thenReturn(regResult);

        // WHEN getting registration information
        client.assertRegistration("tenant", "device", "gateway", span.context())
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    // THEN the registration information is read from the cache
                    verify(cache).get(any());
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
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo("device");
        assertThat(
                MessageHelper.getApplicationProperty(
                        sentMessage.getApplicationProperties(),
                        MessageHelper.APP_PROPERTY_GATEWAY_ID,
                        String.class))
                .isEqualTo("gateway");
    }

}
