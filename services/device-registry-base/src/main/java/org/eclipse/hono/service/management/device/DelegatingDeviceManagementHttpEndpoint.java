/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.management.AbstractDelegatingRegistryHttpEndpoint;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistryManagementConstants;

import io.opentracing.Span;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * An {@code HttpEndpoint} for managing device registration information.
 * <p>
 * This endpoint implements the <em>device</em> resources of Hono's
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>.
 * It receives HTTP requests representing operation invocations and executes the matching service
 * implementation methods. The outcome is then returned to the peer in the HTTP response.
 *
 * @param <S> The type of service this endpoint delegates to.
 */
public class DelegatingDeviceManagementHttpEndpoint<S extends DeviceManagementService> extends AbstractDelegatingRegistryHttpEndpoint<S, ServiceConfigProperties> {

    static final int DEFAULT_PAGE_OFFSET = 0;
    static final int DEFAULT_PAGE_SIZE = 30;
    static final int MAX_PAGE_SIZE = 200;
    static final int MIN_PAGE_OFFSET = 0;
    static final int MIN_PAGE_SIZE = 0;

    private static final String SPAN_NAME_CREATE_DEVICE = "create Device from management API";
    private static final String SPAN_NAME_GET_DEVICE = "get Device from management API";
    private static final String SPAN_NAME_SEARCH_DEVICES = "search Devices from management API";
    private static final String SPAN_NAME_UPDATE_DEVICE = "update Device from management API";
    private static final String SPAN_NAME_REMOVE_DEVICE = "remove Device from management API";
    private static final String SPAN_NAME_REMOVE_DEVICES_OF_TENANT = "remove all of Tenant's Devices from management API";

    private static final String DEVICE_MANAGEMENT_ENDPOINT_NAME = String.format("%s/%s",
                    RegistryManagementConstants.API_VERSION,
                    RegistryManagementConstants.DEVICES_HTTP_ENDPOINT);

    /**
     * Creates an endpoint for a service instance.
     *
     * @param vertx The vert.x instance to use.
     * @param service The service to delegate to.
     * @throws NullPointerException if any of the parameters are {@code null};
     */
    public DelegatingDeviceManagementHttpEndpoint(final Vertx vertx, final S service) {
        super(vertx, service);
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
        router.route(pathWithTenant).handler(createCorsHandler(config.getCorsAllowedOrigin(), Set.of(HttpMethod.POST)));
        router.route(pathWithTenantAndDeviceId).handler(createDefaultCorsHandler(config.getCorsAllowedOrigin()));

        final BodyHandler bodyHandler = BodyHandler.create(DEFAULT_UPLOADS_DIRECTORY);
        bodyHandler.setBodyLimit(config.getMaxPayloadSize());

        // CREATE device with auto-generated deviceID
        router.post(pathWithTenant)
                .handler(bodyHandler)
                .handler(this::extractOptionalJsonPayload)
                .handler(this::doCreateDevice);

        // CREATE device
        router.post(pathWithTenantAndDeviceId)
                .handler(bodyHandler)
                .handler(this::extractOptionalJsonPayload)
                .handler(this::doCreateDevice);

        // SEARCH devices
        router.get(pathWithTenant)
                .handler(this::doSearchDevices);

        // GET device
        router.get(pathWithTenantAndDeviceId)
                .handler(this::doGetDevice);

        // UPDATE existing device
        router.put(pathWithTenantAndDeviceId)
                .handler(bodyHandler)
                .handler(this::extractRequiredJsonPayload)
                .handler(this::extractIfMatchVersionParam)
                .handler(this::doUpdateDevice);

        // DELETE device
        router.delete(pathWithTenantAndDeviceId)
                .handler(this::extractIfMatchVersionParam)
                .handler(this::doDeleteDevice);

        // DELETE all of a tenant's devices
        router.delete(pathWithTenant)
                .handler(this::doDeleteDevicesOfTenant);

    }

    private void doGetDevice(final RoutingContext ctx) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_GET_DEVICE,
                getClass().getSimpleName()
        ).start();

        final Future<String> tenantId = getRequestParameter(ctx, PARAM_TENANT_ID, getPredicate(config.getTenantIdPattern(), false));
        final Future<String> deviceId = getRequestParameter(ctx, PARAM_DEVICE_ID, getPredicate(config.getDeviceIdPattern(), false));

        CompositeFuture.all(tenantId, deviceId)
            .compose(ok -> {
                TracingHelper.setDeviceTags(span, tenantId.result(), deviceId.result());
                logger.debug("retrieving device [tenant: {}, device-id: {}]", tenantId.result(), deviceId.result());
                return getService().readDevice(tenantId.result(), deviceId.result(), span);
            })
            .onSuccess(operationResult -> writeResponse(ctx, operationResult, span))
            .onFailure(t -> failRequest(ctx, t, span))
            .onComplete(s -> span.finish());
    }

    private void doSearchDevices(final RoutingContext ctx) {
        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_SEARCH_DEVICES,
                getClass().getSimpleName()).start();

        final String tenantId = getTenantParam(ctx);
        final Future<Integer> pageSize = getRequestParameter(
                ctx,
                RegistryManagementConstants.PARAM_PAGE_SIZE,
                DEFAULT_PAGE_SIZE,
                CONVERTER_INT,
                value -> value >= MIN_PAGE_SIZE && value <= MAX_PAGE_SIZE);
        final Future<Integer> pageOffset = getRequestParameter(
                ctx,
                RegistryManagementConstants.PARAM_PAGE_OFFSET,
                DEFAULT_PAGE_OFFSET,
                CONVERTER_INT,
                value -> value >= MIN_PAGE_OFFSET);
        final Future<List<Filter>> filters = decodeJsonFromRequestParameter(ctx,
                RegistryManagementConstants.PARAM_FILTER_JSON, Filter.class);
        final Future<List<Sort>> sortOptions = decodeJsonFromRequestParameter(ctx,
                RegistryManagementConstants.PARAM_SORT_JSON, Sort.class);

        CompositeFuture.all(pageSize, pageOffset, filters, sortOptions)
                .onSuccess(ok -> TracingHelper.TAG_TENANT_ID.set(span, tenantId))
                .compose(ok -> getService().searchDevices(
                        tenantId,
                        pageSize.result(),
                        pageOffset.result(),
                        filters.result(),
                        sortOptions.result(),
                        span))
                .onSuccess(operationResult -> writeResponse(ctx, operationResult, span))
                .onFailure(t -> failRequest(ctx, t, span))
                .onComplete(s -> span.finish());
    }

    private void doCreateDevice(final RoutingContext ctx) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_CREATE_DEVICE,
                getClass().getSimpleName()
        ).start();

        final Future<String> tenantId = getRequestParameter(ctx, PARAM_TENANT_ID, getPredicate(config.getTenantIdPattern(), false));
        final Future<String> deviceId = getRequestParameter(ctx, PARAM_DEVICE_ID, getPredicate(config.getDeviceIdPattern(), true));
        final Future<Device> device = fromPayload(ctx);

        CompositeFuture.all(tenantId, deviceId, device)
            .compose(ok -> {
                final Optional<String> did = Optional.ofNullable(deviceId.result());
                TracingHelper.TAG_TENANT_ID.set(span, tenantId.result());
                did.ifPresent(s -> TracingHelper.TAG_DEVICE_ID.set(span, s));
                logger.debug("creating device [tenant: {}, device-id: {}]", tenantId.result(), did.orElse("<auto>"));
                return getService().createDevice(tenantId.result(), did, device.result(), span);
            })
            .onSuccess(operationResult -> writeResponse(ctx, operationResult, (responseHeaders, status) -> {
                    Optional.ofNullable(operationResult.getPayload())
                        .map(Id::getId)
                        .ifPresent(id -> responseHeaders.set(
                                HttpHeaders.LOCATION,
                                String.format("/%s/%s/%s", getName(), tenantId.result(), id)));
                }, span))
            .onFailure(t -> failRequest(ctx, t, span))
            .onComplete(s -> span.finish());
    }

    private void doUpdateDevice(final RoutingContext ctx) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_UPDATE_DEVICE,
                getClass().getSimpleName()
        ).start();

        final Future<String> tenantId = getRequestParameter(ctx, PARAM_TENANT_ID, getPredicate(config.getTenantIdPattern(), false));
        final Future<String> deviceId = getRequestParameter(ctx, PARAM_DEVICE_ID, getPredicate(config.getDeviceIdPattern(), false));
        final Future<Device> device = fromPayload(ctx);

        CompositeFuture.all(tenantId, deviceId, device)
            .compose(ok -> {
                TracingHelper.setDeviceTags(span, tenantId.result(), deviceId.result());
                logger.debug("updating device [tenant: {}, device-id: {}]", tenantId.result(), deviceId.result());
                final Optional<String> resourceVersion = Optional.ofNullable(ctx.get(KEY_RESOURCE_VERSION));
                return getService().updateDevice(tenantId.result(), deviceId.result(), device.result(), resourceVersion, span);
            })
            .onSuccess(operationResult -> writeResponse(ctx, operationResult, span))
            .onFailure(t -> failRequest(ctx, t, span))
            .onComplete(s -> span.finish());
    }

    private void doDeleteDevice(final RoutingContext ctx) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_REMOVE_DEVICE,
                getClass().getSimpleName()
        ).start();

        final Future<String> tenantId = getRequestParameter(ctx, PARAM_TENANT_ID, getPredicate(config.getTenantIdPattern(), false));
        final Future<String> deviceId = getRequestParameter(ctx, PARAM_DEVICE_ID, getPredicate(config.getDeviceIdPattern(), false));

        CompositeFuture.all(tenantId, deviceId)
            .compose(ok -> {
                TracingHelper.setDeviceTags(span, tenantId.result(), deviceId.result());
                logger.debug("removing device [tenant: {}, device-id: {}]", tenantId.result(), deviceId.result());
                final Optional<String> resourceVersion = Optional.ofNullable(ctx.get(KEY_RESOURCE_VERSION));
                return getService().deleteDevice(tenantId.result(), deviceId.result(), resourceVersion, span);
            })
            .onSuccess(result -> writeResponse(ctx, result, span))
            .onFailure(t -> failRequest(ctx, t, span))
            .onComplete(s -> span.finish());
    }

    private void doDeleteDevicesOfTenant(final RoutingContext ctx) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_REMOVE_DEVICES_OF_TENANT,
                getClass().getSimpleName()
        ).start();

        getRequestParameter(ctx, PARAM_TENANT_ID, getPredicate(config.getTenantIdPattern(), false))
            .compose(tenantId -> {
                TracingHelper.TAG_TENANT_ID.set(span, tenantId);
                logger.debug("removing all devices of tenant [tenant: {}]", tenantId);
                return getService().deleteDevicesOfTenant(tenantId, span);
            })
            .onSuccess(result -> writeResponse(ctx, result, span))
            .onFailure(t -> failRequest(ctx, t, span))
            .onComplete(s -> span.finish());
    }

    /**
     * Gets the device from the request body.
     *
     * @param ctx The context to retrieve the request body from.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the request body is either empty or contains a JSON
     *         object that complies with the Device Registry Management API's Device object definition.
     *         Otherwise, the future will be failed with a {@link org.eclipse.hono.client.ClientErrorException}
     *         containing a corresponding status code.
     * @throws NullPointerException If the context is {@code null}.
     */
    private static Future<Device> fromPayload(final RoutingContext ctx) {

        Objects.requireNonNull(ctx);

        final Promise<Device> result = Promise.promise();
        Optional.ofNullable(ctx.get(KEY_REQUEST_BODY))
            .map(JsonObject.class::cast)
            .ifPresentOrElse(
                    // validate payload
                    json -> {
                        try {
                            result.complete(json.mapTo(Device.class));
                        } catch (final DecodeException | IllegalArgumentException e) {
                            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                                    "request does not contain a valid Device object", e));
                        }
                    },
                    // payload was empty
                    () -> result.complete(new Device()));
        return result.future();
    }

}
