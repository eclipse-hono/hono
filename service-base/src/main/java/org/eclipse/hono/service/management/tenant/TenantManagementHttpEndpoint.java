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

package org.eclipse.hono.service.management.tenant;

import java.net.HttpURLConnection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractEventBusHttpEndpoint;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * An {@code HttpEndpoint} for managing tenant information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/docs/api/tenant/">Tenant API</a>.
 * It receives HTTP requests representing operation invocations and sends them to an address on the vertx
 * event bus for processing. The outcome is then returned to the peer in the HTTP response.
 * @deprecated This class will be removed in future versions. Please use {@link AbstractTenantManagementHttpEndpoint} based implementation in the future.
 */
@Deprecated(forRemoval = true)
public final class TenantManagementHttpEndpoint extends AbstractEventBusHttpEndpoint<ServiceConfigProperties> {

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public TenantManagementHttpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    protected String getEventBusAddress() {
        return RegistryManagementConstants.EVENT_BUS_ADDRESS_TENANT_MANAGEMENT_IN;
    }

    @Override
    public String getName() {
        return String.format("%s/%s",
                RegistryManagementConstants.API_VERSION,
                RegistryManagementConstants.TENANT_HTTP_ENDPOINT);
    }

    @Override
    public void addRoutes(final Router router) {

        final String path = String.format("/%s", getName());
        final String pathWithTenant = String.format("/%s/:%s", getName(), PARAM_TENANT_ID);

        // Add CORS handler
        router.route(path).handler(createCorsHandler(config.getCorsAllowedOrigin(), EnumSet.of(HttpMethod.POST)));
        router.route(pathWithTenant).handler(createDefaultCorsHandler(config.getCorsAllowedOrigin()));

        final BodyHandler bodyHandler = BodyHandler.create();
        bodyHandler.setBodyLimit(config.getMaxPayloadSize());

        // ADD tenant with auto-generated ID
        router.post(path).handler(bodyHandler);
        router.post(path).handler(this::extractOptionalJsonPayload);
        router.post(path).handler(this::createTenant);

        // ADD tenant
        router.post(pathWithTenant).handler(bodyHandler);
        router.post(pathWithTenant).handler(this::extractOptionalJsonPayload);
        router.post(pathWithTenant).handler(this::updatePayloadWithTenantId);
        router.post(pathWithTenant).handler(this::createTenant);

        // GET tenant
        router.get(pathWithTenant).handler(this::getTenant);

        // UPDATE tenant
        router.put(pathWithTenant).handler(bodyHandler);
        router.put(pathWithTenant).handler(this::extractRequiredJsonPayload);
        router.put(pathWithTenant).handler(this::extractIfMatchVersionParam);
        router.put(pathWithTenant).handler(this::updateTenant);

        // REMOVE tenant
        router.delete(pathWithTenant).handler(this::extractIfMatchVersionParam);
        router.delete(pathWithTenant).handler(this::deleteTenant);
    }

    /**
     * Check that the tenantId value is not blank then
     * update the payload (that was put to the RoutingContext ctx with the
     * key {@link #KEY_REQUEST_BODY}) with the tenant value retrieved from the RoutingContext.
     * The tenantId value is associated with the key {@link RegistryManagementConstants#FIELD_PAYLOAD_TENANT_ID}.
     *
     * @param ctx The routing context to retrieve the JSON request body from.
     */
    protected void updatePayloadWithTenantId(final RoutingContext ctx) {

        final JsonObject payload = ctx.get(KEY_REQUEST_BODY);
        final String tenantId = getTenantIdFromContext(ctx);

        if (tenantId.isBlank()) {
            ctx.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    String.format("'%s' param cannot be empty", RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID)));
        }

        payload.put(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, tenantId);
        ctx.put(KEY_REQUEST_BODY, payload);
        ctx.next();
    }

    private String getTenantIdFromContext(final RoutingContext ctx) {
        final JsonObject payload = ctx.get(KEY_REQUEST_BODY);
        return Optional.ofNullable(getTenantParam(ctx)).orElse(getTenantParamFromPayload(payload));
    }

    private void createTenant(final RoutingContext ctx) {

        final String tenantId = getTenantIdFromContext(ctx);
        final String location = String.format("/%s/", getName());

        doTenantHttpRequest(ctx, tenantId, RegistryManagementConstants.ACTION_CREATE,
                status -> status == HttpURLConnection.HTTP_CREATED,
                (response, responseMessage) -> response.putHeader(HttpHeaders.LOCATION,
                        location + responseMessage.getTenant())
        );
    }

    private void getTenant(final RoutingContext ctx) {

        final String tenantId = getTenantIdFromContext(ctx);

        doTenantHttpRequest(ctx, tenantId, RegistryManagementConstants.ACTION_GET,
                status -> status == HttpURLConnection.HTTP_OK, null);
    }

    private void updateTenant(final RoutingContext ctx) {

        final String tenantId = getTenantIdFromContext(ctx);

        doTenantHttpRequest(ctx, tenantId, RegistryManagementConstants.ACTION_UPDATE,
                status -> status == HttpURLConnection.HTTP_NO_CONTENT,
                (response, payload) -> response.putHeader(HttpHeaders.ETAG,
                        payload.getResourceVersion()));
    }

    private void deleteTenant(final RoutingContext ctx) {

        final String tenantId = getTenantIdFromContext(ctx);

        doTenantHttpRequest(ctx, tenantId, RegistryManagementConstants.ACTION_DELETE,
                status -> status == HttpURLConnection.HTTP_NO_CONTENT, null);
    }

    private void doTenantHttpRequest(
            final RoutingContext ctx,
            final String tenantId,
            final String action,
            final IntPredicate successfulOutcomeFilter,
            final BiConsumer<HttpServerResponse, EventBusMessage> httpServerResponseHandler) {

        logger.debug("http request [{}] for tenant [tenant: {}]", action, tenantId);

        final JsonObject payload = ctx.get(KEY_REQUEST_BODY);
        final JsonObject requestMsg = EventBusMessage.forOperation(action)
                .setTenant(tenantId)
                .setJsonPayload(payload)
                .setResourceVersion(ctx.get(KEY_RESOURCE_VERSION))
                .toJson();

        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx, successfulOutcomeFilter, httpServerResponseHandler));
    }

    private static String getTenantParamFromPayload(final JsonObject payload) {
        return (payload != null ? (String) payload.remove(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID) : null);
    }
}
