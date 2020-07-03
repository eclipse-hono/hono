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

package org.eclipse.hono.deviceconnection.infinispan;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.deviceconnection.DeviceConnectionService;
import org.eclipse.hono.util.DeviceConnectionResult;

import io.opentracing.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.healthchecks.HealthCheckHandler;


/**
 * An implementation of Hono's <em>Device Connection</em> API that uses an Infinispan cache
 * for storing the device connection data.
 */
public class CacheBasedDeviceConnectionService extends AbstractVerticle implements DeviceConnectionService, HealthCheckProvider {

    private final DeviceConnectionInfo cache;

    /**
     * Creates a new service instance for a remote cache.
     *
     * @param cache The remote cache.
     * @throws NullPointerException if the cache is {@code null}.
     */
    public CacheBasedDeviceConnectionService(final DeviceConnectionInfo cache) {
        this.cache = Objects.requireNonNull(cache);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<DeviceConnectionResult> setLastKnownGatewayForDevice(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Span span) {

        return cache.setLastKnownGatewayForDevice(tenantId, deviceId, gatewayId, span)
                .map(ok -> DeviceConnectionResult.from(HttpURLConnection.HTTP_NO_CONTENT));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<DeviceConnectionResult> getLastKnownGatewayForDevice(
            final String tenantId,
            final String deviceId,
            final Span span) {

        return cache.getLastKnownGatewayForDevice(tenantId, deviceId, span)
                .map(json -> DeviceConnectionResult.from(HttpURLConnection.HTTP_OK, json))
                .otherwise(t -> DeviceConnectionResult.from(ServiceInvocationException.extractStatusCode(t)));
    }

    @Override
    public Future<DeviceConnectionResult> setCommandHandlingAdapterInstance(final String tenantId,
            final String deviceId, final String adapterInstanceId, final Duration lifespan,
            final Span span) {
        return cache.setCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, lifespan, span)
                .map(v -> DeviceConnectionResult.from(HttpURLConnection.HTTP_NO_CONTENT))
                .otherwise(t -> DeviceConnectionResult.from(ServiceInvocationException.extractStatusCode(t)));
    }

    @Override
    public Future<DeviceConnectionResult> removeCommandHandlingAdapterInstance(final String tenantId, final String deviceId,
            final String adapterInstanceId, final Span span) {
        return cache.removeCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, span)
                .map(removed -> removed ? DeviceConnectionResult.from(HttpURLConnection.HTTP_NO_CONTENT)
                        : DeviceConnectionResult.from(HttpURLConnection.HTTP_NOT_FOUND))
                .otherwise(t -> DeviceConnectionResult.from(ServiceInvocationException.extractStatusCode(t)));
    }

    @Override
    public Future<DeviceConnectionResult> getCommandHandlingAdapterInstances(final String tenantId, final String deviceId,
            final List<String> viaGateways, final Span span) {
        return cache.getCommandHandlingAdapterInstances(tenantId, deviceId, new HashSet<>(viaGateways), span)
                .map(json -> DeviceConnectionResult.from(HttpURLConnection.HTTP_OK, json))
                .otherwise(t -> DeviceConnectionResult.from(ServiceInvocationException.extractStatusCode(t)));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers checks provided by the remote cache implementation.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        if (cache instanceof HealthCheckProvider) {
            ((HealthCheckProvider) cache).registerReadinessChecks(readinessHandler);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers checks provided by the remote cache implementation.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        if (cache instanceof HealthCheckProvider) {
            ((HealthCheckProvider) cache).registerLivenessChecks(livenessHandler);
        }
    }
}
