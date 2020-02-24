/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.management.device;

import java.net.HttpURLConnection;
import java.util.EnumSet;
import java.util.Optional;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.Util;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * An {@code HttpEndpoint} for managing device registration information.
 * <p>
 * This endpoint implements the <em>device</em> resources of Hono's
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>.
 * It receives HTTP requests representing operation invocations and executes the matching service
 * implementation methods. The outcome is then returned to the peer in the HTTP response.
 */
public abstract class AbstractDeviceManagementHttpEndpoint extends AbstractHttpEndpoint<ServiceConfigProperties> {

    private static final String SPAN_NAME_CREATE_DEVICE = "create Device from management API";
    private static final String SPAN_NAME_GET_DEVICE = "get Device from management API";
    private static final String SPAN_NAME_UPDATE_DEVICE = "update Device from management API";
    private static final String SPAN_NAME_REMOVE_DEVICE = "remove Device from management API";

    private static final String DEVICE_MANAGEMENT_ENDPOINT_NAME = String.format("%s/%s",
                    RegistryManagementConstants.API_VERSION,
                    RegistryManagementConstants.DEVICES_HTTP_ENDPOINT);

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public AbstractDeviceManagementHttpEndpoint(final Vertx vertx) {
        super(vertx);
    }

    @Override
    public String getName() {
        return DEVICE_MANAGEMENT_ENDPOINT_NAME;
    }

    @Override
    public void addRoutes(final Router router) {

        final String pathWithTenant = String.format("/%s/:%s", getName(), PARAM_TENANT_ID);
        final String pathWithTenantAndDeviceId = String.format("/%s/:%s/:%s", getName(), PARAM_TENANT_ID,
                PARAM_DEVICE_ID);

        // Add CORS handler
        router.route(pathWithTenant).handler(createCorsHandler(config.getCorsAllowedOrigin(), EnumSet.of(HttpMethod.POST)));
        router.route(pathWithTenantAndDeviceId).handler(createDefaultCorsHandler(config.getCorsAllowedOrigin()));


        // CREATE device with auto-generated deviceID
        router.post(pathWithTenant)
                .handler(this::extractOptionalJsonPayload)
                .handler(this::doCreateDevice);

        // CREATE device
        router.post(pathWithTenantAndDeviceId)
                .handler(this::extractOptionalJsonPayload)
                .handler(this::doCreateDevice);

        // GET device
        router.get(pathWithTenantAndDeviceId)
                .handler(this::doGetDevice);

        // UPDATE existing device
        router.put(pathWithTenantAndDeviceId)
                .handler(this::extractRequiredJsonPayload)
                .handler(this::extractIfMatchVersionParam)
                .handler(this::doUpdateDevice);

        // DELETE device
        router.delete(pathWithTenantAndDeviceId)
                .handler(this::extractIfMatchVersionParam)
                .handler(this::doDeleteDevice);
    }

    /**
     * The service to forward requests to.
     *
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract DeviceManagementService getService();

    private void doGetDevice(final RoutingContext ctx) {

        final Span span = Util.newChildSpan(SPAN_NAME_GET_DEVICE, TracingHandler.serverSpanContext(ctx), tracer, getClass().getSimpleName());

        final String deviceId = getMandatoryRequestParam(PARAM_DEVICE_ID, ctx, span);
        final String tenantId = getMandatoryRequestParam(PARAM_TENANT_ID, ctx, span);

        final HttpServerResponse response = ctx.response();

        logger.debug("retrieving device [{}] of tenant [{}]", deviceId, tenantId);
        final Promise<OperationResult<Device>> result = Promise.promise();
        result.future().setHandler(handler -> {
            final OperationResult<Device> operationResult = handler.result();
            final int status = operationResult.getStatus();
            response.setStatusCode(status);
            switch (status) {
                case HttpURLConnection.HTTP_OK:
                    operationResult.getResourceVersion().ifPresent(v -> response.putHeader(HttpHeaders.ETAG, v));
                    HttpUtils.setResponseBody(response, JsonObject.mapFrom(operationResult.getPayload()));
                    // falls through intentionally
                default:
                    Tags.HTTP_STATUS.set(span, status);
                    span.finish();
                    response.end();
            }
        });

        getService().readDevice(tenantId, deviceId, span, result);
    }

    private void doCreateDevice(final RoutingContext ctx) {

        final Span span = Util.newChildSpan(SPAN_NAME_CREATE_DEVICE, TracingHandler.serverSpanContext(ctx), tracer, getClass().getSimpleName());

        final String tenantId = getMandatoryRequestParam(PARAM_TENANT_ID, ctx, span);
        final String deviceId = getRequestParam(PARAM_DEVICE_ID, ctx, span, true);

        final JsonObject payload = ctx.get(KEY_REQUEST_BODY);
        if (payload == null) {
            final String msg = "Missing request body";
            TracingHelper.logError(span, msg);
            Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_BAD_REQUEST);
            HttpUtils.badRequest(ctx, msg);
            span.finish();
            return;
        }

        logger.debug("creating device [tenant: {}, device: {}, payload: {}]", tenantId, deviceId,
                payload);

        final Device device = fromPayload(payload);

        final Promise<OperationResult<Id>> result = Promise.promise();
        result.future().setHandler(handler -> {
            final OperationResult<Id> operationResult = handler.result();

            final String createdDeviceId = Optional.ofNullable(operationResult.getPayload()).map(Id::getId).orElse(null);
            Util.writeOperationResponse(
                    ctx,
                    operationResult,
                    (response) -> response.putHeader(
                            HttpHeaders.LOCATION,
                            String.format("/%s/%s/%s", getName(), tenantId,
                                    createdDeviceId)),
                    span);
        });

        getService().createDevice(tenantId, Optional.ofNullable(deviceId), device, span, result);
    }

    private void doUpdateDevice(final RoutingContext ctx) {

        final Span span = Util.newChildSpan(SPAN_NAME_UPDATE_DEVICE, TracingHandler.serverSpanContext(ctx), tracer, getClass().getSimpleName());

        final String deviceId = getMandatoryRequestParam(PARAM_DEVICE_ID, ctx, span);
        final JsonObject payload = ctx.get(KEY_REQUEST_BODY);
        if (payload != null) {
            payload.remove(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID);
        }
        final String tenantId = getMandatoryRequestParam(PARAM_TENANT_ID, ctx, span);

        logger.debug("updating device [tenant: {}, device: {}, payload: {}]", tenantId, deviceId,
                payload);

        final Optional<String> resourceVersion = Optional.ofNullable(ctx.get(KEY_RESOURCE_VERSION));

        final Device device = fromPayload(payload);
        final Promise<OperationResult<Id>> result = Promise.promise();

        result.future().setHandler(handler -> {
                    Util.writeOperationResponse(ctx, handler.result(), null, span);
                });

        getService().updateDevice(tenantId, deviceId, device, resourceVersion, span, result);
    }

    private void doDeleteDevice(final RoutingContext ctx) {

        final Span span = Util.newChildSpan(SPAN_NAME_REMOVE_DEVICE, TracingHandler.serverSpanContext(ctx), tracer, getClass().getSimpleName());

        final String deviceId = getMandatoryRequestParam(PARAM_DEVICE_ID, ctx, span);
        final String tenantId = getMandatoryRequestParam(PARAM_TENANT_ID, ctx, span);

        logger.debug("removing device [tenant: {}, device: {}]", tenantId, deviceId);

        final Optional<String> resourceVersion = Optional.ofNullable(ctx.get(KEY_RESOURCE_VERSION));

        final Promise<Result<Void>> result = Promise.promise();

        result.future().setHandler(handler -> {
                    Util.writeResponse(ctx, handler.result(), null, span);
                });

        getService().deleteDevice(tenantId, deviceId, resourceVersion, span, result);
    }

    private static Device fromPayload(final JsonObject payload) throws ClientErrorException {
        return Optional.ofNullable(payload)
                .map(json -> json.mapTo(Device.class))
                .orElseGet(Device::new);
    }
}
