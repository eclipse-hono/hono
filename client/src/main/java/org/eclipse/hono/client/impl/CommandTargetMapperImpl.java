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

package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A component for mapping an incoming command to the gateway (if applicable)
 * and protocol adapter instance that can handle it.
 */
public class CommandTargetMapperImpl implements CommandTargetMapper {

    private static final Logger LOG = LoggerFactory.getLogger(CommandTargetMapperImpl.class);

    private final Tracer tracer;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private CommandTargetMapperContext mapperContext;

    /**
     * Creates a new GatewayMapperImpl instance.
     *
     * @param tracer The tracer instance.
     * @throws NullPointerException if tracer is {@code null}.
     */
    public CommandTargetMapperImpl(final Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer);
    }

    @Override
    public void initialize(final CommandTargetMapperContext context) {
        this.mapperContext = Objects.requireNonNull(context);
        initialized.set(true);
    }

    @Override
    public final Future<JsonObject> getTargetGatewayAndAdapterInstance(final String tenantId, final String deviceId, final SpanContext context) {
        if (!initialized.get()) {
            LOG.error("not initialized");
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR));
        }
        final Span span = TracingHelper
                .buildChildSpan(tracer, context, "get target gateway and adapter instance",
                        CommandTargetMapper.class.getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .start();

        return mapperContext.getViaGateways(tenantId, deviceId, span.context())
                .recover(t -> {
                    LOG.debug("Error retrieving gateways authorized to act on behalf of device [tenant-id: {}, device-id: {}]",
                            tenantId, deviceId, t);
                    return Future.failedFuture(t);
                }).compose(viaGateways -> {
                    return mapperContext.getCommandHandlingAdapterInstances(tenantId, deviceId, viaGateways, span.context())
                            .compose(resultJson -> determineTargetInstanceJson(resultJson, deviceId, viaGateways, span));
                }).map(result -> {
                    span.finish();
                    return result;
                }).recover(t -> {
                    LOG.debug("Error getting target gateway and adapter instance", t);
                    TracingHelper.logError(span, t);
                    Tags.HTTP_STATUS.set(span, ServiceInvocationException.extractStatusCode(t));
                    span.finish();
                    return Future.failedFuture(t);
                });
    }

    private Future<JsonObject> determineTargetInstanceJson(final JsonObject adapterInstancesJson, final String deviceId,
            final List<String> viaGateways, final Span span) {
        final JsonArray instancesArray = adapterInstancesJson.getJsonArray(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES);
        if (instancesArray == null || instancesArray.isEmpty()) {
            return createAndLogInternalServerErrorFuture(span, "Invalid result JSON; field '"
                    + DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES + "' is null or empty");
        }

        final JsonObject targetInstanceObject;
        try {
            if (instancesArray.size() == 1) {
                targetInstanceObject = instancesArray.getJsonObject(0);
            } else {
                targetInstanceObject = chooseTargetGatewayAndAdapterInstance(instancesArray);
            }
        } catch (final ClassCastException e) {
            return createAndLogInternalServerErrorFuture(span, "Invalid result JSON: " + e.toString());
        }
        final String targetDevice = targetInstanceObject.getString(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID);
        final String targetAdapterInstance = targetInstanceObject.getString(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID);
        if (targetDevice == null || targetAdapterInstance == null) {
            return createAndLogInternalServerErrorFuture(span, "Invalid result JSON, missing target device and/or adapter instance");
        }
        if (!targetDevice.equals(deviceId)) {
            // target device is a gateway
            if (!viaGateways.contains(targetDevice)) {
                return createAndLogInternalServerErrorFuture(span,
                        "Invalid result JSON, target gateway " + targetDevice + " is not in via gateways list");
            }
            span.setTag(MessageHelper.APP_PROPERTY_GATEWAY_ID, targetDevice);
        }

        final String choiceInfo = instancesArray.size() > 1 ? " chosen from " + instancesArray.size() + " entries" : "";
        final String gatewayInfo = !targetDevice.equals(deviceId) ? " gateway '" + targetDevice + "' and" : "";
        final String infoMsg = String.format("command target%s:%s adapter instance '%s'", choiceInfo, gatewayInfo, targetAdapterInstance);
        LOG.debug(infoMsg);
        span.log(infoMsg);

        span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, targetAdapterInstance);
        return Future.succeededFuture(targetInstanceObject);
    }

    private Future<JsonObject> createAndLogInternalServerErrorFuture(final Span span, final String errorMessage) {
        LOG.error(errorMessage);
        TracingHelper.logError(span, errorMessage);
        return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR));
    }

    /**
     * Chooses the target gateway and adapter instance from the given list of entries.
     * <p>
     * This method returns first entry from the given list.
     * <p>
     * Subclasses may override this method in order to apply a different algorithm.
     *
     * @param instancesArray The JSON array containing the target gateway and adapter instance entries to choose from.
     * @return The chosen JSON object.
     */
    protected JsonObject chooseTargetGatewayAndAdapterInstance(final JsonArray instancesArray) {
        return instancesArray.getJsonObject(0);
    }

}
