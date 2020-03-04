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

package org.eclipse.hono.service.management.credentials;

import java.net.HttpURLConnection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
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
 * It receives HTTP requests representing operation invocations and forward them to the
 * Credential Management Service Implementation for processing.
 * The outcome is then returned to the client in the HTTP response.
 */
public abstract class AbstractCredentialsManagementHttpEndpoint extends AbstractHttpEndpoint<ServiceConfigProperties> {


    private static final String SPAN_NAME_GET_CREDENTIALS = "get Credentials from management API";
    private static final String SPAN_NAME_UPDATE_CREDENTIALS = "update Credentials from management API";

    private static final String CREDENTIALS_MANAGEMENT_ENDPOINT_NAME = String.format("%s/%s",
                    RegistryManagementConstants.API_VERSION,
                    RegistryManagementConstants.CREDENTIALS_HTTP_ENDPOINT);

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public AbstractCredentialsManagementHttpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public String getName() {
        return CREDENTIALS_MANAGEMENT_ENDPOINT_NAME;
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


    /**
     * The service to forward requests to.
     *
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract CredentialsManagementService getService();

    private void updateCredentials(final RoutingContext ctx) {

        final Span span = newChildSpan(SPAN_NAME_UPDATE_CREDENTIALS, TracingHandler.serverSpanContext(ctx), tracer,
                getClass().getSimpleName());

        final JsonArray credentials = ctx.get(KEY_REQUEST_BODY);

        final String tenantId = getMandatoryRequestParam(PARAM_TENANT_ID, ctx, span);
        final String deviceId = getMandatoryRequestParam(PARAM_DEVICE_ID, ctx, span);
        final Optional<String> resourceVersion = Optional.ofNullable(ctx.get(KEY_RESOURCE_VERSION));

        final List<CommonCredential> commonCredentials;
        try {
            commonCredentials = decodeCredentials(credentials);
        } catch (final IllegalArgumentException e) {
            final String msg = "Error parsing credentials";
            logger.debug(msg);
            TracingHelper.logError(span, msg);
            Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_BAD_REQUEST);
            HttpUtils.badRequest(ctx, msg);
            span.finish();
            return;
        }

        logger.debug("updating credentials [tenant: {}, device-id: {}] - {}", tenantId, deviceId, credentials);

        getService().updateCredentials(tenantId, deviceId, commonCredentials, resourceVersion, span)
                .setHandler(handler -> {
                    final OperationResult<Void> operationResult = handler.result();
                    writeOperationResponse(
                            ctx,
                            operationResult,
                            null,
                            span);
                });
    }

    private void getCredentialsForDevice(final RoutingContext ctx) {

        final Span span = newChildSpan(SPAN_NAME_GET_CREDENTIALS, TracingHandler.serverSpanContext(ctx), tracer,
                getClass().getSimpleName());

        // mandatory params
        final String tenantId = getMandatoryRequestParam(PARAM_TENANT_ID, ctx, span);
        final String deviceId = getMandatoryRequestParam(PARAM_DEVICE_ID, ctx, span);

        final HttpServerResponse response = ctx.response();

        logger.debug("getCredentialsForDevice [tenant: {}, device-id: {}]]", tenantId, deviceId);

        getService().readCredentials(tenantId, deviceId, span)
                .setHandler(handler -> {
                    final OperationResult<List<CommonCredential>> operationResult = handler.result();
                    final int status = operationResult.getStatus();
                    response.setStatusCode(status);
                    switch (status) {
                    case HttpURLConnection.HTTP_OK:
                        final JsonArray credentialsArray = new JsonArray();
                        for (final CommonCredential credential : operationResult.getPayload()) {
                            credentialsArray.add(JsonObject.mapFrom(credential));
                        }
                        operationResult.getResourceVersion().ifPresent(v -> response.putHeader(HttpHeaders.ETAG, v));
                        HttpUtils.setResponseBody(response, credentialsArray);

                        // falls through intentionally
                    default:
                        Tags.HTTP_STATUS.set(span, status);
                        span.finish();
                        response.end();
                    }
                });
    }

    /**
     * Decode a list of secrets from a JSON array.
     * <p>
     * This is a convenience method, decoding a list of secrets from a JSON array.
     *
     * @param objects The JSON array.
     * @return The list of decoded secrets.
     * @throws NullPointerException in the case the {@code objects} parameter is {@code null}.
     * @throws IllegalArgumentException If a credentials object is invalid.
     */
    protected List<CommonCredential> decodeCredentials(final JsonArray objects) {
        return objects
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(this::decodeCredential)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Decode a credential from a JSON object.
     *
     * @param object The object to device from.
     * @return The decoded secret. Or {@code null} if the provided JSON object was {@code null}.
     * @throws IllegalArgumentException If the credential object is invalid.
     */
    protected CommonCredential decodeCredential(final JsonObject object) {

        if (object == null) {
            return null;
        }

        final String type = object.getString("type");
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("'type' field must be set");
        }

        return decodeCredential(type, object);
    }

    /**
     * Decode a credential, based on the provided type.
     *
     * @param type The type of the secret. Will never be {@code null}.
     * @param object The JSON object to decode. Will never be {@code null}.
     * @return The decoded secret.
     * @throws IllegalArgumentException If the credential object is invalid.
     */
    protected CommonCredential decodeCredential(final String type, final JsonObject object) {
        switch (type) {
            case RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD:
                return object.mapTo(PasswordCredential.class);
            case RegistryManagementConstants.SECRETS_TYPE_PRESHARED_KEY:
                return object.mapTo(PskCredential.class);
            case RegistryManagementConstants.SECRETS_TYPE_X509_CERT:
                return object.mapTo(X509CertificateCredential.class);
            default:
                return object.mapTo(GenericCredential.class);
        }
    }
}
