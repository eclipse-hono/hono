/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.time.Duration;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
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
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@link RegistrationClientImpl}.
 *
 */
@ExtendWith(VertxExtension.class)
public class RegistrationClientImplTest {

    private ProtonSender sender;
    private RegistrationClientImpl client;
    private ExpiringValueCache<Object, RegistrationResult> cache;
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
        final ProtonReceiver receiver = HonoClientUnitTestHelper.mockProtonReceiver();
        sender = HonoClientUnitTestHelper.mockProtonSender();

        cache = mock(ExpiringValueCache.class);
        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        final HonoConnection connection = HonoClientUnitTestHelper.mockHonoConnection(vertx, config, tracer);

        client = new RegistrationClientImpl(connection, "tenant", sender, receiver, SendMessageSampler.noop());
    }

    /**
     * Verifies that on a cache miss the adapter retrieves registration information
     * from the Device Registration service and puts it to the cache.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationAddsResponseToCacheOnCacheMiss(final VertxTestContext ctx) {

        final JsonObject registrationAssertion = newRegistrationAssertionResult();

        // GIVEN an adapter with an empty cache
        client.setResponseCache(cache);

        // WHEN getting registration information
        client.assertRegistration("myDevice").onComplete(ctx.succeeding(result -> {
            ctx.verify(() -> {
                // THEN the registration information has been added to the cache
                assertThat(result).isEqualTo(registrationAssertion);
                verify(cache).put(eq(TriTuple.of("assert", "myDevice", null)), any(RegistrationResult.class), any(Duration.class));
                // and the span is finished
                verify(span).finish();
            });
            ctx.completeNow();
        }));

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), VertxMockSupport.anyHandler());
        final Message response = ProtonHelper.message();
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        MessageHelper.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, registrationAssertion.toBuffer());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);
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
        final JsonObject registrationAssertion = newRegistrationAssertionResult();
        final Message response = ProtonHelper.message();
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        MessageHelper.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, registrationAssertion.toBuffer());

        // WHEN getting registration information
        client.assertRegistration("device").onComplete(ctx.succeeding(result -> {
            ctx.verify(() -> {
                // THEN the registration information has been retrieved from the service
                assertThat(result).isEqualTo(registrationAssertion);
                // and not been put to the cache
                verify(cache, never()).put(any(), any(RegistrationResult.class), any(Duration.class));
                // and the span is finished
                verify(span).finish();
            });
            ctx.completeNow();
        }));

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), VertxMockSupport.anyHandler());
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);
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
        client.setResponseCache(cache);
        final JsonObject registrationAssertion = newRegistrationAssertionResult();
        final RegistrationResult regResult = RegistrationResult.from(HttpURLConnection.HTTP_OK, registrationAssertion);
        when(cache.get(eq(TriTuple.of("assert", "device", "gateway")))).thenReturn(regResult);

        // WHEN getting registration information
        client.assertRegistration("device", "gateway").onComplete(ctx.succeeding(result -> {
            // THEN the registration information is read from the cache
            assertEquals(registrationAssertion, result);
            // and no request message is sent to the service
            verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
            // and the span is finished
            verify(span).finish();
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

        // WHEN getting registration information
        client.assertRegistration("device", "gateway");

        // THEN the message being sent contains the device ID and the gateway ID
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), VertxMockSupport.anyHandler());
        final Message sentMessage = messageCaptor.getValue();
        assertThat(MessageHelper.getDeviceId(sentMessage)).isEqualTo("device");
        assertThat(
                MessageHelper.getApplicationProperty(
                        sentMessage.getApplicationProperties(),
                        MessageHelper.APP_PROPERTY_GATEWAY_ID,
                        String.class))
                .isEqualTo("gateway");
    }

    private static JsonObject newRegistrationAssertionResult() {
        return newRegistrationAssertionResult(null);
    }

    private static JsonObject newRegistrationAssertionResult(final String defaultContentType) {

        final JsonObject result = new JsonObject();
        if (defaultContentType != null) {
            result.put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, new JsonObject()
                    .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, defaultContentType));
        }
        return result;
    }

}
