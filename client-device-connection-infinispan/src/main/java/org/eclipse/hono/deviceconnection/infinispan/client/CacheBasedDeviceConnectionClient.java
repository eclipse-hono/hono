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

package org.eclipse.hono.deviceconnection.infinispan.client;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing device connection information in a data grid.
 */
public final class CacheBasedDeviceConnectionClient implements DeviceConnectionClient {

    private static final String SPAN_NAME_GET_LAST_GATEWAY = "get last known gateway";
    private static final String SPAN_NAME_SET_LAST_GATEWAY = "set last known gateway";
    private static final String SPAN_NAME_GET_CMD_HANDLING_ADAPTER_INSTANCES = "get command handling adapter instances";
    private static final String SPAN_NAME_SET_CMD_HANDLING_ADAPTER_INSTANCE = "set command handling adapter instance";
    private static final String SPAN_NAME_REMOVE_CMD_HANDLING_ADAPTER_INSTANCE = "remove command handling adapter instance";

    final String tenantId;
    final DeviceConnectionInfo cache;
    private final Tracer tracer;

    /**
     * Creates a client for accessing device connection information.
     *
     * @param tenantId The tenant that this client is scoped to.
     * @param cache The remote cache that contains the data.
     * @param tracer The OpenTracing {@code Tracer} to use for tracking requests done by this client.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public CacheBasedDeviceConnectionClient(final String tenantId, final DeviceConnectionInfo cache, final Tracer tracer) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.cache = Objects.requireNonNull(cache);
        this.tracer = Objects.requireNonNull(tracer);
    }

    /**
     * {@inheritDoc}
     *
     * The given handler will immediately be invoked with a succeeded result.
     */
    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        closeHandler.handle(Future.succeededFuture());
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code true} if this client is connected to the data grid.
     */
    @Override
    public boolean isOpen() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * Invocations of this method are ignored.
     */
    @Override
    public void setRequestTimeout(final long timoutMillis) {
        // ignored
    }

    /**
     * {@inheritDoc}
     *
     * @return Always 1.
     */
    @Override
    public int getCredit() {
        return 1;
    }

    /**
     * {@inheritDoc}
     *
     * The given handler will be invoked immediately.
     */
    @Override
    public void sendQueueDrainHandler(final Handler<Void> handler) {
        handler.handle(null);
    }

    /**
     * {@inheritDoc}
     *
     * If this method is invoked from a vert.x Context, then the returned future will be completed on that context.
     */
    @Override
    public Future<Void> setLastKnownGatewayForDevice(final String deviceId, final String gatewayId, final SpanContext context) {
        final Span span = newSpan(context, SPAN_NAME_SET_LAST_GATEWAY);
        TracingHelper.setDeviceTags(span, tenantId, deviceId);
        TracingHelper.TAG_GATEWAY_ID.set(span, gatewayId);
        return finishSpan(cache.setLastKnownGatewayForDevice(tenantId, deviceId, gatewayId, span), span);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> getLastKnownGatewayForDevice(final String deviceId, final SpanContext context) {
        final Span span = newSpan(context, SPAN_NAME_GET_LAST_GATEWAY);
        TracingHelper.setDeviceTags(span, tenantId, deviceId);
        return finishSpan(cache.getLastKnownGatewayForDevice(tenantId, deviceId, span), span);
    }

    @Override
    public Future<Void> setCommandHandlingAdapterInstance(final String deviceId, final String adapterInstanceId,
            final Duration lifespan, final SpanContext context) {
        final Span span = newSpan(context, SPAN_NAME_SET_CMD_HANDLING_ADAPTER_INSTANCE);
        TracingHelper.setDeviceTags(span, tenantId, deviceId);
        span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        final int lifespanSeconds = lifespan != null && lifespan.getSeconds() <= Integer.MAX_VALUE ? (int) lifespan.getSeconds() : -1;
        span.setTag(MessageHelper.APP_PROPERTY_LIFESPAN, lifespanSeconds);
        return finishSpan(cache.setCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, lifespan, span), span);
    }

    @Override
    public Future<Void> removeCommandHandlingAdapterInstance(final String deviceId, final String adapterInstanceId,
            final SpanContext context) {
        final Span span = newSpan(context, SPAN_NAME_REMOVE_CMD_HANDLING_ADAPTER_INSTANCE);
        TracingHelper.setDeviceTags(span, tenantId, deviceId);
        span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        // skip tracing PRECON_FAILED response status as error (may have been trying to remove an expired entry)
        return finishSpan(cache.removeCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, span), span,
                HttpURLConnection.HTTP_PRECON_FAILED);
    }

    @Override
    public Future<JsonObject> getCommandHandlingAdapterInstances(final String deviceId, final List<String> viaGateways,
            final SpanContext context) {
        final Span span = newSpan(context, SPAN_NAME_GET_CMD_HANDLING_ADAPTER_INSTANCES);
        TracingHelper.setDeviceTags(span, tenantId, deviceId);
        return finishSpan(cache.getCommandHandlingAdapterInstances(tenantId, deviceId, new HashSet<>(viaGateways), span), span);
    }

    private Span newSpan(final SpanContext parent, final String operationName) {
        return TracingHelper.buildChildSpan(tracer, parent, operationName, getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .start();
    }

    private <T> Future<T> finishSpan(final Future<T> result, final Span span) {
        return finishSpan(result, span, null);
    }

    private <T> Future<T> finishSpan(final Future<T> result, final Span span, final Integer statusToSkipErrorTraceFor) {
        return result.recover(t -> {
            final int statusCode = ServiceInvocationException.extractStatusCode(t);
            Tags.HTTP_STATUS.set(span, statusCode);
            if (statusToSkipErrorTraceFor == null || statusCode != statusToSkipErrorTraceFor) {
                TracingHelper.logError(span, t);
            }
            span.finish();
            return Future.failedFuture(t);
        }).map(resultValue -> {
            Tags.HTTP_STATUS.set(span, resultValue != null ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_NO_CONTENT);
            span.finish();
            return resultValue;
        });
    }
}
