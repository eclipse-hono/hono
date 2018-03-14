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
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.time.Duration;

import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.eclipse.hono.util.TriTuple;
import org.eclipse.hono.util.TenantConstants.TenantAction;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

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
 * Tests verifying behavior of {@link TenantClientImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class TenantClientImplTest {

    /**
     * Time out test cases after 5 seconds.
     */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(5);

    private Vertx vertx;
    private Context context;
    private ProtonSender sender;
    private TenantClientImpl client;
    private ExpiringValueCache<Object, TenantResult<TenantObject>> cache;

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
        client = new TenantClientImpl(context, config, sender, receiver);
    }

    /**
     * Verifies that the client includes the required information in the request
     * message sent to the Tenant service.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetTenantInvokesServiceIfNoCacheConfigured(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN getting tenant information by ID
        client.get("tenant");

        // THEN the message being sent contains the tenant ID as search criteria
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message sentMessage = messageCaptor.getValue();
        assertNull(MessageHelper.getTenantId(sentMessage));
        assertThat(sentMessage.getSubject(), is(TenantConstants.TenantAction.get.toString()));
        assertThat(MessageHelper.getJsonPayload(sentMessage).getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID), is("tenant"));
    }

    /**
     * Verifies that on a cache miss the adapter retrieves tenant information
     * from the Tenant service and puts it to the cache.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetTenantAddsInfoToCacheOnCacheMiss(final TestContext ctx) {

        // GIVEN an adapter with an empty cache
        client.setResponseCache(cache);
        final JsonObject tenantResult = newTenantResult("tenant");

        // WHEN getting tenant information
        final Async get = ctx.async();
        client.get("tenant").setHandler(ctx.asyncAssertSuccess(tenant -> get.complete()));

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message response = ProtonHelper.message(tenantResult.encode());
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(60));
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);

        // THEN the tenant result has been added to the cache
        get.await();
        verify(cache).put(eq(TriTuple.of(TenantAction.get, "tenant", null)), any(TenantResult.class), any(Duration.class));
    }

    /**
     * Verifies that tenant information is taken from cache if cache is configured and the cache has this tenant
     * information cached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantReturnsValueFromCache(final TestContext ctx) {

        // GIVEN an adapter with a cache containing a tenant
        client.setResponseCache(cache);

        final JsonObject tenantJsonObject = newTenantResult("tenant");
        final TenantResult<TenantObject> tenantResult = client.getResult(HttpURLConnection.HTTP_OK, tenantJsonObject.toString(), null);

        when(cache.get(any(TriTuple.class))).thenReturn(tenantResult);

        // WHEN getting tenant information
        client.get("tenant").setHandler(ctx.asyncAssertSuccess(result -> {
            // THEN the tenant information is read from the cache
            ctx.assertEquals(tenantResult.getPayload(), result);
            verify(sender, never()).send(any(Message.class));
        }));

    }

    /**
     * Verifies that the client includes the required information in the request
     * message sent to the Tenant service.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetTenantByCaUsesRFC2253SubjectDn(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN getting tenant information for a subject DN
        final X500Principal dn = new X500Principal("CN=ca, OU=Hono, O=Eclipse");
        client.get(dn);

        // THEN the message being sent contains the subject DN in RFC 2253 format in the
        // payload
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message sentMessage = messageCaptor.getValue();
        final JsonObject payload = MessageHelper.getJsonPayload(sentMessage);
        assertThat(payload.getString(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN), is("CN=ca,OU=Hono,O=Eclipse"));
    }

    /**
     * Verifies that the client uses the correct message id prefix defined for the tenant client.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageIdPrefixStartsWithApiSpecificPrefix(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN getting tenant information
        client.get("tenant");

        // THEN the message being sent uses the correct message id prefix
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getMessageId().toString(), startsWith(TenantConstants.MESSAGE_ID_PREFIX));
    }

    private JsonObject newTenantResult(final String tenantId) {

        final JsonObject returnObject = new JsonObject().
                put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId).
                put(TenantConstants.FIELD_ENABLED, true);
        return returnObject;
    }
}
