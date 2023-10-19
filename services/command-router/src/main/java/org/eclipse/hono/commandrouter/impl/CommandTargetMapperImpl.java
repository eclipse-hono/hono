/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.impl;

import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.registry.DeviceDisabledOrNotRegisteredException;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.RequestResponseApiConstants;
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
    private final DeviceRegistrationClient registrationClient;
    private final DeviceConnectionInfo deviceConnectionInfo;

    /**
     * Creates a new GatewayMapperImpl instance.
     *
     * @param registrationClient The Device Registration service client.
     * @param deviceConnectionInfo The Device Connection service client.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public CommandTargetMapperImpl(
            final DeviceRegistrationClient registrationClient,
            final DeviceConnectionInfo deviceConnectionInfo,
            final Tracer tracer) {

        this.registrationClient = Objects.requireNonNull(registrationClient);
        this.deviceConnectionInfo = Objects.requireNonNull(deviceConnectionInfo);
        this.tracer = Objects.requireNonNull(tracer);
    }

    @Override
    public final Future<JsonObject> getTargetGatewayAndAdapterInstance(final String tenantId, final String deviceId,
            final SpanContext context) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final Span span = TracingHelper
                .buildChildSpan(tracer, context, "get target gateway and adapter instance",
                        CommandTargetMapper.class.getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .start();

        return registrationClient.assertRegistration(tenantId, deviceId, null, span.context())
                .map(RegistrationAssertion::getAuthorizedGateways)
                .recover(t -> {
                    LOG.debug("Error retrieving gateways authorized to act on behalf of device [tenant-id: {}, device-id: {}]",
                            tenantId, deviceId, t);
                    return Future.failedFuture(ServiceInvocationException.extractStatusCode(t) == HttpURLConnection.HTTP_NOT_FOUND
                            ? new DeviceDisabledOrNotRegisteredException(tenantId, HttpURLConnection.HTTP_NOT_FOUND)
                            : t);
                }).compose(viaGateways -> deviceConnectionInfo
                        .getCommandHandlingAdapterInstances(tenantId, deviceId, new HashSet<>(viaGateways), span)
                        .compose(resultJson -> determineTargetInstanceJson(resultJson, deviceId, viaGateways, span)))
                .onFailure(t -> {
                    LOG.debug("Error getting target gateway and adapter instance", t);
                    TracingHelper.logError(span, t);
                    Tags.HTTP_STATUS.set(span, ServiceInvocationException.extractStatusCode(t));
                }).onComplete(ar -> span.finish());
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
            return createAndLogInternalServerErrorFuture(span, "Invalid result JSON: " + e);
        }
        final String targetDevice = targetInstanceObject.getString(RequestResponseApiConstants.FIELD_PAYLOAD_DEVICE_ID);
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
        TracingHelper.TAG_ADAPTER_INSTANCE_ID.set(span, targetAdapterInstance);
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
