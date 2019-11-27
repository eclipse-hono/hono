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


package org.eclipse.hono.deviceconnection.infinispan.client;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.HonoProtonHelper;
import org.infinispan.commons.api.BasicCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;


/**
 * A client for accessing device connection information in a data grid using the
 * Hotrod protocol.
 *
 */
public final class HotrodBasedDeviceConnectionInfoCache implements DeviceConnectionInfoCache {

    private static final Logger LOG = LoggerFactory.getLogger(HotrodBasedDeviceConnectionInfoCache.class);

    final BasicCache<String, String> cache;

    /**
     * Creates a client for accessing device connection information.
     * 
     * @param cache The remote cache that contains the data.
     */
    public HotrodBasedDeviceConnectionInfoCache(final BasicCache<String, String> cache) {
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

        final Promise<Void> result = Promise.promise();

        if (cache == null) {
            result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no connection to remote cache"));
        } else {
            cache
            .putAsync(getKey(tenantId, deviceId), gatewayId)
            .whenComplete((replacedValue, error) -> {
                if (error == null) {
                    LOG.debug("set last known gateway [tenant: {}, device-id: {}, gateway: {}]", tenantId, deviceId, gatewayId);
                    result.complete();
                } else {
                    LOG.debug("failed to set last known gateway [tenant: {}, device-id: {}, gateway: {}]",
                            tenantId, deviceId, gatewayId, error);
                    result.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, error));
                }
            });
        }

        return HonoProtonHelper.handleOnContext(result, Vertx.currentContext());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> getLastKnownGatewayForDevice(final String tenantId, final String deviceId, final SpanContext context) {

        final Promise<JsonObject> result = Promise.promise();

        if (cache == null) {
            result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no connection to remote cache"));
        } else {
            cache.getAsync(getKey(tenantId, deviceId))
            .whenComplete((gatewayId, error) -> {
                if (error != null) {
                    LOG.debug("failed to find last known gateway for device [tenant: {}, device-id: {}]",
                            tenantId, deviceId, error);
                    result.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, error));
                } else if (gatewayId == null) {
                    LOG.debug("could not find last known gateway for device [tenant: {}, device-id: {}]", tenantId, deviceId);
                    result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND));
                } else {
                    LOG.debug("found last known gateway for device [tenant: {}, device-id: {}]: {}", tenantId, deviceId, gatewayId);
                    result.complete(getResult(gatewayId));
                }
            });
        }

        return HonoProtonHelper.handleOnContext(result, Vertx.currentContext());
    }
}
