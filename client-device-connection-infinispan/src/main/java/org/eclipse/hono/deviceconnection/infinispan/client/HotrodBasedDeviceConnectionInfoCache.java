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


package org.eclipse.hono.deviceconnection.infinispan.client;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;


/**
 * A client for accessing device connection information in a data grid using the
 * Hotrod protocol.
 *
 */
public final class HotrodBasedDeviceConnectionInfoCache implements DeviceConnectionInfoCache, HealthCheckProvider {

    private static final Logger LOG = LoggerFactory.getLogger(HotrodBasedDeviceConnectionInfoCache.class);

    final HotrodCache<String, String> cache;

    /**
     * Creates a client for accessing device connection information.
     * 
     * @param cache The remote cache that contains the data.
     */
    public HotrodBasedDeviceConnectionInfoCache(final HotrodCache<String, String> cache) {
        this.cache = Objects.requireNonNull(cache);
    }

    private static String getKey(final String tenantId, final String deviceId) {
        return String.format("%s@@%s", tenantId, deviceId);
    }


    private static JsonObject getResult(final String gatewayId) {
        return new JsonObject().put(DeviceConnectionConstants.FIELD_GATEWAY_ID, gatewayId);
    }

    /**
     * {@inheritDoc}
     * 
     * If this method is invoked from a vert.x Context, then the returned future will be completed on that context.
     */
    @Override
    public Future<Void> setLastKnownGatewayForDevice(final String tenantId, final String deviceId, final String gatewayId, final SpanContext context) {

        return cache.put(getKey(tenantId, deviceId), gatewayId)
            .map(replacedValue -> {
                LOG.debug("set last known gateway [tenant: {}, device-id: {}, gateway: {}]", tenantId, deviceId,
                        gatewayId);
                return (Void) null;
            })
            .recover(t -> {
                LOG.debug("failed to set last known gateway [tenant: {}, device-id: {}, gateway: {}]",
                        tenantId, deviceId, gatewayId, t);
                return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, t));
            });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> getLastKnownGatewayForDevice(final String tenantId, final String deviceId, final SpanContext context) {

        return cache.get(getKey(tenantId, deviceId))
                .compose(gatewayId -> {
                    if (gatewayId == null) {
                        LOG.debug("could not find last known gateway for device [tenant: {}, device-id: {}]", tenantId,
                                deviceId);
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND));
                    } else {
                        LOG.debug("found last known gateway for device [tenant: {}, device-id: {}]: {}", tenantId,
                                deviceId, gatewayId);
                        return Future.succeededFuture(getResult(gatewayId));
                    }
                })
                .recover(t -> {
                    LOG.debug("failed to find last known gateway for device [tenant: {}, device-id: {}]",
                            tenantId, deviceId, t);
                    return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, t));
                });
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers a check for an established connection to the remote cache.
     * The check times out (and fails) after 1000ms.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        readinessHandler.register("remote-cache-connection", 1000, this::checkForCacheAvailability);
    }

    private void checkForCacheAvailability(final Promise<Status> status) {

        cache.checkForCacheAvailability()
            .map(stats -> Status.OK(stats))
            .otherwise(t -> Status.KO())
            .setHandler(status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
    }
}
