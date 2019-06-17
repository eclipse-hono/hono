/**
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
 */
package org.eclipse.hono.service.plan;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies the behavior of {@link PrometheusBasedResourceLimitChecks}.
 */
@ExtendWith(VertxExtension.class)
public class PrometheusBasedResourceLimitChecksTest {

    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "localhost";

    private PrometheusBasedResourceLimitChecks limitChecksImpl;
    private WebClient webClient;
    private HttpRequest<Buffer> request;
    private CacheProvider cacheProvider;
    private ExpiringValueCache<Object, Object> limitsCache;


    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {

        request = mock(HttpRequest.class);
        when(request.addQueryParam(anyString(), anyString())).thenReturn(request);
        when(request.expect(any(ResponsePredicate.class))).thenReturn(request);
        when(request.as(any(BodyCodec.class))).thenReturn(request);

        webClient = mock(WebClient.class);
        when(webClient.get(anyInt(), anyString(), anyString())).thenReturn(request);

        limitsCache = mock(ExpiringValueCache.class);
        cacheProvider = mock(CacheProvider.class);
        when(cacheProvider.getCache(any())).thenReturn(limitsCache);

        final PrometheusBasedResourceLimitChecksConfig config = new PrometheusBasedResourceLimitChecksConfig();
        config.setHost(DEFAULT_HOST);
        config.setPort(DEFAULT_PORT);

        limitChecksImpl = new PrometheusBasedResourceLimitChecks(webClient, config, cacheProvider);
    }

    /**
     * Verifies that the connection limit check returns {@code false} if the limit
     * is not yet reached.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionLimitIsNotReached(final VertxTestContext ctx) {

        givenCurrentConnections(9);
        final JsonObject limitsConfig = new JsonObject()
                .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_CONNECTIONS, 10);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setProperty(TenantConstants.FIELD_RESOURCE_LIMITS, limitsConfig);

        limitChecksImpl.isConnectionLimitReached(tenant).setHandler(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(webClient).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the connection limit check returns {@code true} if the limit
     * is reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionLimitIsReached(final VertxTestContext ctx) {

        givenCurrentConnections(10);
        final JsonObject limitsConfig = new JsonObject()
                .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_CONNECTIONS, 10);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setProperty(TenantConstants.FIELD_RESOURCE_LIMITS, limitsConfig);

        limitChecksImpl.isConnectionLimitReached(tenant).setHandler(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response);
                        verify(webClient).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the default value for connection limit is used when
     * no specific limits have been set for a tenant.
     */
    @Test
    public void testGetConnectionsLimitDefaultValue() {
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(limitChecksImpl.getConnectionsLimit(tenant),
                is(PrometheusBasedResourceLimitChecks.DEFAULT_MAX_CONNECTIONS));
    }

    /**
     * Verifies that the default max-bytes is used when
     * no specific limits have been set for a tenant.
     */
    @Test
    public void testGetMaxBytesLimitDefaultValue() {
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(limitChecksImpl.getMaximumNumberOfBytes(tenant),
                is(PrometheusBasedResourceLimitChecks.DEFAULT_MAX_BYTES));
    }

    /**
     * Verifies that the default period of days is used when
     * no specific limits have been set for a tenant.
     */
    @Test
    public void testGetPeriodInDaysDefaultValue() {
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(limitChecksImpl.getPeriodInDays(tenant),
                is(PrometheusBasedResourceLimitChecks.DEFAULT_PERIOD_IN_DAYS));
    }

    /**
     * Verifies that the connection limit is checked based on the value
     * specified for a tenant.
     */
    @Test
    public void testGetConnectionsLimit() {
        final JsonObject limitsConfig = new JsonObject()
                .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_CONNECTIONS, 2);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setProperty(TenantConstants.FIELD_RESOURCE_LIMITS, limitsConfig);
        assertThat(limitChecksImpl.getConnectionsLimit(tenant), is(2L));
    }

    /**
     * Verifies that the data volume limit is checked based on the values
     * specified for a tenant.
     */
    @Test
    public void testGetMaxBytesLimit() {

        final JsonObject dataVolumeConfig = new JsonObject()
                .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_BYTES, 20_000_000)
                .put(PrometheusBasedResourceLimitChecks.FIELD_PERIOD_IN_DAYS, 90)
                .put(PrometheusBasedResourceLimitChecks.FIELD_EFFECTIVE_SINCE, "2019-04-25T14:30Z");

        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setProperty(TenantConstants.FIELD_RESOURCE_LIMITS,
                new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME, dataVolumeConfig));

        assertThat(limitChecksImpl.getMaximumNumberOfBytes(tenant), is(20_000_000L));
        assertThat(limitChecksImpl.getPeriodInDays(tenant), is(90L));
        assertThat(limitChecksImpl.getEffectiveSince(tenant), is(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2019-04-25T14:30Z", OffsetDateTime::from).toInstant()));
    }

    /**
     * Verifies that the default value for the effective-since parameter is {@code null}.
     */
    @Test
    public void testEffectiveSinceWhenNotSet() {
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertNull(limitChecksImpl.getEffectiveSince(tenant));
    }

    /**
     *
     * Verifies that the message limit check returns {@code false} if the limit is not exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitNotExceeded(final VertxTestContext ctx) {

        givenDataVolumeUsageInBytes(90);
        final long incomingMessageSize = 10;
        final JsonObject limitsConfig = new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME,
                new JsonObject()
                        .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_BYTES, 100)
                        .put(PrometheusBasedResourceLimitChecks.FIELD_EFFECTIVE_SINCE, "2019-01-03T14:30Z")
                        .put(PrometheusBasedResourceLimitChecks.FIELD_PERIOD_IN_DAYS, 30));
        final TenantObject tenant = TenantObject.from("tenant", true).setResourceLimits(limitsConfig);

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize)
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(webClient).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the message limit check returns {@code true} if the limit is exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitExceeded(final VertxTestContext ctx) {

        givenDataVolumeUsageInBytes(100);
        final long incomingMessageSize = 20;
        final JsonObject limitsConfig = new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME,
                new JsonObject()
                        .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_BYTES, 100)
                        .put(PrometheusBasedResourceLimitChecks.FIELD_EFFECTIVE_SINCE, "2019-01-03T14:30Z")
                        .put(PrometheusBasedResourceLimitChecks.FIELD_PERIOD_IN_DAYS, 30));
        final TenantObject tenant = TenantObject.from("tenant", true).setResourceLimits(limitsConfig);

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize)
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response);
                        verify(webClient).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the message limit check returns {@code false} if the limit is not set and no call is made to
     * retrieve metrics data from the prometheus server.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitNotExceededWhenNotConfigured(final VertxTestContext ctx) {

        final TenantObject tenant = TenantObject.from("tenant", true);

        limitChecksImpl.isMessageLimitReached(tenant, 10)
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(webClient, never()).get(any(), any());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the consumed bytes value is taken from limitsCache.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitUsesValueFromCache(final VertxTestContext ctx) {

        when(limitsCache.get(any())).thenReturn(100L);
        final long incomingMessageSize = 20;
        final JsonObject limitsConfig = new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME,
                new JsonObject()
                        .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_BYTES, 100)
                        .put(PrometheusBasedResourceLimitChecks.FIELD_EFFECTIVE_SINCE, "2019-01-03T14:30Z")
                        .put(PrometheusBasedResourceLimitChecks.FIELD_PERIOD_IN_DAYS, 30));
        final TenantObject tenant = TenantObject.from("tenant", true).setResourceLimits(limitsConfig);

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize)
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response);
                        verify(webClient, never()).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the metrics data retrieved from the prometheus server during message limit check
     * is saved to the limitsCache.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitStoresValueToCache(final VertxTestContext ctx) {

        givenDataVolumeUsageInBytes(100);
        final long incomingMessageSize = 20;
        final JsonObject limitsConfig = new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME,
                new JsonObject()
                        .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_BYTES, 100)
                        .put(PrometheusBasedResourceLimitChecks.FIELD_EFFECTIVE_SINCE, "2019-01-03T14:30Z")
                        .put(PrometheusBasedResourceLimitChecks.FIELD_PERIOD_IN_DAYS, 30));
        final TenantObject tenant = TenantObject.from("tenant", true).setResourceLimits(limitsConfig);

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize)
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        verify(webClient).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
                        verify(cacheProvider.getCache(any())).put(any(), any(), any(Duration.class));
                    });
                    ctx.completeNow();
                }));
    }

    @SuppressWarnings("unchecked")
    private void givenCurrentConnections(final int currentConnections) {
        doAnswer(invocation -> {
            final Handler<AsyncResult<HttpResponse<JsonObject>>> responseHandler = invocation.getArgument(0);
            final HttpResponse<JsonObject> response = mock(HttpResponse.class);
            when(response.body()).thenReturn(createPrometheusResponse(currentConnections));
            responseHandler.handle(Future.succeededFuture(response));
            return null;
        }).when(request).send(any(Handler.class));
    }

    @SuppressWarnings("unchecked")
    private void givenDataVolumeUsageInBytes(final int consumedBytes) {
        doAnswer(invocation -> {
            final Handler<AsyncResult<HttpResponse<JsonObject>>> responseHandler = invocation.getArgument(0);
            final HttpResponse<JsonObject> response = mock(HttpResponse.class);
            when(response.body()).thenReturn(createPrometheusResponse(consumedBytes));
            responseHandler.handle(Future.succeededFuture(response));
            return null;
        }).when(request).send(any(Handler.class));
    }

    private JsonObject createPrometheusResponse(final int connections) {
        return new JsonObject()
                .put("status", "success")
                .put("data", new JsonObject()
                        .put("result", new JsonArray().add(new JsonObject()
                                .put("value", new JsonArray().add("timestamp").add(String.valueOf(connections))))));
    }
}
