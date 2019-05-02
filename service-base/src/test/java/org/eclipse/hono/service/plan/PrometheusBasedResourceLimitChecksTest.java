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

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
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
import java.time.LocalDate;

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;

/**
 * Verifies the behavior of {@link PrometheusBasedResourceLimitChecks}.
 */
@RunWith(VertxUnitRunner.class)
public class PrometheusBasedResourceLimitChecksTest {

    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "localhost";
    /**
     * Time out each test after five seconds.
     */
    @Rule
    public final Timeout timeout = Timeout.seconds(5);

    private PrometheusBasedResourceLimitChecks limitChecksImpl;
    private WebClient webClient;
    private HttpRequest<Buffer> request;
    private CacheProvider cacheProvider;
    private ExpiringValueCache limitsCache;


    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
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
    public void testConnectionLimitIsNotReached(final TestContext ctx) {

        givenCurrentConnections(9);
        final JsonObject limitsConfig = new JsonObject()
                .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_CONNECTIONS, 10);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setProperty(TenantConstants.FIELD_RESOURCE_LIMITS, limitsConfig);
        limitChecksImpl.isConnectionLimitReached(tenant).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertEquals(Boolean.FALSE, b);
            verify(webClient).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
        }));
    }

    /**
     * Verifies that the connection limit check returns {@code true} if the limit
     * is reached.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionLimitIsReached(final TestContext ctx) {

        givenCurrentConnections(10);
        final JsonObject limitsConfig = new JsonObject()
                .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_CONNECTIONS, 10);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setProperty(TenantConstants.FIELD_RESOURCE_LIMITS, limitsConfig);
        limitChecksImpl.isConnectionLimitReached(tenant).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertEquals(Boolean.TRUE, b);
            verify(webClient).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
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
                .put(PrometheusBasedResourceLimitChecks.FIELD_EFFECTIVE_SINCE, "2019-04-25");

        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setProperty(TenantConstants.FIELD_RESOURCE_LIMITS,
                new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME, dataVolumeConfig));

        assertThat(limitChecksImpl.getMaximumNumberOfBytes(tenant), is(20_000_000L));
        assertThat(limitChecksImpl.getPeriodInDays(tenant), is(90L));
        assertThat(limitChecksImpl.getEffectiveSince(tenant), is(LocalDate.parse("2019-04-25", ISO_LOCAL_DATE)));
    }

    /**
     * Verifies that the default value for the effective-since parameter is {@code null}.
     */
    @Test
    public void testEffectiveSinceWhenNotSet() {
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(limitChecksImpl.getEffectiveSince(tenant), is(nullValue()));
    }

    /**
     *
     * Verifies that the message limit check returns {@code false} if the limit is not exceeded.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitNotExceeded(final TestContext ctx) {

        givenDataVolumeUsageInBytes(90);
        final long incomingMessageSize = 10;
        final JsonObject limitsConfig = new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME,
                new JsonObject()
                        .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_BYTES, 100)
                        .put(PrometheusBasedResourceLimitChecks.FIELD_EFFECTIVE_SINCE, "2019-01-03")
                        .put(PrometheusBasedResourceLimitChecks.FIELD_PERIOD_IN_DAYS, 30));
        final TenantObject tenant = TenantObject.from("tenant", true).setResourceLimits(limitsConfig);

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize)
                .setHandler(ctx.asyncAssertSuccess(b -> {
                    ctx.assertEquals(Boolean.FALSE, b);
                    verify(webClient).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
                }));
    }

    /**
     * Verifies that the message limit check returns {@code true} if the limit is exceeded.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitExceeded(final TestContext ctx) {

        givenDataVolumeUsageInBytes(100);
        final long incomingMessageSize = 20;
        final JsonObject limitsConfig = new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME,
                new JsonObject()
                        .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_BYTES, 100)
                        .put(PrometheusBasedResourceLimitChecks.FIELD_EFFECTIVE_SINCE, "2019-01-03")
                        .put(PrometheusBasedResourceLimitChecks.FIELD_PERIOD_IN_DAYS, 30));
        final TenantObject tenant = TenantObject.from("tenant", true).setResourceLimits(limitsConfig);

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize)
                .setHandler(ctx.asyncAssertSuccess(b -> {
                    ctx.assertEquals(Boolean.TRUE, b);
                    verify(webClient).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
                }));
    }

    /**
     * Verifies that the message limit check returns {@code false} if the limit is not set and no call is made to
     * retrieve metrics data from the prometheus server.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitNotExceededWhenNotConfigured(final TestContext ctx) {

        final TenantObject tenant = TenantObject.from("tenant", true);
        limitChecksImpl.isMessageLimitReached(tenant, 10)
                .setHandler(ctx.asyncAssertSuccess(b -> {
                    ctx.assertEquals(Boolean.FALSE, b);
                    verify(webClient, never()).get(any(), any());
                }));
    }

    /**
     * Verifies that the consumed bytes value is taken from limitsCache.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageLimitUsesValueFromCache(final TestContext ctx) {

        when(limitsCache.get(any())).thenReturn(100L);
        final long incomingMessageSize = 20;
        final JsonObject limitsConfig = new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME,
                new JsonObject()
                        .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_BYTES, 100)
                        .put(PrometheusBasedResourceLimitChecks.FIELD_EFFECTIVE_SINCE, "2019-01-03")
                        .put(PrometheusBasedResourceLimitChecks.FIELD_PERIOD_IN_DAYS, 30));
        final TenantObject tenant = TenantObject.from("tenant", true).setResourceLimits(limitsConfig);

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize)
                .setHandler(ctx.asyncAssertSuccess(b -> {
                    ctx.assertEquals(Boolean.TRUE, b);
                    verify(webClient, never()).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
                }));
    }

    /**
     * Verifies that the metrics data retrieved from the prometheus server during message limit check
     * is saved to the limitsCache.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageLimitStoresValueToCache(final TestContext ctx){

        givenDataVolumeUsageInBytes(100);
        final long incomingMessageSize = 20;
        final JsonObject limitsConfig = new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME,
                new JsonObject()
                        .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_BYTES, 100)
                        .put(PrometheusBasedResourceLimitChecks.FIELD_EFFECTIVE_SINCE, "2019-01-03")
                        .put(PrometheusBasedResourceLimitChecks.FIELD_PERIOD_IN_DAYS, 30));
        final TenantObject tenant = TenantObject.from("tenant", true).setResourceLimits(limitsConfig);

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize)
                .setHandler(ctx.asyncAssertSuccess(b -> {
                    verify(webClient).get(eq(DEFAULT_PORT), eq(DEFAULT_HOST), anyString());
                    verify(cacheProvider.getCache(any())).put(any(), any(), any(Duration.class));
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
    private void givenDataVolumeUsageInBytes(final int consumedBytes){
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
