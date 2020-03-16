/**
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.resourcelimits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.util.ConnectionDuration;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DataVolume;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.ResourceLimitsPeriod;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.log.Fields;
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

    private PrometheusBasedResourceLimitChecks limitChecksImpl;
    private WebClient webClient;
    private HttpRequest<JsonObject> request;
    private CacheProvider cacheProvider;
    private ExpiringValueCache<Object, Object> limitsCache;
    private SpanContext spanContext;
    private Span span;
    private Tracer tracer;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {

        request = mock(HttpRequest.class);
        final HttpRequest<Buffer> req = mock(HttpRequest.class);
        when(req.addQueryParam(anyString(), anyString())).thenReturn(req);
        when(req.expect(any(ResponsePredicate.class))).thenReturn(req);
        when(req.as(any(BodyCodec.class))).thenReturn(request);
        when(request.basicAuthentication(anyString(), anyString())).thenReturn(request);

        webClient = mock(WebClient.class);
        when(webClient.get(anyInt(), anyString(), anyString())).thenReturn(req);

        limitsCache = mock(ExpiringValueCache.class);
        cacheProvider = mock(CacheProvider.class);
        when(cacheProvider.getCache(any())).thenReturn(limitsCache);

        spanContext = mock(SpanContext.class);

        span = mock(Span.class);
        when(span.context()).thenReturn(spanContext);

        final SpanBuilder builder = mock(SpanBuilder.class, withSettings().defaultAnswer(RETURNS_SELF));
        when(builder.start()).thenReturn(span);

        tracer = mock(Tracer.class);
        when(tracer.buildSpan(anyString())).thenReturn(builder);

        final PrometheusBasedResourceLimitChecksConfig config = new PrometheusBasedResourceLimitChecksConfig();

        limitChecksImpl = new PrometheusBasedResourceLimitChecks(webClient, config, cacheProvider, tracer);
    }

    /**
     * Verifies that the Basic authentication header is set if username and password have been
     * configured.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteQuerySetsAuthHeader(final VertxTestContext ctx) {

        final PrometheusBasedResourceLimitChecksConfig config = new PrometheusBasedResourceLimitChecksConfig();
        config.setUsername("hono");
        config.setPassword("hono-secret");

        limitChecksImpl = new PrometheusBasedResourceLimitChecks(webClient, config, cacheProvider, tracer);

        givenCurrentConnections(0);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits().setMaxConnections(10));

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).setHandler(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(request).basicAuthentication(eq("hono"), eq("hono-secret"));
                        verify(request).send(any(Handler.class));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the connection limit check returns {@code false} if the limit
     * is not yet reached.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectionLimitIsNotReached(final VertxTestContext ctx) {

        givenCurrentConnections(9);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits().setMaxConnections(10));

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).setHandler(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(request).send(any(Handler.class));
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
    @SuppressWarnings("unchecked")
    public void testConnectionLimitIsReached(final VertxTestContext ctx) {

        givenCurrentConnections(10);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits().setMaxConnections(10));

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).setHandler(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response);
                        verify(request).send(any(Handler.class));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     *
     * Verifies that the message limit check returns {@code false} if the limit is not exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageLimitNotExceeded(final VertxTestContext ctx) {

        givenDataVolumeUsageInBytes(90);
        final long incomingMessageSize = 10;
        final TenantObject tenant = TenantObject.from("tenant", true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume()
                                .setMaxBytes(100L)
                                .setEffectiveSince(Instant.parse("2019-01-03T14:30:00Z"))
                                .setPeriod(new ResourceLimitsPeriod()
                                        .setMode("days")
                                        .setNoOfDays(30))));

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, mock(SpanContext.class))
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(request).send(any(Handler.class));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the message limit check returns {@code true} if the limit is exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageLimitExceeded(final VertxTestContext ctx) {

        givenDataVolumeUsageInBytes(100);
        final long incomingMessageSize = 20;
        final TenantObject tenant = TenantObject.from("tenant", true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume()
                                .setMaxBytes(100L)
                                .setEffectiveSince(Instant.parse("2019-01-03T14:30:00Z"))
                                .setPeriod(new ResourceLimitsPeriod()
                                        .setMode("days")
                                        .setNoOfDays(30))));

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, mock(SpanContext.class))
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response);
                        verify(request).send(any(Handler.class));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the message limit check returns {@code false} if no metrics are
     * available (yet).
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageLimitNotExceededForMissingMetrics(final VertxTestContext ctx) {

        givenDataVolumeUsageInBytes(null);
        final long incomingMessageSize = 20;
        final TenantObject tenant = TenantObject.from("tenant", true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume()
                                .setMaxBytes(100L)
                                .setEffectiveSince(Instant.parse("2019-01-03T14:30:00Z"))
                                .setPeriod(new ResourceLimitsPeriod()
                                        .setMode("days")
                                        .setNoOfDays(30))));

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, spanContext)
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        // THEN the limit is not exceeded
                        assertFalse(response);
                        verify(request).send(any(Handler.class));
                        // AND the span is not marked as erroneous
                        verify(span).log(argThat((Map<String, ?> map) -> !"error".equals(map.get(Fields.EVENT))));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies the effective resource limit calculation for various scenarios.
     *
     */
    @Test
    public void verifyEffectiveResourceLimitCalculation() {
        final long maxBytes = 9300;

        // Monthly mode
        // The case where the effectiveSince lies on the past months of the target date.
        assertEquals(maxBytes,
                limitChecksImpl.calculateEffectiveLimit(
                        OffsetDateTime.parse("2019-08-06T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        OffsetDateTime.parse("2019-09-06T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        PrometheusBasedResourceLimitChecks.PeriodMode.MONTHLY,
                        maxBytes));
        // The case where the effectiveSince lies on the the same month as of the target date 
        // and first day of the month.
        assertEquals(9300,
                limitChecksImpl.calculateEffectiveLimit(
                        OffsetDateTime.parse("2019-09-01T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        OffsetDateTime.parse("2019-09-06T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        PrometheusBasedResourceLimitChecks.PeriodMode.MONTHLY,
                        maxBytes));
        // The case where the effectiveSince lies on the the same month as of the target date
        // and not on the first day of the month.
        assertEquals(8990,
                limitChecksImpl.calculateEffectiveLimit(
                        OffsetDateTime.parse("2019-09-02T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        OffsetDateTime.parse("2019-09-06T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        PrometheusBasedResourceLimitChecks.PeriodMode.MONTHLY,
                        maxBytes));

        // Days mode
        assertEquals(maxBytes,
                limitChecksImpl.calculateEffectiveLimit(
                        OffsetDateTime.parse("2019-09-02T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        OffsetDateTime.parse("2019-09-06T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        PrometheusBasedResourceLimitChecks.PeriodMode.DAYS,
                        maxBytes));
    }

    /**
     * Verifies the resource usage period calculation for various scenarios.
     *
     */
    @Test
    public void verifyResourceUsagePeriodCalculation() {
        final long noOfDays = 30;
        // Monthly mode
        // The case where the effectiveSince lies on the past months of the target date.
        assertEquals(6,
                limitChecksImpl.calculateResourceUsagePeriod(
                        OffsetDateTime.parse("2019-08-06T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        OffsetDateTime.parse("2019-09-06T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        PrometheusBasedResourceLimitChecks.PeriodMode.MONTHLY,
                        noOfDays));
        // The case where the effectiveSince lies on the the same month as of the target date.
        assertEquals(5,
                limitChecksImpl.calculateResourceUsagePeriod(
                        OffsetDateTime.parse("2019-09-06T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        OffsetDateTime.parse("2019-09-10T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        PrometheusBasedResourceLimitChecks.PeriodMode.MONTHLY,
                        noOfDays));

        // Days mode
        // The case where the effectiveSince lies on the past months of the target date.
        assertEquals(6,
                limitChecksImpl.calculateResourceUsagePeriod(
                        OffsetDateTime.parse("2019-08-06T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        OffsetDateTime.parse("2019-09-10T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        PrometheusBasedResourceLimitChecks.PeriodMode.DAYS,
                        noOfDays));
        // The case where the effectiveSince lies on the the same month as of the target date.
        assertEquals(5,
                limitChecksImpl.calculateResourceUsagePeriod(
                        OffsetDateTime.parse("2019-09-06T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        OffsetDateTime.parse("2019-09-10T14:30:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        PrometheusBasedResourceLimitChecks.PeriodMode.DAYS,
                        noOfDays));
    }

    /**
     * Verifies that the message limit check returns {@code false} if the limit is not set and no call is made to
     * retrieve metrics data from the prometheus server.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageLimitNotExceededWhenNotConfigured(final VertxTestContext ctx) {

        final TenantObject tenant = TenantObject.from("tenant", true);

        limitChecksImpl.isMessageLimitReached(tenant, 10, mock(SpanContext.class))
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(request, never()).send(any(Handler.class));
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
    @SuppressWarnings("unchecked")
    public void testMessageLimitUsesValueFromCache(final VertxTestContext ctx) {

        when(limitsCache.get(any())).thenReturn(100L);
        final long incomingMessageSize = 20;
        final TenantObject tenant = TenantObject.from("tenant", true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume()
                                .setMaxBytes(100L)
                                .setEffectiveSince(Instant.parse("2019-01-03T14:30:00Z"))
                                .setPeriod(new ResourceLimitsPeriod()
                                        .setMode("days")
                                        .setNoOfDays(30))));

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, spanContext)
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response);
                        verify(request, never()).send(any(Handler.class));
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
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageLimitStoresValueToCache(final VertxTestContext ctx) {

        givenDataVolumeUsageInBytes(100);
        final long incomingMessageSize = 20;
        final TenantObject tenant = TenantObject.from("tenant", true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume()
                                .setMaxBytes(100L)
                                .setEffectiveSince(Instant.parse("2019-01-03T14:30:00Z"))
                                .setPeriod(new ResourceLimitsPeriod()
                                        .setMode("days")
                                        .setNoOfDays(30))));

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, mock(SpanContext.class))
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        verify(request).send(any(Handler.class));
                        verify(cacheProvider.getCache(any())).put(eq("tenant_bytes_consumed"), any(),
                                any(Duration.class));
                        verify(cacheProvider.getCache(any())).put(eq("tenant_allowed_max_bytes"), any(),
                                any(Duration.class));
                        verify(cacheProvider.getCache(any())).put(eq("tenant_data_usage_period"), any(),
                                any(Duration.class));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     *
     * Verifies that the connection duration limit check returns {@code false} if the limit is not exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectionDurationLimitNotExceeded(final VertxTestContext ctx) {

        givenDeviceConnectionDurationInMinutes(90);
        final TenantObject tenant = TenantObject.from("tenant", true)
                .setResourceLimits(new ResourceLimits()
                        .setConnectionDuration(new ConnectionDuration()
                                .setMaxDuration(100L)
                                .setEffectiveSince(Instant.parse("2019-01-03T14:30:00Z"))
                                .setPeriod(new ResourceLimitsPeriod()
                                        .setMode("days")
                                        .setNoOfDays(30))));
        limitChecksImpl.isConnectionDurationLimitReached(tenant, mock(SpanContext.class))
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(request).send(any(Handler.class));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     *
     * Verifies that the connection duration limit check returns {@code true} if the limit is exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectionDurationLimitExceeded(final VertxTestContext ctx) {

        givenDeviceConnectionDurationInMinutes(100);
        final TenantObject tenant = TenantObject.from("tenant", true)
                .setResourceLimits(new ResourceLimits()
                        .setConnectionDuration(new ConnectionDuration()
                                .setMaxDuration(100L)
                                .setEffectiveSince(Instant.parse("2019-01-03T14:30:00Z"))
                                .setPeriod(new ResourceLimitsPeriod()
                                        .setMode("days")
                                        .setNoOfDays(30))));
        limitChecksImpl.isConnectionDurationLimitReached(tenant, mock(SpanContext.class))
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response);
                        verify(request).send(any(Handler.class));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the connection duration limit check returns {@code false} if no metrics are
     * available (yet).
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectionDurationLimitNotExceededForMissingMetrics(final VertxTestContext ctx) {

        givenDeviceConnectionDurationInMinutes(null);
        final TenantObject tenant = TenantObject.from("tenant", true)
                .setResourceLimits(new ResourceLimits()
                        .setConnectionDuration(new ConnectionDuration()
                                .setMaxDuration(100L)
                                .setEffectiveSince(Instant.parse("2019-01-03T14:30:00Z"))
                                .setPeriod(new ResourceLimitsPeriod()
                                        .setMode("days")
                                        .setNoOfDays(30))));
        limitChecksImpl.isConnectionDurationLimitReached(tenant, mock(SpanContext.class))
                .setHandler(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        // THEN the limit is not exceeded
                        assertFalse(response);
                        verify(request).send(any(Handler.class));
                        // AND the span is not marked as erroneous
                        verify(span).log(argThat((Map<String, ?> map) -> !"error".equals(map.get(Fields.EVENT))));
                    });
                    ctx.completeNow();
                }));
    }

    private void givenCurrentConnections(final Integer currentConnections) {
        givenResponseWithValue(currentConnections);
    }

    private void givenDataVolumeUsageInBytes(final Integer consumedBytes) {
        givenResponseWithValue(consumedBytes);
    }

    private void givenDeviceConnectionDurationInMinutes(final Integer consumedMinutes) {
        givenResponseWithValue(consumedMinutes);
    }

    @SuppressWarnings("unchecked")
    private void givenResponseWithValue(final Integer value) {
        doAnswer(invocation -> {
            final Handler<AsyncResult<HttpResponse<JsonObject>>> responseHandler = invocation.getArgument(0);
            final HttpResponse<JsonObject> response = mock(HttpResponse.class);
            when(response.body()).thenReturn(createPrometheusResponse(value));
            responseHandler.handle(Future.succeededFuture(response));
            return null;
        }).when(request).send(any(Handler.class));
    }

    private JsonObject createPrometheusResponse(final Integer value) {
        final JsonArray valueArray = new JsonArray();
        if (value != null) {
            valueArray.add("timestamp").add(String.valueOf(value));
        }
        return new JsonObject()
                .put("status", "success")
                .put("data", new JsonObject()
                        .put("result", new JsonArray().add(new JsonObject()
                                .put("value", valueArray))));
    }
}
