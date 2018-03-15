/**
 * Copyright (c) 2016, 2018 Red Hat and others
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

import io.vertx.core.http.HttpHeaders;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.EventBusMessage;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
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

    // path parameters for capturing parts of the URI path
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
    protected String getEventBusAddress() {
        return CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_IN;
    }

    @Override
    public void addRoutes(final Router router) {

        final String pathWithTenant = String.format("/%s/:%s", CredentialsConstants.CREDENTIALS_ENDPOINT, PARAM_TENANT_ID);
        final String pathWithTenantAndDeviceId = String.format("/%s/:%s/:%s",
                CredentialsConstants.CREDENTIALS_ENDPOINT, PARAM_TENANT_ID, PARAM_DEVICE_ID);
        final String pathWithTenantAndAuthIdAndType = String.format("/%s/:%s/:%s/:%s",
                CredentialsConstants.CREDENTIALS_ENDPOINT, PARAM_TENANT_ID, PARAM_AUTH_ID, PARAM_TYPE);

        final BodyHandler bodyHandler = BodyHandler.create();
        bodyHandler.setBodyLimit(config.getMaxPayloadSize());

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

    private void addCredentials(final RoutingContext ctx) {

        final JsonObject payload = (JsonObject) ctx.get(KEY_REQUEST_BODY);
        final String deviceId = payload.getString(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID);
        final String authId = payload.getString(CredentialsConstants.FIELD_AUTH_ID);
        final String type = payload.getString(CredentialsConstants.FIELD_TYPE);
        final String tenantId = getTenantParam(ctx);
        logger.debug("adding credentials [tenant: {}, device-id: {}, auth-id: {}, type: {}]", tenantId, deviceId, authId, type);

        final JsonObject requestMsg = EventBusMessage.forOperation(CredentialsConstants.CredentialsAction.add.toString())
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .setJsonPayload(payload)
                .toJson();

        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx,
                status -> status == HttpURLConnection.HTTP_CREATED,
                httpServerResponse -> httpServerResponse.putHeader(HttpHeaders.LOCATION,
                        String.format("/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, tenantId, authId, type))
                )
        );
    }

    private void updateCredentials(final RoutingContext ctx) {

        final JsonObject payload = (JsonObject) ctx.get(KEY_REQUEST_BODY);
        final String deviceId = payload.getString(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID);
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

            final JsonObject requestMsg = EventBusMessage.forOperation(CredentialsConstants.CredentialsAction.update.toString())
                    .setTenant(tenantId)
                    .setDeviceId(deviceId)
                    .setJsonPayload(payload)
                    .toJson();

            sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx));
        }
    }

    private void removeCredentials(final RoutingContext ctx) {

        final String tenantId = getTenantParam(ctx);
        final String type = getTypeParam(ctx);
        final String authId = getAuthIdParam(ctx);

        logger.debug("removeCredentials [tenant: {}, type: {}, authId: {}]", tenantId, type, authId);

        final JsonObject payload = new JsonObject();
        payload.put(CredentialsConstants.FIELD_TYPE, type);
        payload.put(CredentialsConstants.FIELD_AUTH_ID, authId);

        final JsonObject requestMsg = EventBusMessage.forOperation(CredentialsConstants.CredentialsAction.remove.toString())
                .setTenant(tenantId)
                .setJsonPayload(payload)
                .toJson();

        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx,
                status -> status == HttpURLConnection.HTTP_NO_CONTENT,
                null));
    }

    private void removeCredentialsForDevice(final RoutingContext ctx) {

        final String tenantId = getTenantParam(ctx);
        final String deviceId = getDeviceIdParam(ctx);

        logger.debug("removeCredentialsForDevice: [tenant: {}, device-id: {}]", tenantId, deviceId);

        final JsonObject payload = new JsonObject();
        payload.put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        payload.put(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SPECIFIER_WILDCARD);

        final JsonObject requestMsg = EventBusMessage.forOperation(CredentialsConstants.CredentialsAction.remove.toString())
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .setJsonPayload(payload)
                .toJson();

        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx,
                status -> status == HttpURLConnection.HTTP_NO_CONTENT,
                null));
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

        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx,
                status -> status == HttpURLConnection.HTTP_OK,
                null));

    }

    private void getCredentialsForDevice(final RoutingContext ctx) {

        // mandatory params
        final String tenantId = getTenantParam(ctx);
        final String deviceId = getDeviceIdParam(ctx);

        logger.debug("getCredentialsForDevice [tenant: {}, device-id: {}]]", tenantId, deviceId);

        final JsonObject requestMsg = CredentialsConstants.getServiceGetRequestAsJson(
                tenantId, deviceId, null, CredentialsConstants.SPECIFIER_WILDCARD);

        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx,
                status -> status == HttpURLConnection.HTTP_OK,
                null));
    }

}
