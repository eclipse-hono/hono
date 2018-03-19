/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.client.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.TriTuple;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@link RegistrationClientImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class RegistrationClientImplTest {

    private Vertx vertx;
    private Context context;
    private ProtonSender sender;

    /**
     * Time out test cases after 5 seconds.
     */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(5);

    private RegistrationClientImpl client;
    private ExpiringValueCache<Object, RegistrationResult> cache;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        vertx = mock(Vertx.class);
        context = HonoClientUnitTestHelper.mockContext(vertx);
        final ProtonReceiver receiver = HonoClientUnitTestHelper.mockProtonReceiver();
        sender = HonoClientUnitTestHelper.mockProtonSender();

        cache = mock(ExpiringValueCache.class);
        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        client = new RegistrationClientImpl(context, config, "tenant", sender, receiver);
    }

    /**
     * Verifies that on a cache miss the adapter retrieves registration information
     * from the Device Registration service and puts it to the cache.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testAssertRegistrationAddsResponseToCacheOnCacheMiss(final TestContext ctx) {

        // GIVEN an adapter with an empty cache
        client.setResponseCache(cache);

        // WHEN getting registration information
        final Async assertion = ctx.async();
        client.assertRegistration("device").setHandler(ctx.asyncAssertSuccess(result -> assertion.complete()));

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final JsonObject registrationAssertion = newRegistrationAssertionResult();
        final Message response = ProtonHelper.message(registrationAssertion.encode());
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);

        // THEN the registration information has been added to the cache
        verify(cache).put(eq(TriTuple.of("assert", "device", null)), any(RegistrationResult.class), any(Duration.class));
    }

    /**
     * Verifies that the client retrieves registration information from the
     * Device Registration service if no cache is configured.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testAssertRegistrationInvokesServiceIfNoCacheConfigured(final TestContext ctx) {

        // GIVEN an adapter with no cache configured
        final JsonObject registrationAssertion = newRegistrationAssertionResult();
        final Message response = ProtonHelper.message(registrationAssertion.encode());
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);

        // WHEN getting registration information
        final Async assertion = ctx.async();
        client.assertRegistration("device").setHandler(ctx.asyncAssertSuccess(result -> assertion.complete()));

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);

        // THEN the registration information has been retrieved from the service
        assertion.await();
        // and not been put to the cache
        verify(cache, never()).put(any(), any(RegistrationResult.class), any(Duration.class));
    }

    /**
     * Verifies that registration information is taken from cache.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetRegistrationInfoReturnsValueFromCache(final TestContext ctx) {

        // GIVEN an adapter with a cache containing a registration assertion
        // response for "device"
        client.setResponseCache(cache);
        final JsonObject registrationAssertion = newRegistrationAssertionResult();
        final RegistrationResult regResult = RegistrationResult.from(HttpURLConnection.HTTP_OK, registrationAssertion);
        when(cache.get(eq(TriTuple.of("assert", "device", "gateway")))).thenReturn(regResult);

        // WHEN getting registration information
        client.assertRegistration("device", "gateway").setHandler(ctx.asyncAssertSuccess(result -> {
            // THEN the registration information is read from the cache
            ctx.assertEquals(registrationAssertion, result);
            verify(sender, never()).send(any(Message.class));
        }));

    }

    /**
     * Verifies that the client includes the required information in the request
     * message sent to the Device Registration service.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetRegistrationInfoIncludesRequiredParamsInRequest(final TestContext ctx) {

        // GIVEN an adapter without a cache

        // WHEN getting registration information
        client.assertRegistration("device", "gateway");

        // THEN the message being sent contains the device ID and the gateway ID
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message sentMessage = messageCaptor.getValue();
        assertThat(MessageHelper.getDeviceId(sentMessage), is("device"));
        assertThat(
                MessageHelper.getApplicationProperty(
                        sentMessage.getApplicationProperties(),
                        MessageHelper.APP_PROPERTY_GATEWAY_ID,
                        String.class),
                is("gateway"));
    }

    private static JsonObject newRegistrationAssertionResult() {
        return newRegistrationAssertionResult(null);
    }

    private static JsonObject newRegistrationAssertionResult(final String defaultContentType) {

        final String token = Jwts.builder()
            .signWith(SignatureAlgorithm.HS256, "asecretkeywithatleastthirtytwobytes")
            .setExpiration(Date.from(Instant.now().plusSeconds(10)))
            .setIssuer("test")
            .compact();
        final JsonObject result = new JsonObject().put(RegistrationConstants.FIELD_ASSERTION, token);
        if (defaultContentType != null) {
            result.put(RegistrationConstants.FIELD_DEFAULTS, new JsonObject()
                    .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, defaultContentType));
        }
        return result;
    }

}
