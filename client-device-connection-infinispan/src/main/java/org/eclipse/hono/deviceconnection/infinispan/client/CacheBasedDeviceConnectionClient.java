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

import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
import org.eclipse.hono.adapter.client.util.ServiceClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * A client for accessing device connection information in a cache.
 */
public final class CacheBasedDeviceConnectionClient implements DeviceConnectionClient, ServiceClient {

    private static final String SPAN_NAME_GET_LAST_GATEWAY = "get last known gateway";
    private static final String SPAN_NAME_SET_LAST_GATEWAY = "set last known gateway";
    private static final String SPAN_NAME_GET_CMD_HANDLING_ADAPTER_INSTANCES = "get command handling adapter instances";
    private static final String SPAN_NAME_SET_CMD_HANDLING_ADAPTER_INSTANCE = "set command handling adapter instance";
    private static final String SPAN_NAME_REMOVE_CMD_HANDLING_ADAPTER_INSTANCE = "remove command handling adapter instance";

    private final DeviceConnectionInfo connectionInfoCache;
    private final Tracer tracer;

    /**
     * Creates a client for accessing device connection information.
     *
     * @param cache The remote cache that contains the data.
     * @param tracer The OpenTracing {@code Tracer} to use for tracking requests done by this client.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public CacheBasedDeviceConnectionClient(final DeviceConnectionInfo cache, final Tracer tracer) {
        this.connectionInfoCache = Objects.requireNonNull(cache);
        this.tracer = Objects.requireNonNull(tracer);
    }

    /**
     * {@inheritDoc}
     *
     * If this method is invoked from a vert.x Context, then the returned future will be completed on that context.
     */
    @Override
    public Future<Void> setLastKnownGatewayForDevice(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {

        final Span span = newSpan(context, SPAN_NAME_SET_LAST_GATEWAY);
        TracingHelper.setDeviceTags(span, tenantId, deviceId);
        TracingHelper.TAG_GATEWAY_ID.set(span, gatewayId);

        return finishSpan(connectionInfoCache.setLastKnownGatewayForDevice(tenantId, deviceId, gatewayId, span), span);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> getLastKnownGatewayForDevice(
            final String tenantId,
            final String deviceId,
            final SpanContext context) {

        final Span span = newSpan(context, SPAN_NAME_GET_LAST_GATEWAY);
        TracingHelper.setDeviceTags(span, tenantId, deviceId);

        return finishSpan(connectionInfoCache.getLastKnownGatewayForDevice(tenantId, deviceId, span), span);
    }

    @Override
    public Future<Void> setCommandHandlingAdapterInstance(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final Duration lifespan,
            final SpanContext context) {

        final Span span = newSpan(context, SPAN_NAME_SET_CMD_HANDLING_ADAPTER_INSTANCE);
        TracingHelper.setDeviceTags(span, tenantId, deviceId);
        span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        final int lifespanSeconds = lifespan != null && lifespan.getSeconds() <= Integer.MAX_VALUE ? (int) lifespan.getSeconds() : -1;
        span.setTag(MessageHelper.APP_PROPERTY_LIFESPAN, lifespanSeconds);

        return finishSpan(connectionInfoCache.setCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, lifespan, span), span);
    }

    @Override
    public Future<Void> removeCommandHandlingAdapterInstance(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final SpanContext context) {

        final Span span = newSpan(context, SPAN_NAME_REMOVE_CMD_HANDLING_ADAPTER_INSTANCE);
        TracingHelper.setDeviceTags(span, tenantId, deviceId);
        span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);

        // skip tracing PRECON_FAILED response status as error (may have been trying to remove an expired entry)
        return finishSpan(
                connectionInfoCache.removeCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, span),
                span,
                HttpURLConnection.HTTP_PRECON_FAILED);
    }

    @Override
    public Future<JsonObject> getCommandHandlingAdapterInstances(
            final String tenantId,
            final String deviceId,
            final List<String> viaGateways,
            final SpanContext context) {

        final Span span = newSpan(context, SPAN_NAME_GET_CMD_HANDLING_ADAPTER_INSTANCES);
        TracingHelper.setDeviceTags(span, tenantId, deviceId);

        return finishSpan(connectionInfoCache.getCommandHandlingAdapterInstances(tenantId, deviceId, new HashSet<>(viaGateways), span), span);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> start() {
        if (connectionInfoCache instanceof Lifecycle) {
            return ((Lifecycle) connectionInfoCache).stop();
        } else {
            return Future.succeededFuture();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> stop() {
        if (connectionInfoCache instanceof Lifecycle) {
            return ((Lifecycle) connectionInfoCache).stop();
        } else {
            return Future.succeededFuture();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        if (connectionInfoCache instanceof ServiceClient) {
            ((ServiceClient) connectionInfoCache).registerReadinessChecks(readinessHandler);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        if (connectionInfoCache instanceof ServiceClient) {
            ((ServiceClient) connectionInfoCache).registerLivenessChecks(livenessHandler);
        }
    }
}
