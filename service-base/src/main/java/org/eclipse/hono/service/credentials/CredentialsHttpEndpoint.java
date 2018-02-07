/**
 * Copyright (c) 2016, 2017 Red Hat and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
 */

package org.eclipse.hono.service.credentials;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.MessageHelper;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * An {@code HttpEndpoint} for managing device credentials.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/Credentials-API//">Credentials API</a>.
 * It receives HTTP requests representing operation invocations and sends them to the address {@link CredentialsConstants#CREDENTIALS_ENDPOINT} on the vertx
 * event bus for processing. The outcome is then returned to the client in the HTTP response.
 */
public final class CredentialsHttpEndpoint extends AbstractHttpEndpoint<ServiceConfigProperties> {

    private static final String KEY_REQUEST_BODY = "KEY_REQUEST_BODY";
    // path parameters for capturing parts of the URI path
    private static final String PARAM_TENANT_ID = "tenant_id";
    private static final String PARAM_DEVICE_ID = "device_id";
    private static final String PARAM_TYPE = "type";
    private static final String PARAM_AUTH_ID = "auth_id";

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public CredentialsHttpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public void addRoutes(final Router router) {

        final String pathWithTenant = String.format("/%s/:%s", CredentialsConstants.CREDENTIALS_ENDPOINT, PARAM_TENANT_ID);
        final String pathWithTenantAndDeviceId = String.format("/%s/:%s/:%s",
                CredentialsConstants.CREDENTIALS_ENDPOINT, PARAM_TENANT_ID, PARAM_DEVICE_ID);
        final String pathWithTenantAndAuthIdAndType = String.format("/%s/:%s/:%s/:%s",
                CredentialsConstants.CREDENTIALS_ENDPOINT, PARAM_TENANT_ID, PARAM_AUTH_ID, PARAM_TYPE);

        final BodyHandler bodyHandler = BodyHandler.create();
        bodyHandler.setBodyLimit(2048); // limit body size to 2kb

        // add credentials
        router.post(pathWithTenant).handler(bodyHandler);
        router.post(pathWithTenant).handler(this::extractRequiredJsonPayload);
        router.post(pathWithTenant).handler(this::addCredentials);

        // get credentials by auth-id and type
        router.get(pathWithTenantAndAuthIdAndType).handler(this::getCredentials);
        // get all credentials for a given device
        router.get(pathWithTenantAndDeviceId).handler(this::getCredentialsForDevice);

        // update credentials by auth-id and type
        router.put(pathWithTenantAndAuthIdAndType).handler(bodyHandler);
        router.put(pathWithTenantAndAuthIdAndType).handler(this::extractRequiredJsonPayload);
        router.put(pathWithTenantAndAuthIdAndType).handler(this::updateCredentials);

        // remove credentials by auth-id and type
        router.delete(pathWithTenantAndAuthIdAndType).handler(this::removeCredentials);
        // remove all credentials for a device
        router.delete(pathWithTenantAndDeviceId).handler(this::removeCredentialsForDevice);

    }

    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT_ID);
    }

    private static String getDeviceIdParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_DEVICE_ID);
    }

    private static String getTypeParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TYPE);
    }

    private static String getAuthIdParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_AUTH_ID);
    }

    @Override
    public String getName() {
        return CredentialsConstants.CREDENTIALS_ENDPOINT;
    }

    /**
     * 
     * @param ctx The routing context to retrieve the JSON request body from.
     */
    private void extractRequiredJsonPayload(final RoutingContext ctx) {

        final MIMEHeader contentType = ctx.parsedHeaders().contentType();
        if (contentType == null) {
            ctx.response().setStatusMessage("Missing Content-Type header");
            ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
        } else if (!HttpUtils.CONTENT_TYPE_JSON.equalsIgnoreCase(contentType.value())) {
            ctx.response().setStatusMessage("Unsupported Content-Type");
            ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
        } else {
            try {
                if (ctx.getBody() != null) {
                    ctx.put(KEY_REQUEST_BODY, ctx.getBodyAsJson());
                    ctx.next();
                } else {
                    ctx.response().setStatusMessage("Empty body");
                    ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
                }
            } catch (DecodeException e) {
                ctx.response().setStatusMessage("Invalid JSON");
                ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
            }
        }
    }

    private void addCredentials(final RoutingContext ctx) {

        final JsonObject payload = (JsonObject) ctx.get(KEY_REQUEST_BODY);
        final String deviceId = payload.getString(CredentialsConstants.FIELD_DEVICE_ID);
        final String authId = payload.getString(CredentialsConstants.FIELD_AUTH_ID);
        final String type = payload.getString(CredentialsConstants.FIELD_TYPE);
        final String tenantId = getTenantParam(ctx);
        logger.debug("adding credentials [tenant: {}, device-id: {}, auth-id: {}, type: {}]", tenantId, deviceId, authId, type);

        final JsonObject requestMsg = CredentialsConstants.getServiceRequestAsJson(CredentialsConstants.OPERATION_ADD, tenantId, deviceId, payload);
        doCredentialsAction(ctx, requestMsg, (status, addCredentialsResult) -> {
            final HttpServerResponse response = ctx.response();
            response.setStatusCode(status);
            switch(status) {
                case HttpURLConnection.HTTP_CREATED:
                response
                        .putHeader(
                                HttpHeaders.LOCATION,
                                String.format("/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, tenantId, authId, type));
                default:
                    if (status >= 400) {
                        setResponseBody(addCredentialsResult, response);
                    }
                    response.end();
            }
        });
    }

    private void updateCredentials(final RoutingContext ctx) {

        final JsonObject payload = (JsonObject) ctx.get(KEY_REQUEST_BODY);
        final String deviceId = payload.getString(CredentialsConstants.FIELD_DEVICE_ID);
        final String authId = payload.getString(CredentialsConstants.FIELD_AUTH_ID);
        final String type = payload.getString(CredentialsConstants.FIELD_TYPE);
        final String tenantId = getTenantParam(ctx);
        final String authIdFromUri = getAuthIdParam(ctx);
        final String typeFromUri = getTypeParam(ctx);

        // auth-id and type from URI must match payload
        if (!authIdFromUri.equals(authId)) {
            ctx.response().setStatusMessage("Non-matching authentication identifier");
            ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
        } else if (!typeFromUri.equals(type)) {
            ctx.response().setStatusMessage("Non-matching credentials type");
            ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
        } else {
            logger.debug("updating credentials [tenant: {}, device-id: {}, auth-id: {}, type: {}]", tenantId, deviceId, authId, type);

            final JsonObject requestMsg = CredentialsConstants.getServiceRequestAsJson(CredentialsConstants.OPERATION_UPDATE, tenantId, deviceId, payload);
            doCredentialsAction(ctx, requestMsg, (status, updateCredentialsResult) -> {
                final HttpServerResponse response = ctx.response();
                response.setStatusCode(status);
                if (status >= 400) {
                    setResponseBody(updateCredentialsResult, response);
                }
                response.end();
            });
        }
    }

    private void removeCredentials(final RoutingContext ctx) {

        final String tenantId = getTenantParam(ctx);
        final String type = getTypeParam(ctx);
        final String authId = getAuthIdParam(ctx);

        logger.debug("removeCredentials [tenant: {}, type: {}, authId: {}]", tenantId, type, authId);

        final HttpServerResponse response = ctx.response();
        final JsonObject payload = new JsonObject();
        payload.put(CredentialsConstants.FIELD_TYPE, type);
        payload.put(CredentialsConstants.FIELD_AUTH_ID, authId);

        final JsonObject requestMsg = CredentialsConstants.getServiceRequestAsJson(CredentialsConstants.OPERATION_REMOVE,
                tenantId, null, payload);

        doCredentialsAction(ctx, requestMsg, (status, removeCredentialsResult) -> {
            response.setStatusCode(status);
            if (status >= 400) {
                setResponseBody(removeCredentialsResult, response);
            }
            response.end();
        });
    }

    private void removeCredentialsForDevice(final RoutingContext ctx) {

        final String tenantId = getTenantParam(ctx);
        final String deviceId = getDeviceIdParam(ctx);

        logger.debug("removeCredentialsForDevice: [tenant: {}, device-id: {}]", tenantId, deviceId);

        final HttpServerResponse response = ctx.response();
        final JsonObject payload = new JsonObject();
        payload.put(CredentialsConstants.FIELD_DEVICE_ID, deviceId);
        payload.put(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SPECIFIER_WILDCARD);

        final JsonObject requestMsg = CredentialsConstants.getServiceRequestAsJson(CredentialsConstants.OPERATION_REMOVE,
                tenantId, deviceId, payload);

        doCredentialsAction(ctx, requestMsg, (status, removeCredentialsResult) -> {
            response.setStatusCode(status);
            if (status >= 400) {
                setResponseBody(removeCredentialsResult, response);
            }
            response.end();
        });
    }

    /**
     * Gets credentials by auth-id and type.
     * 
     * @param ctx The context to retrieve the query parameters from.
     */
    private void getCredentials(final RoutingContext ctx) {

        // mandatory params
        final String tenantId = getTenantParam(ctx);
        final String authId = getAuthIdParam(ctx);
        final String type = getTypeParam(ctx);

        logger.debug("getCredentials [tenant: {}, auth-id: {}, type: {}]", tenantId, authId, type);

        final JsonObject requestMsg = CredentialsConstants.getServiceGetRequestAsJson(
                tenantId, null, authId, type);

        doCredentialsAction(ctx, requestMsg, (status, getCredentialsResult) -> {
            final HttpServerResponse response = ctx.response();
            response.setStatusCode(status);
            if (status == HttpURLConnection.HTTP_OK || status >= 400) {
                setResponseBody(getCredentialsResult, response);
            }
            response.end();
        });

    }

    private void getCredentialsForDevice(final RoutingContext ctx) {

        // mandatory params
        final String tenantId = getTenantParam(ctx);
        final String deviceId = getDeviceIdParam(ctx);

        logger.debug("getCredentialsForDevice [tenant: {}, device-id: {}]]", tenantId, deviceId);

        final JsonObject requestMsg = CredentialsConstants.getServiceGetRequestAsJson(
                tenantId, deviceId, null, CredentialsConstants.SPECIFIER_WILDCARD);

        doCredentialsAction(ctx, requestMsg, (status, getCredentialsResult) -> {
            final HttpServerResponse response = ctx.response();
            response.setStatusCode(status);
            if (status == HttpURLConnection.HTTP_OK || status >= 400) {
                setResponseBody(getCredentialsResult, response);
            }
            response.end();
        });

    }

    private void doCredentialsAction(final RoutingContext ctx, final JsonObject requestMsg, final BiConsumer<Integer, JsonObject> responseHandler) {

        vertx.eventBus().send(CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_IN, requestMsg, invocation -> {

            if (invocation.failed()) {
                HttpUtils.serviceUnavailable(ctx, 2);
            } else {
                final JsonObject credentialsResult = (JsonObject) invocation.result().body();
                final Integer status = credentialsResult.getInteger(MessageHelper.APP_PROPERTY_STATUS);
                responseHandler.accept(status, credentialsResult);
            }
        });
    }

    private static void setResponseBody(final JsonObject registrationResult, final HttpServerResponse response) {
        final JsonObject msg = registrationResult.getJsonObject(CredentialsConstants.FIELD_PAYLOAD);
        if (msg != null) {
            final String body = msg.encodePrettily();
            response.putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON_UFT8)
                    .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()))
                    .write(body);
        }
    }
}
