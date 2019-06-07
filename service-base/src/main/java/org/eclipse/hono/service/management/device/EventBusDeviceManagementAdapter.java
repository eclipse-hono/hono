/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.device;

import static org.eclipse.hono.service.management.Util.newChildSpan;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.EventBusMessage;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

/**
 * An adapter, hooking up the {@link DeviceManagementService} with the event bus.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages via vert.x' event
 * bus and route them to specific methods corresponding to the operation indicated in the message.
 *
 * @param <T> The type of configuration properties this service requires.
 */
public abstract class EventBusDeviceManagementAdapter<T> extends EventBusService<T>
        implements Verticle {

    private static final String SPAN_NAME_CREATE_DEVICE = "create Device from management API";
    private static final String SPAN_NAME_GET_DEVICE = "get Device from management API";
    private static final String SPAN_NAME_UPDATE_DEVICE = "update Device from management API";
    private static final String SPAN_NAME_REMOVE_DEVICE = "remove Device from management API";

    /**
     * The service to forward requests to.
     * 
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract DeviceManagementService getService();

    @Override
    protected String getEventBusAddress() {
        return RegistryManagementConstants.EVENT_BUS_ADDRESS_DEVICE_MANAGEMENT_IN;
    }

    /**
     * Processes a device registration API request received via the vert.x event bus.
     * <p>
     * This method validates the request parameters against the Device Registration API specification before invoking
     * the corresponding {@code RegistrationService} methods.
     *
     * @param requestMessage The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    @Override
    public final Future<EventBusMessage> processRequest(final EventBusMessage requestMessage) {

        Objects.requireNonNull(requestMessage);

        switch (requestMessage.getOperation()) {
        case RegistryManagementConstants.ACTION_CREATE:
            return processCreateRequest(requestMessage);
        case RegistryManagementConstants.ACTION_GET:
            return processGetRequest(requestMessage);
        case RegistryManagementConstants.ACTION_UPDATE:
            return processUpdateRequest(requestMessage);
        case RegistryManagementConstants.ACTION_DELETE:
            return processDeleteRequest(requestMessage);
        default:
            return processCustomDeviceMessage(requestMessage);
        }
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom operations that are not defined by
     * Hono's Device Registration API.
     * <p>
     * This default implementation simply returns a future that is failed with a {@link ClientErrorException} with an
     * error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<EventBusMessage> processCustomDeviceMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    private static Future<Device> deviceFromPayload(final EventBusMessage request) {
        try {
            return Future.succeededFuture(fromPayload(request));
        } catch (final IllegalArgumentException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, e));
        }
    }

    static Device fromPayload(final EventBusMessage request) throws ClientErrorException {
        return Optional.ofNullable(request.getJsonPayload())
                .map(json -> json.mapTo(Device.class))
                .orElseGet(Device::new);
    }

    private Future<EventBusMessage> processCreateRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final Optional<String> deviceId = Optional.ofNullable(request.getDeviceId());
        final SpanContext spanContext = request.getSpanContext();

        final Future<Device> deviceFuture = deviceFromPayload(request);
        final Span span = newChildSpan(SPAN_NAME_CREATE_DEVICE, spanContext, tracer, tenantId, deviceId.orElse("unspecified"), getClass().getSimpleName());

        return deviceFuture.compose(device -> {
            log.debug("registering device [{}] for tenant [{}]", deviceId.orElse("<auto>"), tenantId);
            final Future<OperationResult<Id>> result = Future.future();
            getService().createDevice(tenantId, deviceId, device, span, result);
            return result.map(res -> {
                final String createdDeviceId = Optional.ofNullable(res.getPayload()).map(Id::getId).orElse(null);
                return res.createResponse(request, JsonObject::mapFrom).setDeviceId(createdDeviceId);
            });
        });

    }

    private Future<EventBusMessage> processGetRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final SpanContext spanContext = request.getSpanContext();

        final Span span = newChildSpan(SPAN_NAME_GET_DEVICE, spanContext, tracer, tenantId, deviceId, getClass().getSimpleName());

        if (tenantId == null || deviceId == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }

        log.debug("retrieving device [{}] of tenant [{}]", deviceId, tenantId);
        final Future<OperationResult<Device>> result = Future.future();
        getService().readDevice(tenantId, deviceId, span, result);
        return result.map(res -> {
            return res.createResponse(request, JsonObject::mapFrom).setDeviceId(deviceId);
        });

    }

    private Future<EventBusMessage> processUpdateRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final Optional<String> resourceVersion = Optional.ofNullable(request.getResourceVersion());
        final SpanContext spanContext = request.getSpanContext();

        final Span span = newChildSpan(SPAN_NAME_UPDATE_DEVICE, spanContext, tracer, tenantId, deviceId, getClass().getSimpleName());

        if (tenantId == null || deviceId == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }

        final Future<Device> deviceFuture = deviceFromPayload(request);

        return deviceFuture.compose(device -> {

            log.debug("updating registration information for device [{}] of tenant [{}]", deviceId, tenantId);
            final Future<OperationResult<Id>> result = Future.future();
            getService().updateDevice(tenantId, deviceId, device, resourceVersion, span, result);
            return result.map(res -> {
                return res.createResponse(request, JsonObject::mapFrom).setDeviceId(deviceId);
            });

        });
    }

    private Future<EventBusMessage> processDeleteRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final Optional<String> resourceVersion = Optional.ofNullable(request.getResourceVersion());
        final SpanContext spanContext = request.getSpanContext();

        final Span span = newChildSpan(SPAN_NAME_REMOVE_DEVICE, spanContext, tracer, tenantId, deviceId, getClass().getSimpleName());

        if (tenantId == null || deviceId == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }

        log.debug("deleting device [{}] of tenant [{}]", deviceId, tenantId);
        final Future<Result<Void>> result = Future.future();
        getService().deleteDevice(tenantId, deviceId, resourceVersion, span, result);
        return result.map(res -> {
            return res.createResponse(request, id -> null).setDeviceId(deviceId);
        });

    }

}
