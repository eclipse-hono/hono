/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.registration;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.RegistrationConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * An {@code HttpEndpoint} for managing device registration information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a>.
 * It receives HTTP requests representing operation invocations and sends them to an address on the vertx
 * event bus for processing. The outcome is then returned to the peer in the HTTP response.
 */
public final class RegistrationHttpEndpoint extends AbstractHttpEndpoint<ServiceConfigProperties> {

    /**
     * Creates an endpoint for a Vertx instance.
     * 
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public RegistrationHttpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    protected String getEventBusAddress() {
        return RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN;
    }

    @Override
    public String getName() {
        return RegistrationConstants.REGISTRATION_ENDPOINT;
    }

    @Override
    public void addRoutes(final Router router) {

        final String pathWithTenant = String.format("/%s/:%s", RegistrationConstants.REGISTRATION_ENDPOINT, PARAM_TENANT_ID);
        // ADD device registration
        router.route(HttpMethod.POST, pathWithTenant).consumes(HttpUtils.CONTENT_TYPE_JSON)
                .handler(this::doRegisterDeviceJson);
        router.route(HttpMethod.POST, pathWithTenant)
                .handler(ctx -> HttpUtils.badRequest(ctx, "missing or unsupported content-type"));

        final String pathWithTenantAndDeviceId = String.format("/%s/:%s/:%s",
                RegistrationConstants.REGISTRATION_ENDPOINT, PARAM_TENANT_ID, PARAM_DEVICE_ID);
        // GET device registration
        router.route(HttpMethod.GET, pathWithTenantAndDeviceId).handler(this::doGetDevice);

        // UPDATE existing registration
        router.route(HttpMethod.PUT, pathWithTenantAndDeviceId).consumes(HttpUtils.CONTENT_TYPE_JSON)
                .handler(this::doUpdateRegistrationJson);
        router.route(HttpMethod.PUT, pathWithTenantAndDeviceId)
                .handler(ctx -> HttpUtils.badRequest(ctx, "missing or unsupported content-type"));

        // REMOVE registration
        router.route(HttpMethod.DELETE, pathWithTenantAndDeviceId).handler(this::doUnregisterDevice);
    }

    private void doGetDevice(final RoutingContext ctx) {

        final String deviceId = getDeviceIdParam(ctx);
        final String tenantId = getTenantParam(ctx);
        final HttpServerResponse response = ctx.response();
        final JsonObject requestMsg = EventBusMessage.forOperation(RegistrationConstants.ACTION_GET)
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .toJson();

        sendAction(ctx, requestMsg, (status, registrationResult) -> {
            response.setStatusCode(status);
            switch (status) {
                case HttpURLConnection.HTTP_OK:
                    HttpUtils.setResponseBody(ctx.response(), registrationResult);
                default:
                    response.end();
                }
        });
    }

    private void doRegisterDeviceJson(final RoutingContext ctx) {
        try {
            JsonObject payload = null;
            if (ctx.getBody().length() > 0) {
                payload = ctx.getBodyAsJson();
            }
            registerDevice(ctx, payload);
        } catch (final DecodeException e) {
            HttpUtils.badRequest(ctx, "body does not contain a valid JSON object");
        }
    }

    private void registerDevice(final RoutingContext ctx, final JsonObject payload) {

        if (payload == null) {
            HttpUtils.badRequest(ctx, "missing body");
        } else {
            final Object deviceId = payload.remove(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID);
            if (deviceId == null) {
                HttpUtils.badRequest(ctx, String.format("'%s' param is required",
                        RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID));
            } else if (!(deviceId instanceof String)) {
                HttpUtils.badRequest(ctx, String.format("'%s' must be a string",
                        RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID));
            } else {
                final String tenantId = getTenantParam(ctx);
                logger.debug("registering data for device [tenant: {}, device: {}, payload: {}]", tenantId, deviceId, payload);
                final JsonObject requestMsg = EventBusMessage.forOperation(RegistrationConstants.ACTION_REGISTER)
                        .setTenant(tenantId)
                        .setDeviceId((String) deviceId)
                        .setJsonPayload(payload)
                        .toJson();
                sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx,
                        status -> status == HttpURLConnection.HTTP_CREATED,
                        response -> response.putHeader(
                                HttpHeaders.LOCATION,
                                String.format("/%s/%s/%s", RegistrationConstants.REGISTRATION_ENDPOINT, tenantId, deviceId))));
            }
        }
    }

    private void doUpdateRegistrationJson(final RoutingContext ctx) {

        try {
            JsonObject payload = null;
            if (ctx.getBody().length() > 0) {
                payload = ctx.getBodyAsJson();
            }
            updateRegistration(getDeviceIdParam(ctx), payload, ctx);
        } catch (final DecodeException e) {
            HttpUtils.badRequest(ctx, "body does not contain a valid JSON object");
        }
    }

    private void updateRegistration(final String deviceId, final JsonObject payload, final RoutingContext ctx) {

        if (payload != null) {
            payload.remove(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID);
        }
        final String tenantId = getTenantParam(ctx);
        logger.debug("updating registration data for device [tenant: {}, device: {}, payload: {}]", tenantId, deviceId, payload);
        final JsonObject requestMsg = EventBusMessage.forOperation(RegistrationConstants.ACTION_UPDATE)
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .setJsonPayload(payload)
                .toJson();
        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx));
    }

    private void doUnregisterDevice(final RoutingContext ctx) {

        final String deviceId = getDeviceIdParam(ctx);
        final String tenantId = getTenantParam(ctx);
        logger.debug("removing registration information for device [tenant: {}, device: {}]", tenantId, deviceId);
        final JsonObject requestMsg = EventBusMessage.forOperation(RegistrationConstants.ACTION_DEREGISTER)
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .toJson();
        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx));
    }

}
