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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;


/**
 * A client for accessing device connection information in a data grid.
 */
public final class CacheBasedDeviceConnectionInfo implements DeviceConnectionInfo, HealthCheckProvider {

    /**
     * Lifespan for last-known-gateway cache entries.
     */
    static final Duration LAST_KNOWN_GATEWAY_CACHE_ENTRY_LIFESPAN = Duration.ofDays(28);

    /**
     * For <em>viaGateways</em> parameter value lower or equal to this value, the {@link #getCommandHandlingAdapterInstances(String, String, Set, Span)}
     * method will use an optimized approach, potentially saving additional cache requests.
     */
    static final int VIA_GATEWAYS_OPTIMIZATION_THRESHOLD = 3;

    private static final Logger LOG = LoggerFactory.getLogger(CacheBasedDeviceConnectionInfo.class);

    /**
     * Prefix for cache entries having gateway id values, concerning <em>lastKnownGatewayForDevice</em>
     * operations.
     */
    private static final String KEY_PREFIX_GATEWAY_ENTRIES_VALUE = "gw";
    /**
     * Prefix for cache entries having protocol adapter instance id values, concerning
     * <em>commandHandlingAdapterInstance</em> operations.
     */
    private static final String KEY_PREFIX_ADAPTER_INSTANCE_VALUES = "ai";
    private static final String KEY_SEPARATOR = "@@";

    final Cache<String, String> cache;
    final Tracer tracer;

    /**
     * Creates a client for accessing device connection information.
     *
     * @param cache The remote cache that contains the data.
     * @param tracer The tracer instance.
     * @throws NullPointerException if cache or tracer is {@code null}.
     */
    public CacheBasedDeviceConnectionInfo(final Cache<String, String> cache, final Tracer tracer) {
        this.cache = Objects.requireNonNull(cache);
        this.tracer = Objects.requireNonNull(tracer);
    }

    /**
     * {@inheritDoc}
     *
     * If this method is invoked from a vert.x Context, then the returned future will be completed on that context.
     */
    @Override
    public Future<Void> setLastKnownGatewayForDevice(final String tenantId, final String deviceId,
            final String gatewayId, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(span);

        final long lifespanMillis = LAST_KNOWN_GATEWAY_CACHE_ENTRY_LIFESPAN.toMillis();
        return cache.put(getGatewayEntryKey(tenantId, deviceId), gatewayId, lifespanMillis, TimeUnit.MILLISECONDS)
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
    public Future<JsonObject> getLastKnownGatewayForDevice(final String tenantId, final String deviceId, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(span);

        return cache.get(getGatewayEntryKey(tenantId, deviceId))
                .recover(t -> {
                    LOG.debug("failed to find last known gateway for device [tenant: {}, device-id: {}]",
                            tenantId, deviceId, t);
                    return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, t));
                })
                .compose(gatewayId -> {
                    if (gatewayId == null) {
                        LOG.debug("could not find last known gateway for device [tenant: {}, device-id: {}]", tenantId,
                                deviceId);
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND));
                    } else {
                        LOG.debug("found last known gateway for device [tenant: {}, device-id: {}]: {}", tenantId,
                                deviceId, gatewayId);
                        return Future.succeededFuture(getLastKnownGatewayResultJson(gatewayId));
                    }
                });
    }

    @Override
    public Future<Void> setCommandHandlingAdapterInstance(final String tenantId, final String deviceId,
            final String adapterInstanceId, final Duration lifespan, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);
        Objects.requireNonNull(span);

        // sanity check, preventing an ArithmeticException in lifespan.toMillis()
        final long lifespanMillis = lifespan == null || lifespan.isNegative()
                || lifespan.getSeconds() > (Long.MAX_VALUE / 1000L) ? -1 : lifespan.toMillis();
        return cache.put(getAdapterInstanceEntryKey(tenantId, deviceId), adapterInstanceId, lifespanMillis, TimeUnit.MILLISECONDS)
                .map(replacedValue -> {
                    LOG.debug("set command handling adapter instance [tenant: {}, device-id: {}, adapter-instance: {}, lifespan: {}ms]",
                            tenantId, deviceId, adapterInstanceId, lifespanMillis);
                    return (Void) null;
                })
                .recover(t -> {
                    LOG.debug("failed to set command handling adapter instance [tenant: {}, device-id: {}, adapter-instance: {}, lifespan: {}ms]",
                            tenantId, deviceId, adapterInstanceId, lifespanMillis, t);
                    return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, t));
                });
    }

    @Override
    public Future<Void> removeCommandHandlingAdapterInstance(final String tenantId, final String deviceId,
            final String adapterInstanceId, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);
        Objects.requireNonNull(span);

        final String key = getAdapterInstanceEntryKey(tenantId, deviceId);

        return cache
                .remove(key, adapterInstanceId)
                .recover(t -> {
                    LOG.debug("failed to remove the cache entry when for the command handling adapter instance [tenant: {}, device-id: {}, adapter-instance: {}]",
                            tenantId, deviceId, adapterInstanceId, t);
                    return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, t));
                })
                .compose(removed -> {
                    if (!removed) {
                        LOG.debug("command handling adapter instance was not removed, key not mapped or value didn't match [tenant: {}, device-id: {}, adapter-instance: {}]",
                                tenantId, deviceId, adapterInstanceId);
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED));
                    } else {
                        LOG.debug("removed command handling adapter instance [tenant: {}, device-id: {}, adapter-instance: {}]",
                                tenantId, deviceId, adapterInstanceId);
                        return Future.succeededFuture();
                    }
                });

    }

    @Override
    public Future<JsonObject> getCommandHandlingAdapterInstances(final String tenantId, final String deviceId,
            final Set<String> viaGateways, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(viaGateways);
        Objects.requireNonNull(span);

        final Future<JsonObject> resultFuture;
        if (viaGateways.isEmpty()) {
             // get the command handling adapter instance for the device (no gateway involved)
            resultFuture = cache.get(getAdapterInstanceEntryKey(tenantId, deviceId))
                    .recover(t -> failedToGetEntriesWhenGettingInstances(tenantId, deviceId, t))
                    .compose(adapterInstanceId -> {
                        if (adapterInstanceId == null) {
                            LOG.debug("no command handling adapter instances found [tenant: {}, device-id: {}]",
                                    tenantId, deviceId);
                            span.log("no command handling adapter instances found for device (no via-gateways given)");
                            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND));
                        } else {
                            LOG.debug("found command handling adapter instance '{}' [tenant: {}, device-id: {}]",
                                    adapterInstanceId, tenantId, deviceId);
                            span.log("returning command handling adapter instance for device itself");
                            setTagsForSingleResult(span, adapterInstanceId);
                            return Future.succeededFuture(getAdapterInstancesResultJson(deviceId, adapterInstanceId));
                        }
                    });
        } else if (viaGateways.size() <= VIA_GATEWAYS_OPTIMIZATION_THRESHOLD) {
            resultFuture = getInstancesQueryingAllGatewaysFirst(tenantId, deviceId, viaGateways, span);
        } else {
            // number of viaGateways is more than threshold value - reduce cache accesses by not checking *all* viaGateways,
            // instead trying the last known gateway first
            resultFuture = getInstancesGettingLastKnownGatewayFirst(tenantId, deviceId, viaGateways, span);
        }
        return resultFuture;
    }

    private Future<JsonObject> getInstancesQueryingAllGatewaysFirst(final String tenantId, final String deviceId,
            final Set<String> viaGateways, final Span span) {
        // get the command handling adapter instances for the device and *all* via-gateways in one call first
        // (this saves the extra lastKnownGateway check if only one adapter instance is returned)
        return cache.getAll(getAdapterInstanceEntryKeys(tenantId, deviceId, viaGateways))
                .recover(t -> failedToGetEntriesWhenGettingInstances(tenantId, deviceId, t))
                .compose(getAllMap -> {
                    final Map<String, String> deviceToInstanceMap = convertAdapterInstanceEntryKeys(getAllMap);
                    final Future<JsonObject> resultFuture;
                    if (deviceToInstanceMap.isEmpty()) {
                        LOG.debug("no command handling adapter instances found [tenant: {}, device-id: {}]",
                                tenantId, deviceId);
                        span.log("no command handling adapter instances found for device or given via-gateways ("
                                + String.join(", ", viaGateways) + ")");
                        resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND));
                    } else if (deviceToInstanceMap.containsKey(deviceId)) {
                        // there is a adapter instance set for the device itself - that gets precedence
                        resultFuture = getAdapterInstanceFoundForDeviceItselfResult(tenantId, deviceId,
                                deviceToInstanceMap.get(deviceId), span);
                    } else if (deviceToInstanceMap.size() > 1) {
                        // multiple gateways found - check last known gateway
                        resultFuture = cache.get(getGatewayEntryKey(tenantId, deviceId))
                                .recover(t -> failedToGetEntriesWhenGettingInstances(tenantId, deviceId, t))
                                .compose(lastKnownGateway -> {
                                    if (lastKnownGateway == null) {
                                        // no last known gateway found - just return all found mapping entries
                                        LOG.debug("returning {} command handling adapter instances for device gateways (no last known gateway found) [tenant: {}, device-id: {}]",
                                                deviceToInstanceMap.size(), tenantId, deviceId);
                                        span.log("no last known gateway found, returning all matching adapter instances");
                                        return Future.succeededFuture(getAdapterInstancesResultJson(deviceToInstanceMap));
                                    } else if (!viaGateways.contains(lastKnownGateway)) {
                                        // found gateway is not valid anymore - just return all found mapping entries
                                        LOG.debug("returning {} command handling adapter instances for device gateways (last known gateway not valid anymore) [tenant: {}, device-id: {}, lastKnownGateway: {}]",
                                                deviceToInstanceMap.size(), tenantId, deviceId, lastKnownGateway);
                                        span.log("last known gateway '" + lastKnownGateway + "' is not valid anymore, returning all matching adapter instances");
                                        return Future.succeededFuture(getAdapterInstancesResultJson(deviceToInstanceMap));
                                    } else if (!deviceToInstanceMap.containsKey(lastKnownGateway)) {
                                        // found gateway has no command handling instance assigned - just return all found mapping entries
                                        LOG.debug("returning {} command handling adapter instances for device gateways (last known gateway not in that list) [tenant: {}, device-id: {}, lastKnownGateway: {}]",
                                                deviceToInstanceMap.size(), tenantId, deviceId, lastKnownGateway);
                                        span.log("last known gateway '" + lastKnownGateway + "' has no adapter instance assigned, returning all matching adapter instances");
                                        return Future.succeededFuture(getAdapterInstancesResultJson(deviceToInstanceMap));
                                    } else {
                                        LOG.debug("returning command handling adapter instance '{}' for last known gateway [tenant: {}, device-id: {}, lastKnownGateway: {}]",
                                                deviceToInstanceMap.get(lastKnownGateway), tenantId, deviceId, lastKnownGateway);
                                        span.log("returning adapter instance for last known gateway '" + lastKnownGateway + "'");
                                        setTagsForSingleResultWithGateway(span, deviceToInstanceMap.get(lastKnownGateway), lastKnownGateway);
                                        return Future.succeededFuture(getAdapterInstancesResultJson(lastKnownGateway,
                                                deviceToInstanceMap.get(lastKnownGateway)));
                                    }
                                });
                    } else {
                        // one command handling instance found
                        final Map.Entry<String, String> foundEntry = deviceToInstanceMap.entrySet().iterator().next();
                        LOG.debug("returning command handling adapter instance '{}' associated with gateway {} [tenant: {}, device-id: {}]",
                                foundEntry.getValue(), foundEntry.getKey(), tenantId, deviceId);
                        span.log("returning adapter instance associated with gateway '" + foundEntry.getKey() + "'");
                        setTagsForSingleResultWithGateway(span, foundEntry.getValue(), foundEntry.getKey());
                        resultFuture = Future.succeededFuture(getAdapterInstancesResultJson(foundEntry.getKey(),
                                foundEntry.getValue()));
                    }
                    return resultFuture;
                });
    }

    private void setTagsForSingleResultWithGateway(final Span span, final String adapterInstanceId, final String gatewayId) {
        span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        span.setTag(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);
    }

    private void setTagsForSingleResult(final Span span, final String adapterInstanceId) {
        span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
    }

    private Future<JsonObject> getInstancesGettingLastKnownGatewayFirst(final String tenantId, final String deviceId,
            final Set<String> viaGateways, final Span span) {
        return cache.get(getGatewayEntryKey(tenantId, deviceId))
                .recover(t -> failedToGetEntriesWhenGettingInstances(tenantId, deviceId, t))
                .compose(lastKnownGateway -> {
                    if (lastKnownGateway == null) {
                        LOG.trace("no last known gateway found [tenant: {}, device-id: {}]", tenantId, deviceId);
                        span.log("no last known gateway found");
                    } else if (!viaGateways.contains(lastKnownGateway)) {
                        LOG.trace("found gateway is not valid for the device anymore [tenant: {}, device-id: {}]", tenantId, deviceId);
                        span.log("found gateway '" + lastKnownGateway + "' is not valid anymore");
                    }
                    if (lastKnownGateway != null && viaGateways.contains(lastKnownGateway)) {
                        // fetch command handling instances for lastKnownGateway and device
                        return cache.getAll(getAdapterInstanceEntryKeys(tenantId, deviceId, lastKnownGateway))
                                .recover(t -> failedToGetEntriesWhenGettingInstances(tenantId, deviceId, t))
                                .compose(getAllMap -> {
                                    final Map<String, String> deviceToInstanceMap = convertAdapterInstanceEntryKeys(getAllMap);
                                    if (deviceToInstanceMap.isEmpty()) {
                                        // no adapter instances found for last-known-gateway and device - check all via gateways
                                        span.log("last known gateway '" + lastKnownGateway + "' has no adapter instance assigned, returning all matching adapter instances");
                                        return getAdapterInstancesWithoutLastKnownGatewayCheck(tenantId, deviceId, viaGateways, span);
                                    } else if (deviceToInstanceMap.containsKey(deviceId)) {
                                        // there is a adapter instance set for the device itself - that gets precedence
                                        return getAdapterInstanceFoundForDeviceItselfResult(tenantId, deviceId, deviceToInstanceMap.get(deviceId), span);
                                    } else {
                                        // adapter instance found for last known gateway
                                        LOG.debug("returning command handling adapter instance '{}' for last known gateway [tenant: {}, device-id: {}, lastKnownGateway: {}]",
                                                deviceToInstanceMap.get(lastKnownGateway), tenantId, deviceId, lastKnownGateway);
                                        span.log("returning adapter instance for last known gateway '" + lastKnownGateway + "'");
                                        setTagsForSingleResultWithGateway(span, deviceToInstanceMap.get(lastKnownGateway), lastKnownGateway);
                                        return Future.succeededFuture(getAdapterInstancesResultJson(deviceToInstanceMap));
                                    }
                                });
                    } else {
                        // last-known-gateway not found or invalid - look for all adapter instances for device and viaGateways
                        return getAdapterInstancesWithoutLastKnownGatewayCheck(tenantId, deviceId, viaGateways, span);
                    }
                });
    }

    private Future<JsonObject> getAdapterInstancesWithoutLastKnownGatewayCheck(final String tenantId,
            final String deviceId, final Set<String> viaGateways, final Span span) {
        return cache.getAll(getAdapterInstanceEntryKeys(tenantId, deviceId, viaGateways))
                .recover(t -> failedToGetEntriesWhenGettingInstances(tenantId, deviceId, t))
                .compose(getAllMap -> {
                    final Map<String, String> deviceToInstanceMap = convertAdapterInstanceEntryKeys(getAllMap);
                    final Future<JsonObject> resultFuture;
                    if (deviceToInstanceMap.isEmpty()) {
                        LOG.debug("no command handling adapter instances found [tenant: {}, device-id: {}]",
                                tenantId, deviceId);
                        span.log("no command handling adapter instances found for device or given via-gateways ("
                                + String.join(", ", viaGateways) + ")");
                        resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND));
                    } else if (deviceToInstanceMap.containsKey(deviceId)) {
                        // there is a command handling instance set for the device itself - that gets precedence
                        resultFuture = getAdapterInstanceFoundForDeviceItselfResult(tenantId, deviceId, deviceToInstanceMap.get(deviceId), span);
                    } else {
                        LOG.debug("returning {} command handling adapter instance(s) (no last known gateway found) [tenant: {}, device-id: {}]",
                                deviceToInstanceMap.size(), tenantId, deviceId);
                        resultFuture = Future.succeededFuture(getAdapterInstancesResultJson(deviceToInstanceMap));
                    }
                    return resultFuture;
                });
    }

    private Future<JsonObject> getAdapterInstanceFoundForDeviceItselfResult(final String tenantId,
            final String deviceId, final String adapterInstanceId, final Span span) {
        LOG.debug("returning command handling adapter instance '{}' for device itself [tenant: {}, device-id: {}]",
                adapterInstanceId, tenantId, deviceId);
        span.log("returning command handling adapter instance for device itself");
        setTagsForSingleResult(span, adapterInstanceId);
        return Future.succeededFuture(getAdapterInstancesResultJson(deviceId, adapterInstanceId));
    }

    private <T> Future<T> failedToGetEntriesWhenGettingInstances(final String tenantId, final String deviceId, final Throwable t) {
        LOG.debug("failed to get cache entries when trying to get command handling adapter instances [tenant: {}, device-id: {}]",
                tenantId, deviceId, t);
        return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, t));
    }

    private static String getGatewayEntryKey(final String tenantId, final String deviceId) {
        return KEY_PREFIX_GATEWAY_ENTRIES_VALUE + KEY_SEPARATOR + tenantId + KEY_SEPARATOR + deviceId;
    }

    private static String getAdapterInstanceEntryKey(final String tenantId, final String deviceId) {
        return KEY_PREFIX_ADAPTER_INSTANCE_VALUES + KEY_SEPARATOR + tenantId + KEY_SEPARATOR + deviceId;
    }

    private static Set<String> getAdapterInstanceEntryKeys(final String tenantId, final String deviceIdA,
            final String deviceIdB) {
        final HashSet<String> keys = new HashSet<>(2);
        keys.add(getAdapterInstanceEntryKey(tenantId, deviceIdA));
        keys.add(getAdapterInstanceEntryKey(tenantId, deviceIdB));
        return keys;
    }

    /**
     * Puts the entries from the given map, having {@link #getAdapterInstanceEntryKey(String, String)} keys, into
     * a new map with just the extracted device ids as keys.
     *
     * @param map Map to get the entries from.
     * @return New map with keys containing just the device id.
     */
    private static Map<String, String> convertAdapterInstanceEntryKeys(final Map<String, String> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(entry -> getDeviceIdFromAdapterInstanceEntryKey(entry.getKey()), Map.Entry::getValue));
    }

    private static String getDeviceIdFromAdapterInstanceEntryKey(final String key) {
        final int pos = key.lastIndexOf(KEY_SEPARATOR);
        return key.substring(pos + KEY_SEPARATOR.length());
    }

    private static Set<String> getAdapterInstanceEntryKeys(final String tenantId, final String deviceIdA,
            final Set<String> additionalDeviceIds) {
        final HashSet<String> keys = new HashSet<>(additionalDeviceIds.size() + 1);
        keys.add(getAdapterInstanceEntryKey(tenantId, deviceIdA));
        additionalDeviceIds.forEach(id -> keys.add(getAdapterInstanceEntryKey(tenantId, id)));
        return keys;
    }

    private static JsonObject getLastKnownGatewayResultJson(final String gatewayId) {
        return new JsonObject().put(DeviceConnectionConstants.FIELD_GATEWAY_ID, gatewayId);
    }

    private static JsonObject getAdapterInstancesResultJson(final Map<String, String> deviceToAdapterInstanceMap) {
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

    private static JsonObject getAdapterInstancesResultJson(final String deviceId, final String adapterInstanceId) {
        return getAdapterInstancesResultJson(Map.of(deviceId, adapterInstanceId));
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
            .map(Status::OK)
            .otherwise(t -> Status.KO())
            .onComplete(ar -> status.tryComplete(ar.result()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
    }
}
