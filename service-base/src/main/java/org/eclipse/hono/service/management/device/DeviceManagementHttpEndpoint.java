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

package org.eclipse.hono.service.management.device;

import java.net.HttpURLConnection;
import java.util.EnumSet;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.EventBusMessage;
import org.springframework.beans.factory.annotation.Autowired;

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
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device
 * Registration API</a>. It receives HTTP requests representing operation invocations and sends them to an address on
 * the vertx event bus for processing. The outcome is then returned to the peer in the HTTP response.
 */
public final class DeviceManagementHttpEndpoint extends AbstractHttpEndpoint<ServiceConfigProperties> {

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public DeviceManagementHttpEndpoint(final Vertx vertx) {
        super(vertx);
    }

    @Override
    protected String getEventBusAddress() {
        return RegistryManagementConstants.EVENT_BUS_ADDRESS_DEVICE_MANAGEMENT_IN;
    }

    @Override
    public String getName() {
        return String.format("%s/%s",
                RegistryManagementConstants.API_VERSION,
                RegistryManagementConstants.DEVICES_HTTP_ENDPOINT);
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

    private void doGetDevice(final RoutingContext ctx) {

        final String deviceId = getDeviceIdParam(ctx);
        final String tenantId = getTenantParam(ctx);
        final HttpServerResponse response = ctx.response();

        final JsonObject requestMsg = EventBusMessage.forOperation(RegistryManagementConstants.ACTION_GET)
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .toJson();

        sendAction(ctx, requestMsg, (status, result) -> {
            response.setStatusCode(status);
            switch (status) {
            case HttpURLConnection.HTTP_OK:
                ctx.response()
                        .putHeader(HttpHeaders.ETAG, result.getResourceVersion());
                HttpUtils.setResponseBody(ctx.response(), result.getJsonPayload());
                // falls through intentionally
            default:
                response.end();
            }
        });
    }

    private void doCreateDevice(final RoutingContext ctx) {

        final JsonObject payload = ctx.get(KEY_REQUEST_BODY);
        if (payload == null) {
            HttpUtils.badRequest(ctx, "missing body");
            return;
        }

        final String deviceId = getDeviceIdParam(ctx);
        if (deviceId != null) {
            if (!(deviceId instanceof String)) {
                HttpUtils.badRequest(ctx, String.format("'%s' must be a string",
                        RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID));
            }
        }

        final String tenantId = getTenantParam(ctx);
        logger.debug("creating device [tenant: {}, device: {}, payload: {}]", tenantId, deviceId,
                payload);
        final JsonObject requestMsg = EventBusMessage.forOperation(RegistryManagementConstants.ACTION_CREATE)
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .setJsonPayload(payload)
                .toJson();

        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx,
                status -> status == HttpURLConnection.HTTP_CREATED,
                (response, responseMessage) -> response.putHeader(
                        HttpHeaders.LOCATION,
                        String.format("/%s/%s/%s", getName(), tenantId,
                                responseMessage.getDeviceId()))));
    }

    private void doUpdateDevice(final RoutingContext ctx) {

        final String deviceId = getDeviceIdParam(ctx);
        final JsonObject payload = ctx.get(KEY_REQUEST_BODY);
        if (payload != null) {
            payload.remove(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID);
        }
        final String tenantId = getTenantParam(ctx);
        logger.debug("updating device [tenant: {}, device: {}, payload: {}]", tenantId, deviceId,
                payload);
        final JsonObject requestMsg = EventBusMessage.forOperation(RegistryManagementConstants.ACTION_UPDATE)
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .setJsonPayload(payload)
                .setResourceVersion(ctx.get(KEY_RESOURCE_VERSION))
                .toJson();
        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx));
    }

    private void doDeleteDevice(final RoutingContext ctx) {

        final String deviceId = getDeviceIdParam(ctx);
        final String tenantId = getTenantParam(ctx);

        logger.debug("removing device [tenant: {}, device: {}]", tenantId, deviceId);

        final JsonObject requestMsg = EventBusMessage.forOperation(RegistryManagementConstants.ACTION_DELETE)
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .setResourceVersion(ctx.get(KEY_RESOURCE_VERSION))
                .toJson();

        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx));

    }

}
