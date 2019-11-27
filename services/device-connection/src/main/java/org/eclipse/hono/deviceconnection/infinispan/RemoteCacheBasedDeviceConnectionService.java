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
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfoCache;
import org.eclipse.hono.deviceconnection.infinispan.client.HotrodBasedDeviceConnectionInfoCache;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.deviceconnection.DeviceConnectionService;
import org.eclipse.hono.service.deviceconnection.EventBusDeviceConnectionAdapter;
import org.eclipse.hono.util.DeviceConnectionResult;
import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.infinispan.commons.api.BasicCache;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;


/**
 * An implementation of Hono's <em>Device Connection</em> API that uses an Infinispan cache
 * for storing the device connection data.
 */
public class RemoteCacheBasedDeviceConnectionService extends EventBusDeviceConnectionAdapter implements DeviceConnectionService, HealthCheckProvider {

    private RemoteCacheContainer cacheManager;
    private DeviceConnectionInfoCache cache;

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

    void setCache(final DeviceConnectionInfoCache cache) {
        this.cache = cache;
    }

    /**
     * {@inheritDoc}
     * 
     * This method currently delegates to the deprecated {@link #doStart(Future)}
     * method. After its removal, this method will be responsible for all initialization
     * of this service.
     */
    @Override
    protected final void doStart(final Promise<Void> startPromise) {
        doStart(startPromise.future());
    }

    /**
     * {@inheritDoc}
     * 
     * @deprecated This method will be removed in Hono 2.0.0.
     */
    @Deprecated(forRemoval = true)
    @Override
    protected void doStart(final Future<Void> startFuture) {

        final Promise<Void> result = Promise.promise();
        result.future().setHandler(startFuture);

        if (cache != null) {
            result.complete();
        } else if (cacheManager == null) {
            result.fail(new IllegalStateException("cache manager is not set"));
        } else {
            context.executeBlocking(r -> {
                cacheManager.start();
                final BasicCache<String, String> remoteCache = cacheManager.getCache("device-connection");
                remoteCache.start();
                cache = new HotrodBasedDeviceConnectionInfoCache(remoteCache);
                r.complete(cacheManager);
            }, attempt -> {
                if (attempt.succeeded()) {
                    log.info("successfully connected to remote cache");
                } else {
                    log.info("failed to connect to remote cache", attempt.cause());
                }
            });
            result.complete();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * This method currently delegates to the deprecated {@link #doStop(Future)}
     * method. After its removal, this method will be responsible for cleaning up
     * during shutdown of this service.
     */
    @Override
    protected final void doStop(final Promise<Void> stopPromise) {
        doStop(stopPromise.future());
    }

    /**
     * {@inheritDoc}
     * 
     * @deprecated This method will be removed in Hono 2.0.0.
     */
    @Deprecated(forRemoval = true)
    @Override
    protected void doStop(final Future<Void> stopFuture) {

        final Promise<Void> result = Promise.promise();
        result.future().setHandler(stopFuture);

        context.executeBlocking(r -> {
            if (cacheManager != null) {
                cacheManager.stop();
            }
            r.complete();
        }, stopAttempt -> {
            if (stopAttempt.succeeded()) {
                log.info("connection(s) to remote cache stopped successfully");
            } else {
                log.info("error trying to stop connection(s) to remote cache", stopAttempt.cause());
            }
            result.complete();
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
            cache.setLastKnownGatewayForDevice(tenantId, deviceId, gatewayId, span.context())
            .map(ok -> DeviceConnectionResult.from(HttpURLConnection.HTTP_NO_CONTENT))
            .setHandler(resultHandler);
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
            cache.getLastKnownGatewayForDevice(tenantId, deviceId, span.context())
            .map(json -> DeviceConnectionResult.from(HttpURLConnection.HTTP_OK, json))
            .otherwise(t -> DeviceConnectionResult.from(ServiceInvocationException.extractStatusCode(t)))
            .setHandler(resultHandler);
        }
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
