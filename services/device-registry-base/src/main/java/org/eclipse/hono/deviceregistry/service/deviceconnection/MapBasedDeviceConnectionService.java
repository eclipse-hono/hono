/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.deviceregistry.service.deviceconnection;

import java.net.HttpURLConnection;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hono.service.deviceconnection.DeviceConnectionService;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.DeviceConnectionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A device connection service that keeps all data in memory.
 */
@Repository
@Qualifier("backend")
public final class MapBasedDeviceConnectionService implements DeviceConnectionService {

    private static final Logger log = LoggerFactory.getLogger(MapBasedDeviceConnectionService.class);

    // <tenantId, <deviceId, lastKnownGatewayJson>>
    private final Map<String, Map<String, JsonObject>> lastKnownGatewaysMap = new HashMap<>();

    // <tenantId, <deviceId, adapterInstanceIdJson>>
    private final Map<String, Map<String, JsonObject>> commandHandlingAdapterInstancesMap = new HashMap<>();

    private MapBasedDeviceConnectionsConfigProperties config;

    @Autowired
    public void setConfig(final MapBasedDeviceConnectionsConfigProperties configuration) {
        this.config = configuration;
    }

    public MapBasedDeviceConnectionsConfigProperties getConfig() {
        return config;
    }

    @Override
    public Future<DeviceConnectionResult> setLastKnownGatewayForDevice(final String tenantId, final String deviceId,
            final String gatewayId, final Span span) {
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
        return Future.succeededFuture(result);
    }

    @Override
    public Future<DeviceConnectionResult> getLastKnownGatewayForDevice(final String tenantId, final String deviceId,
            final Span span) {
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
        return Future.succeededFuture(result);
    }

    @Override
    public Future<DeviceConnectionResult> setCommandHandlingAdapterInstance(final String tenantId, final String deviceId,
            final String protocolAdapterInstanceId, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(protocolAdapterInstanceId);

        final Map<String, JsonObject> adapterInstancesForTenantMap = commandHandlingAdapterInstancesMap.computeIfAbsent(tenantId,
                k -> new ConcurrentHashMap<>());
        final DeviceConnectionResult result;
        final int currentMapSize = adapterInstancesForTenantMap.size();
        if (currentMapSize < getConfig().getMaxDevicesPerTenant()
                || (currentMapSize == getConfig().getMaxDevicesPerTenant() && adapterInstancesForTenantMap.containsKey(deviceId))) {
            adapterInstancesForTenantMap.put(deviceId, createAdapterInstanceIdJson(protocolAdapterInstanceId));
            result = DeviceConnectionResult.from(HttpURLConnection.HTTP_NO_CONTENT);
        } else {
            log.debug("cannot set protocol adapter instance for handling commands of device [{}], tenant [{}]: max number of entries per tenant reached ({})",
                    deviceId, tenantId, getConfig().getMaxDevicesPerTenant());
            result = DeviceConnectionResult.from(HttpURLConnection.HTTP_FORBIDDEN);
        }
        return Future.succeededFuture(result);
    }

    @Override
    public Future<DeviceConnectionResult> removeCommandHandlingAdapterInstance(final String tenantId, final String deviceId,
            final String adapterInstanceId, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        final Map<String, JsonObject> adapterInstancesForTenantMap = commandHandlingAdapterInstancesMap.computeIfAbsent(tenantId,
                k -> new ConcurrentHashMap<>());

        final JsonObject adapterInstanceIdJson = adapterInstancesForTenantMap.get(deviceId);
        final Future<DeviceConnectionResult> resultFuture;
        if (adapterInstanceIdJson != null) {
            // remove entry only if existing value contains matching adapterInstanceId
            final boolean removed = adapterInstanceId.equals(getAdapterInstanceIdFromJson(adapterInstanceIdJson))
                    && adapterInstancesForTenantMap.remove(deviceId, adapterInstanceIdJson);
            if (removed) {
                resultFuture = Future.succeededFuture(DeviceConnectionResult.from(HttpURLConnection.HTTP_NO_CONTENT));
            } else {
                log.debug("cannot remove command handling adapter instance for device [{}], tenant [{}] - given value does not match current",
                        deviceId, tenantId);
                resultFuture = Future.succeededFuture(DeviceConnectionResult.from(HttpURLConnection.HTTP_PRECON_FAILED));
            }
        } else {
            resultFuture = Future.succeededFuture(DeviceConnectionResult.from(HttpURLConnection.HTTP_NOT_FOUND));
        }
        return resultFuture;
    }

    @Override
    public Future<DeviceConnectionResult> getCommandHandlingAdapterInstances(final String tenantId,
            final String deviceId, final List<String> viaGateways, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final Map<String, JsonObject> commandHandlersForTenantMap = commandHandlingAdapterInstancesMap.get(tenantId);
        final DeviceConnectionResult result;
        if (commandHandlersForTenantMap != null) {
            // resultMap has device id as key and adapter instance id as value
            final Map<String, String> resultMap = new HashMap<>();
            final JsonObject deviceAdapterInstanceIdJson = commandHandlersForTenantMap.get(deviceId);
            if (deviceAdapterInstanceIdJson != null) {
                // found mapping for given device id
                resultMap.put(deviceId, getAdapterInstanceIdFromJson(deviceAdapterInstanceIdJson));
            } else if (!viaGateways.isEmpty()) {
                // no mapping found for given device; check last known gateway of device
                final Map<String, JsonObject> lastKnownGatewaysForTenantMap = lastKnownGatewaysMap.get(tenantId);
                if (lastKnownGatewaysForTenantMap != null) {
                    final JsonObject lastKnownGatewayJson = lastKnownGatewaysForTenantMap.get(deviceId);
                    if (lastKnownGatewayJson != null) {
                        final String gatewayId = getGatewayIdFromLastKnownGatewayJson(lastKnownGatewayJson);
                        if (viaGateways.contains(gatewayId)) {
                            // get command handler for found gateway device
                            final JsonObject gwAdapterInstanceIdJson = commandHandlersForTenantMap.get(gatewayId);
                            if (gwAdapterInstanceIdJson != null) {
                                resultMap.put(gatewayId, getAdapterInstanceIdFromJson(gwAdapterInstanceIdJson));
                            }
                        } else {
                            log.trace("ignoring found last known gateway [{}]; gateway is not in given via list", gatewayId);
                        }
                    }
                }
            }
            if (resultMap.isEmpty() && !viaGateways.isEmpty()) {
                log.trace("no command handling adapter instance found for given device or last known gateway; getting instances for all via gateways");
                for (final String viaGateway : viaGateways) {
                    final JsonObject gwAdapterInstanceIdJson = commandHandlersForTenantMap.get(viaGateway);
                    if (gwAdapterInstanceIdJson != null) {
                        resultMap.put(viaGateway, getAdapterInstanceIdFromJson(gwAdapterInstanceIdJson));
                    }
                }
            }
            if (!resultMap.isEmpty()) {
                result = DeviceConnectionResult.from(HttpURLConnection.HTTP_OK, getResultJson(resultMap));
            } else {
                result = DeviceConnectionResult.from(HttpURLConnection.HTTP_NOT_FOUND);
            }
        } else {
            result = DeviceConnectionResult.from(HttpURLConnection.HTTP_NOT_FOUND);
        }
        return Future.succeededFuture(result);
    }

    private JsonObject getResultJson(final Map<String, String> deviceToAdapterInstanceMap) {
        final JsonObject jsonObject = new JsonObject();
        final JsonArray adapterInstancesArray = new JsonArray(new ArrayList<>(deviceToAdapterInstanceMap.size()));
        for (final Map.Entry<String, String> resultEntry : deviceToAdapterInstanceMap.entrySet()) {
            final JsonObject entryJson = new JsonObject();
            entryJson.put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, resultEntry.getKey());
            entryJson.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, resultEntry.getValue());
            adapterInstancesArray.add(entryJson);
        }
        jsonObject.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES, adapterInstancesArray);
        return jsonObject;
    }

    private JsonObject createLastKnownGatewayJson(final String gatewayId) {
        final JsonObject lastKnownGatewayJson = new JsonObject();
        lastKnownGatewayJson.put(DeviceConnectionConstants.FIELD_GATEWAY_ID, gatewayId);
        setLastUpdateDate(lastKnownGatewayJson);
        return lastKnownGatewayJson;
    }

    private String getGatewayIdFromLastKnownGatewayJson(final JsonObject lastKnownGatewayJson) {
        return lastKnownGatewayJson.getString(DeviceConnectionConstants.FIELD_GATEWAY_ID);
    }

    private JsonObject setLastUpdateDate(final JsonObject lastKnownGatewayJson) {
        lastKnownGatewayJson.put(DeviceConnectionConstants.FIELD_LAST_UPDATED,
                ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
        return lastKnownGatewayJson;
    }

    private JsonObject createAdapterInstanceIdJson(final String adapterInstanceId) {
        final JsonObject adapterInstanceIdJson = new JsonObject();
        adapterInstanceIdJson.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, adapterInstanceId);
        setLastUpdateDate(adapterInstanceIdJson);
        return adapterInstanceIdJson;
    }

    private String getAdapterInstanceIdFromJson(final JsonObject adapterInstanceIdJson) {
        return adapterInstanceIdJson.getString(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID);
    }

}
