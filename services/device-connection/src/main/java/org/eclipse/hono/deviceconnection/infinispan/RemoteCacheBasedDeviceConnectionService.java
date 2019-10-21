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


package org.eclipse.hono.deviceconnection.infinispan;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.deviceconnection.DeviceConnectionService;
import org.eclipse.hono.service.deviceconnection.EventBusDeviceConnectionAdapter;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.DeviceConnectionResult;
import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.infinispan.commons.api.BasicCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;


/**
 * An implementation of Hono's <em>Device Connection</em> API that uses an Infinispan cache
 * for storing the device connection data.
 */
public class RemoteCacheBasedDeviceConnectionService extends EventBusDeviceConnectionAdapter implements DeviceConnectionService, HealthCheckProvider {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteCacheBasedDeviceConnectionService.class);
    private RemoteCacheContainer cacheManager;
    private BasicCache<String, String> cache;

    /**
     * Sets the cache manager to use for retrieving a cache.
     * 
     * @param cacheManager The cache manager.
     * @throws NullPointerException if cache manager is {@code null}.
     */
    @Autowired
    public void setCacheManager(final RemoteCacheContainer cacheManager) {
        this.cacheManager = Objects.requireNonNull(cacheManager);
        log.info("using cache manager [{}]", cacheManager.getClass().getName());
    }

    void setCache(final BasicCache<String, String> cache) {
        this.cache = cache;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doStart(final Future<Void> startFuture) {

        if (cacheManager == null) {
            startFuture.fail(new IllegalStateException("cache manager is not set"));
        } else {
            context.executeBlocking(result -> {
                cacheManager.start();
                cache = cacheManager.getCache("device-connection");
                cache.start();
                result.complete(cacheManager);
            }, attempt -> {
                if (attempt.succeeded()) {
                    LOG.info("successfully connected to remote cache");
                } else {
                    LOG.info("failed to connect to remote cache", attempt.cause());
                }
            });
            startFuture.complete();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doStop(final Future<Void> stopFuture) {
        context.executeBlocking(result -> {
            if (cacheManager != null) {
                cacheManager.stop();
            }
            result.complete();
        }, stopAttempt -> {
            if (stopAttempt.succeeded()) {
                LOG.info("connection(s) to remote cache stopped successfully");
            } else {
                LOG.info("error trying to stop connection(s) to remote cache", stopAttempt.cause());
            }
            stopFuture.complete();
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLastKnownGatewayForDevice(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Span span,
            final Handler<AsyncResult<DeviceConnectionResult>> resultHandler) {

        if (cache == null) {
            resultHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no connection to remote cache")));
        } else {
            cache
            .putAsync(getKey(tenantId, deviceId), gatewayId)
            .whenComplete((replacedValue, error) -> {
                if (error == null) {
                    log.debug("set last known gateway [tenant: {}, device-id: {}, gateway: {}]", tenantId, deviceId, gatewayId);
                    resultHandler.handle(Future.succeededFuture(DeviceConnectionResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
                } else {
                    log.debug("failed to set last known gateway [tenant: {}, device-id: {}, gateway: {}]",
                            tenantId, deviceId, gatewayId, error);
                    resultHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, error)));
                }
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void getLastKnownGatewayForDevice(
            final String tenantId,
            final String deviceId,
            final Span span,
            final Handler<AsyncResult<DeviceConnectionResult>> resultHandler) {

        if (cache == null) {
            resultHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no connection to remote cache")));
        } else {
            cache.getAsync(getKey(tenantId, deviceId))
            .whenComplete((gatewayId, error) -> {
                final Future<DeviceConnectionResult> result = Future.future();
                if (error != null) {
                    log.debug("failed to find last known gateway for device [tenant: {}, device-id: {}]",
                            tenantId, deviceId, error);
                    result.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, error));
                } else if (gatewayId == null) {
                    log.debug("could not find last known gateway for device [tenant: {}, device-id: {}]", tenantId, deviceId);
                    result.complete(DeviceConnectionResult.from(HttpURLConnection.HTTP_NOT_FOUND));
                } else {
                    log.debug("found last known gateway for device [tenant: {}, device-id: {}]: {}", tenantId, deviceId, gatewayId);
                    result.complete(DeviceConnectionResult.from(HttpURLConnection.HTTP_OK, getResult(gatewayId)));
                }
                resultHandler.handle(result);
            });
        }
    }

    private static JsonObject getResult(final String gatewayId) {
        return new JsonObject().put(DeviceConnectionConstants.FIELD_GATEWAY_ID, gatewayId);
    }

    /**
     * Gets the key to use for a mapping.
     * <p>
     * The key returned by this default implementation consists of concatenation
     * of the tenant ID, {@code @@} and the device ID.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return The key.
     */
    private String getKey(final String tenantId, final String deviceId) {
        return String.format("%s@@%s", tenantId, deviceId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final DeviceConnectionService getService() {
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers a check for an established connection to the remote cache.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        readinessHandler.register("remote-cache-connection", this::checkForCacheAvailability);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers a check for an established connection to the remote cache.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        livenessHandler.register("remote-cache-connection", this::checkForCacheAvailability);
    }

    private void checkForCacheAvailability(final Promise<Status> status) {

        if (cache == null) {
            status.complete(Status.KO());
        } else {
            status.complete(Status.OK());
        }
    }
}
