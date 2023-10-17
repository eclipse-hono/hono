/*******************************************************************************
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command.amqp;

import java.net.HttpURLConnection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.AbstractRequestResponseServiceClient;
import org.eclipse.hono.client.amqp.RequestResponseClient;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.command.CommandRouterClient;
import org.eclipse.hono.client.util.CachingClientFactory;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.CommandRouterConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.Pair;
import org.eclipse.hono.util.RequestResponseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A vertx-proton based client of Hono's Command Router service.
 *
 */
public class ProtonBasedCommandRouterClient extends AbstractRequestResponseServiceClient<JsonObject, RequestResponseResult<JsonObject>> implements CommandRouterClient {

    /**
     * Interval in "set last known gateway" requests are done.
     */
    protected static final long SET_LAST_KNOWN_GATEWAY_UPDATE_INTERVAL_MILLIS = 400L;
    /**
     * Maximum number of entries in a "set last known gateway" batch request.
     */
    protected static final int SET_LAST_KNOWN_GATEWAY_UPDATE_MAX_ENTRIES = 100;
    /**
     * The maximum number of "set last known gateway" requests triggered in one go. Subsequent requests will only be
     * sent after these requests are finished.
     */
    protected static final int SET_LAST_KNOWN_GATEWAY_UPDATE_MAX_PARALLEL_REQ = 50;

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedCommandRouterClient.class);

    /**
     * Key is a Pair with tenant and device identifier, value is the gateway identifier.
     */
    private final LinkedHashMap<Pair<String, String>, String> lastKnownGatewaysWorkQueue = new LinkedHashMap<>();
    /**
     * A non-null value means the "set last known gateways" update is currently in progress.
     */
    private Long lastKnownGatewaysUpdateTimerId;

    private boolean stopped = false;
    private Clock clock = Clock.systemUTC();

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the Command Router service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedCommandRouterClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory) {
        super(connection,
                samplerFactory,
                new CachingClientFactory<>(connection.getVertx(), RequestResponseClient::isOpen),
                null);
        connection.getVertx().eventBus().consumer(
                Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    /**
     * Sets a clock to use for determining the current system time.
     * <p>
     * The default value of this property is {@link Clock#systemUTC()}.
     * <p>
     * This property should only be set for running tests expecting the current
     * time to be a certain value, e.g. by using {@link Clock#fixed(Instant, java.time.ZoneId)}.
     *
     * @param clock The clock to use.
     * @throws NullPointerException if clock is {@code null}.
     */
    void setClock(final Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Future<Void> stop() {
        stopped = true;
        Optional.ofNullable(lastKnownGatewaysUpdateTimerId).ifPresent(tid -> connection.getVertx().cancelTimer(tid));
        return disconnectOnStop();
    }

    @Override
    protected String getKey(final String tenantId) {
        return String.format("%s-%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, tenantId);
    }

    private Future<RequestResponseClient<RequestResponseResult<JsonObject>>> getOrCreateClient(final String tenantId) {

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            getKey(tenantId),
                            () -> {
                                return RequestResponseClient.forEndpoint(
                                        connection,
                                        CommandRouterConstants.COMMAND_ROUTER_ENDPOINT,
                                        tenantId,
                                        samplerFactory.create(CommandRouterConstants.COMMAND_ROUTER_ENDPOINT),
                                        this::removeClient,
                                        this::removeClient);
                            },
                            result);
                }));
    }

    @Override
    protected final RequestResponseResult<JsonObject> getResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {

        final var props = Optional.ofNullable(applicationProperties)
                .map(ApplicationProperties::getValue)
                .orElse(null);

        if (payload == null) {
            return new RequestResponseResult<>(status, null, null, props);
        } else {
            try {
                // ignoring given cacheDirective param here - command router results shall not be cached
                return new RequestResponseResult<>(status, new JsonObject(payload),
                        CacheDirective.noCacheDirective(), props);
            } catch (final DecodeException e) {
                LOG.warn("received malformed payload from Command Router service", e);
                return new RequestResponseResult<>(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null, props);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns a succeeded future and strives to set the gateway with a delay of at most
     * {@value #SET_LAST_KNOWN_GATEWAY_UPDATE_INTERVAL_MILLIS} ms as part of a batch request.
     */
    @Override
    public Future<Void> setLastKnownGatewayForDevice(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);

        // last known gateway isn't set immediately here, data is put in a queue to do a batch request later

        // insertion order used here so that oldest mappings are processed first (entries are removed when requests are prepared)
        lastKnownGatewaysWorkQueue.put(Pair.of(tenantId, deviceId), gatewayId);

        if (lastKnownGatewaysUpdateTimerId == null && !stopped) {
            lastKnownGatewaysUpdateTimerId = connection.getVertx().setTimer(
                    SET_LAST_KNOWN_GATEWAY_UPDATE_INTERVAL_MILLIS,
                    tid -> processLastKnownGatewaysWorkQueue(null, null, null));
        }
        return Future.succeededFuture();
    }

    private void processLastKnownGatewaysWorkQueue(final Instant processingStartParam, final Set<String> tenantsToProcess,
            final Span spanParam) {
        log.debug("processLastKnownGatewaysWorkQueue; queue size: {}", lastKnownGatewaysWorkQueue.size());
        final Instant processingStart = Optional.ofNullable(processingStartParam).orElseGet(() -> Instant.now(clock));
        final Span currentSpan = Optional.ofNullable(spanParam)
                .orElseGet(() -> newFollowingSpan(null, "set last known gateways"));
        if (tenantsToProcess == null) {
            currentSpan.log(Map.of("no_of_device_entries_to_set", lastKnownGatewaysWorkQueue.size()));
        }

        final Map<String, Map<String, String>> deviceToGatewayMapPerTenant = new LinkedHashMap<>();
        final Set<String> tenantsWithRequestLimitReached = new HashSet<>();

        // assemble data for the batch requests triggered in this run
        // - entries exceeding the request size or count limits will be handled in the next run
        for (final var iter = lastKnownGatewaysWorkQueue.entrySet().iterator(); iter.hasNext();) {
            final var tenantDeviceToGatewayEntry = iter.next();
            final String tenantId = tenantDeviceToGatewayEntry.getKey().one();
            if (tenantsToProcess == null || tenantsToProcess.contains(tenantId)) {
                if (!deviceToGatewayMapPerTenant.containsKey(tenantId)
                        && deviceToGatewayMapPerTenant.size() >= SET_LAST_KNOWN_GATEWAY_UPDATE_MAX_PARALLEL_REQ) {
                    tenantsWithRequestLimitReached.add(tenantId);
                } else {
                    deviceToGatewayMapPerTenant.putIfAbsent(tenantId, new HashMap<>());
                    final Map<String, String> deviceToGatewayMap = deviceToGatewayMapPerTenant.get(tenantId);
                    if (deviceToGatewayMap.size() < SET_LAST_KNOWN_GATEWAY_UPDATE_MAX_ENTRIES) {
                        deviceToGatewayMap.put(tenantDeviceToGatewayEntry.getKey().two(),
                                tenantDeviceToGatewayEntry.getValue());
                        iter.remove();
                    } else {
                        tenantsWithRequestLimitReached.add(tenantId);
                    }
                }
            }
        }
        final List<Future<Void>> resultFutures = new ArrayList<>();
        deviceToGatewayMapPerTenant.forEach((tenantId, deviceToGatewayMap) -> {
            resultFutures.add(setLastKnownGateways(tenantId, deviceToGatewayMap, currentSpan.context()));
        });
        Future.join(resultFutures)
                .onComplete(ar -> {
                    if (ar.failed()) {
                        TracingHelper.logError(currentSpan, ar.cause());
                    }
                    if (stopped) {
                        currentSpan.finish();
                    } else if (!lastKnownGatewaysWorkQueue.isEmpty()) {
                        // still work to do
                        final long millisToWaitForNextInvocation = SET_LAST_KNOWN_GATEWAY_UPDATE_INTERVAL_MILLIS - Duration
                                .between(processingStart, Instant.now(clock)).toMillis();

                        if (millisToWaitForNextInvocation < 1) {
                            // processing took longer than the update interval - start a new run right away (not restricted to tenantsWithRequestLimitReached)
                            if (!tenantsWithRequestLimitReached.isEmpty()) {
                                currentSpan.log(String.format("still remaining entries to be set for %d tenants - will be handled in next overall run",
                                        tenantsWithRequestLimitReached.size()));
                                log.info("processLastKnownGatewaysWorkQueue: not all entries could be set during update interval; current queue size: {}",
                                        lastKnownGatewaysWorkQueue.size());
                            }
                            currentSpan.finish();
                            processLastKnownGatewaysWorkQueue(null, null, null);

                        } else if (!tenantsWithRequestLimitReached.isEmpty()) {
                            // not all entries could be handled in this run - start a new run with just these tenants
                            // (no need to already do the requests for the other tenants, for which entries where added in between)
                            currentSpan.log(String.format("starting another round of requests for %d tenants (request size/count limit was reached)",
                                    tenantsWithRequestLimitReached.size()));
                            processLastKnownGatewaysWorkQueue(processingStart, tenantsWithRequestLimitReached, currentSpan);

                        } else {
                            log.debug("schedule next processLastKnownGatewaysWorkQueue invocation in {}ms", millisToWaitForNextInvocation);
                            currentSpan.finish();
                            lastKnownGatewaysUpdateTimerId = connection.getVertx().setTimer(
                                    millisToWaitForNextInvocation,
                                    tid -> processLastKnownGatewaysWorkQueue(null, null, null));
                        }
                    } else {
                        // no work left
                        currentSpan.finish();
                        lastKnownGatewaysUpdateTimerId = null;
                    }
                });
    }

    /**
     * For a given list of device and gateway combinations, sets the gateway as the last gateway that acted on behalf
     * of the device.
     *
     * @param tenantId The tenant id.
     * @param deviceToGatewayMap The map associating device identifiers with the corresponding last gateway.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the entries were successfully set.
     *         Otherwise the future will be failed with a {@code org.eclipse.hono.client.ServiceInvocationException}.
     *         The outcome is indeterminate if any of the entries cannot be processed by the Command Router service
     *         implementation. In such a case, client code should assume that none of the entries have been updated.
     */
    protected Future<Void> setLastKnownGateways(final String tenantId, final Map<String, String> deviceToGatewayMap,
            final SpanContext context) {
        final Span currentSpan;
        final Future<RequestResponseResult<JsonObject>> resultTracker;
        if (deviceToGatewayMap.size() == 1) {
            // use single entry operation so that traces with device and gateway are created
            final Map.Entry<String, String> entry = deviceToGatewayMap.entrySet().iterator().next();
            final String deviceId = entry.getKey();
            final String gatewayId = entry.getValue();
            final Map<String, Object> properties = createDeviceIdProperties(deviceId);
            properties.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);
            currentSpan = newChildSpan(context, "set last known gateway for device");
            TracingHelper.setDeviceTags(currentSpan, tenantId, deviceId);
            currentSpan.setTag(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);

            resultTracker = getOrCreateClient(tenantId)
                    .compose(client -> client.createAndSendRequest(
                            CommandRouterConstants.CommandRouterAction.SET_LAST_KNOWN_GATEWAY.getSubject(),
                            properties,
                            null,
                            null,
                            this::getRequestResponseResult,
                            currentSpan));
        } else {
            currentSpan = newChildSpan(context, "set last known gateways for tenant devices");
            TracingHelper.setDeviceTags(currentSpan, tenantId, null);
            currentSpan.log(Map.of("no_of_entries", deviceToGatewayMap.size()));

            final JsonObject payload = new JsonObject();
            deviceToGatewayMap.forEach(payload::put);
            resultTracker = getOrCreateClient(tenantId)
                    .compose(client -> client.createAndSendRequest(
                            CommandRouterConstants.CommandRouterAction.SET_LAST_KNOWN_GATEWAY.getSubject(),
                            null,
                            payload.toBuffer(),
                            MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
                            this::getRequestResponseResult,
                            currentSpan));
        }
        return mapResultAndFinishSpan(
                resultTracker,
                result -> {
                    switch (result.getStatus()) {
                    case HttpURLConnection.HTTP_NO_CONTENT:
                        return null;
                    default:
                        throw StatusCodeMapper.from(result);
                    }
                },
                currentSpan)
                .onFailure(thr -> log.debug("failed to set last known gateway(s) for tenant [{}]", tenantId, thr))
                .mapEmpty();
    }

    @Override
    public Future<Void> registerCommandConsumer(
            final String tenantId,
            final String deviceId,
            final boolean sendEvent,
            final String adapterInstanceId,
            final Duration lifespan,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        final int lifespanSeconds = lifespan != null && lifespan.getSeconds() <= Integer.MAX_VALUE ? (int) lifespan.getSeconds() : -1;
        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(CommandConstants.MSG_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        properties.put(MessageHelper.APP_PROPERTY_LIFESPAN, lifespanSeconds);

        final Span currentSpan = newChildSpan(context, "register command consumer");
        TracingHelper.setDeviceTags(currentSpan, tenantId, deviceId);
        TracingHelper.TAG_ADAPTER_INSTANCE_ID.set(currentSpan, adapterInstanceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_LIFESPAN, lifespanSeconds);

        properties.put(MessageHelper.APP_PROPERTY_SEND_EVENT, sendEvent);

        final Future<RequestResponseResult<JsonObject>> resultTracker = getOrCreateClient(tenantId)
                .compose(client -> client.createAndSendRequest(
                        CommandRouterConstants.CommandRouterAction.REGISTER_COMMAND_CONSUMER.getSubject(),
                        properties,
                        null,
                        null,
                        this::getRequestResponseResult,
                        currentSpan));
        return mapResultAndFinishSpan(resultTracker, result -> {
            switch (result.getStatus()) {
                case HttpURLConnection.HTTP_NO_CONTENT:
                    return null;
                default:
                    throw StatusCodeMapper.from(result);
            }
        }, currentSpan).mapEmpty();
    }

    @Override
    public Future<Void> unregisterCommandConsumer(
            final String tenantId,
            final String deviceId,
            final boolean sendEvent,
            final String adapterInstanceId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(CommandConstants.MSG_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);

        final Span currentSpan = newChildSpan(context, "unregister command consumer");
        TracingHelper.setDeviceTags(currentSpan, tenantId, deviceId);
        TracingHelper.TAG_ADAPTER_INSTANCE_ID.set(currentSpan, adapterInstanceId);

        properties.put(MessageHelper.APP_PROPERTY_SEND_EVENT, sendEvent);

        return getOrCreateClient(tenantId)
                .compose(client -> client.createAndSendRequest(
                        CommandRouterConstants.CommandRouterAction.UNREGISTER_COMMAND_CONSUMER.getSubject(),
                        properties,
                        null,
                        null,
                        this::getRequestResponseResult,
                        currentSpan))
                // not using mapResultAndFinishSpan() in order to skip logging PRECON_FAILED result as error
                // (may have been trying to remove an expired entry)
                .recover(t -> {
                    Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(t));
                    TracingHelper.logError(currentSpan, t);
                    return Future.failedFuture(t);
                })
                .map(resultValue -> {
                    Tags.HTTP_STATUS.set(currentSpan, resultValue.getStatus());
                    if (resultValue.isError() && resultValue.getStatus() != HttpURLConnection.HTTP_PRECON_FAILED) {
                        Tags.ERROR.set(currentSpan, Boolean.TRUE);
                    }

                    switch (resultValue.getStatus()) {
                    case HttpURLConnection.HTTP_NO_CONTENT:
                        return null;
                    default:
                        throw StatusCodeMapper.from(resultValue);
                    }
                })
                .onComplete(v -> currentSpan.finish())
                .mapEmpty();
    }

    @Override
    public Future<Void> enableCommandRouting(final List<String> tenantIds, final SpanContext context) {

        Objects.requireNonNull(tenantIds);
        if (tenantIds.isEmpty()) {
            return Future.succeededFuture();
        }
        final Span currentSpan = newChildSpan(context, "enable command routing");
        currentSpan.log(Map.of("no_of_tenants", tenantIds.size()));
        final JsonArray payload = new JsonArray(tenantIds);
        final Future<RequestResponseResult<JsonObject>> resultTracker = getOrCreateClient(tenantIds.get(0))
                .compose(client -> client.createAndSendRequest(
                        CommandRouterConstants.CommandRouterAction.ENABLE_COMMAND_ROUTING.getSubject(),
                        null,
                        payload.toBuffer(),
                        MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
                        this::getRequestResponseResult,
                        currentSpan));
        return mapResultAndFinishSpan(resultTracker, result -> {
            switch (result.getStatus()) {
                case HttpURLConnection.HTTP_NO_CONTENT:
                    log.info("successfully enabled routing of commands for {} tenants in Command Router", tenantIds.size());
                    return null;
                default:
                    final ServiceInvocationException e = StatusCodeMapper.from(result);
                    log.info("failed to enable routing of commands in Command Router", e);
                    throw e;
            }
        }, currentSpan).mapEmpty();
    }
}
