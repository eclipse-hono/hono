/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.deviceregistry;

import java.net.HttpURLConnection;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hono.service.deviceconnection.BaseDeviceConnectionService;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.DeviceConnectionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A device connection service that keeps all data in memory.
 */
@Repository
public final class MapBasedDeviceConnectionService extends BaseDeviceConnectionService<MapBasedDeviceConnectionsConfigProperties> {

    // <tenantId, <deviceId, lastKnownGatewayJson>>
    private final Map<String, Map<String, JsonObject>> lastKnownGatewaysMap = new HashMap<>();

    @Autowired
    @Override
    public void setConfig(final MapBasedDeviceConnectionsConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    @Override
    public void setLastKnownGatewayForDevice(final String tenantId, final String deviceId, final String gatewayId,
            final Span span, final Handler<AsyncResult<DeviceConnectionResult>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);

        final Map<String, JsonObject> lastKnownGatewaysForTenantMap = lastKnownGatewaysMap.computeIfAbsent(tenantId,
                k -> new ConcurrentHashMap<>());
        final DeviceConnectionResult result;
        final int currentMapSize = lastKnownGatewaysForTenantMap.size();
        if (currentMapSize < getConfig().getMaxDevicesPerTenant()
                || (currentMapSize == getConfig().getMaxDevicesPerTenant() && lastKnownGatewaysForTenantMap.containsKey(deviceId))) {
            lastKnownGatewaysForTenantMap.compute(deviceId, (key, oldValue) -> {
                return oldValue != null ? setLastUpdateDate(oldValue) : createLastKnownGatewayJson(gatewayId);
            });
            result = DeviceConnectionResult.from(HttpURLConnection.HTTP_NO_CONTENT);
        } else {
            log.debug("cannot set last known gateway for device [{}], tenant [{}]: max number of entries per tenant reached ({})",
                    deviceId, tenantId, getConfig().getMaxDevicesPerTenant());
            result = DeviceConnectionResult.from(HttpURLConnection.HTTP_FORBIDDEN);
        }
        resultHandler.handle(Future.succeededFuture(result));
    }

    @Override
    public void getLastKnownGatewayForDevice(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<DeviceConnectionResult>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final Map<String, JsonObject> lastKnownGatewaysForTenantMap = lastKnownGatewaysMap.get(tenantId);
        final DeviceConnectionResult result;
        if (lastKnownGatewaysForTenantMap != null) {
            final JsonObject lastKnownGatewayJson = lastKnownGatewaysForTenantMap.get(deviceId);
            if (lastKnownGatewayJson != null) {
                result = DeviceConnectionResult.from(HttpURLConnection.HTTP_OK, lastKnownGatewayJson);
            } else {
                result = DeviceConnectionResult.from(HttpURLConnection.HTTP_NOT_FOUND);
            }
        } else {
            result = DeviceConnectionResult.from(HttpURLConnection.HTTP_NOT_FOUND);
        }
        resultHandler.handle(Future.succeededFuture(result));
    }

    private JsonObject createLastKnownGatewayJson(final String gatewayId) {
        final JsonObject lastKnownGatewayJson = new JsonObject();
        lastKnownGatewayJson.put(DeviceConnectionConstants.FIELD_GATEWAY_ID, gatewayId);
        setLastUpdateDate(lastKnownGatewayJson);
        return lastKnownGatewayJson;
    }

    private JsonObject setLastUpdateDate(final JsonObject lastKnownGatewayJson) {
        lastKnownGatewayJson.put(DeviceConnectionConstants.FIELD_LAST_UPDATED,
                ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
        return lastKnownGatewayJson;
    }

}
