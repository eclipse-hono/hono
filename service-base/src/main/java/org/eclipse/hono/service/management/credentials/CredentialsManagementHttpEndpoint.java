/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.credentials;

import java.net.HttpURLConnection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractEventBusHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * An {@code HttpEndpoint} for managing device credentials.
 * <p>
 * This endpoint implements the <em>credentials</em> resources of Hono's
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>.
 * It receives HTTP requests representing operation invocations and sends them to an address on
 * the vertx event bus for processing. The outcome is then returned to the peer in the HTTP response.
 * @deprecated This class will be removed in future versions. Please use {@link org.eclipse.hono.service.management.credentials.AbstractCredentialsManagementHttpEndpoint} based implementation in the future.
 */
@Deprecated(forRemoval = true)
public final class CredentialsManagementHttpEndpoint extends AbstractEventBusHttpEndpoint<ServiceConfigProperties> {

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public CredentialsManagementHttpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    protected String getEventBusAddress() {
        return RegistryManagementConstants.EVENT_BUS_ADDRESS_CREDENTIALS_MANAGEMENT_IN;
    }

    @Override
    public String getName() {
        return String.format("%s/%s",
                RegistryManagementConstants.API_VERSION,
                RegistryManagementConstants.CREDENTIALS_HTTP_ENDPOINT);
    }

    @Override
    public void addRoutes(final Router router) {

        final String pathWithTenantAndDeviceId = String.format("/%s/:%s/:%s",
                getName(), PARAM_TENANT_ID, PARAM_DEVICE_ID);


        // Add CORS handler
        router.route(pathWithTenantAndDeviceId).handler(createCorsHandler(config.getCorsAllowedOrigin(), EnumSet.of(HttpMethod.GET, HttpMethod.PUT)));

        final BodyHandler bodyHandler = BodyHandler.create();
        bodyHandler.setBodyLimit(config.getMaxPayloadSize());

        // get all credentials for a given device
        router.get(pathWithTenantAndDeviceId).handler(this::getCredentialsForDevice);

        // set credentials for a given device
        router.put(pathWithTenantAndDeviceId).handler(bodyHandler);
        router.put(pathWithTenantAndDeviceId).handler(this::extractRequiredJsonArrayPayload);
        router.put(pathWithTenantAndDeviceId).handler(this::extractIfMatchVersionParam);
        router.put(pathWithTenantAndDeviceId).handler(this::updateCredentials);
    }

    private void updateCredentials(final RoutingContext ctx) {

        final JsonArray credentials = (JsonArray) ctx.get(KEY_REQUEST_BODY);

        final String deviceId = getDeviceIdParam(ctx);
        final String resourceVersion = ctx.get(KEY_RESOURCE_VERSION);
        final String tenantId = getTenantParam(ctx);

        logger.debug("updating credentials [tenant: {}, device-id: {}] - {}", tenantId, deviceId, credentials);

        final JsonObject payload = new JsonObject();
        payload.put(RegistryManagementConstants.CREDENTIALS_OBJECT, credentials);

        final JsonObject requestMsg = EventBusMessage.forOperation(RegistryManagementConstants.ACTION_UPDATE)
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .setResourceVersion(resourceVersion)
                .setJsonPayload(payload)
                .toJson();

        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx));
    }

    private void getCredentialsForDevice(final RoutingContext ctx) {

        // mandatory params
        final String tenantId = getTenantParam(ctx);
        final String deviceId = getDeviceIdParam(ctx);

        logger.debug("getCredentialsForDevice [tenant: {}, device-id: {}]]", tenantId, deviceId);

        final JsonObject requestMsg = EventBusMessage.forOperation(RegistryManagementConstants.ACTION_GET)
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .toJson();

        sendAction(ctx, requestMsg, getCredentialsResponseHandler(ctx,
                status -> status == HttpURLConnection.HTTP_OK));
    }


    /**
     * Gets a response handler that implements the default behavior for responding to an HTTP request.
     * <p>
     * The default behavior is as follows:
     * <ol>
     * <li>Set the status code on the response.</li>
     * <li>If the status code represents an error condition (i.e. the code is &gt;= 400),
     * then the JSON object passed in to the returned handler is written to the response body.</li>
     * <li>Otherwise, if the given filter evaluates to {@code true} for the status code,
     * the JSON object is written to the response body and the given custom handler is
     * invoked (if not {@code null}).</li>
     * </ol>
     *
     * @param ctx The routing context of the request.
     * @param successfulOutcomeFilter A predicate that evaluates to {@code true} for the status code(s) representing a
     *                           successful outcome.
     * @return The created handler for processing responses.
     * @throws NullPointerException If routing context or filter is {@code null}.
     */
    protected BiConsumer<Integer, EventBusMessage> getCredentialsResponseHandler(
            final RoutingContext ctx,
            final IntPredicate successfulOutcomeFilter) {

        Objects.requireNonNull(successfulOutcomeFilter);
        final HttpServerResponse response = ctx.response();

        return (status, responseMessage) -> {
            response.setStatusCode(status);
            if (status >= 400) {
                HttpUtils.setResponseBody(response, responseMessage.getJsonPayload());
            } else if (successfulOutcomeFilter.test(status)) {
                final JsonArray credentials = responseMessage.getJsonPayload()
                        .getJsonArray(RegistryManagementConstants.CREDENTIALS_OBJECT);
                HttpUtils.setResponseBody(response, credentials);
            }
            response.end();
        };
    }
}
