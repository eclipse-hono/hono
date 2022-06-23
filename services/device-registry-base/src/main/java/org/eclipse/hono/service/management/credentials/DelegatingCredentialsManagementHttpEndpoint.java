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

package org.eclipse.hono.service.management.credentials;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.management.AbstractDelegatingRegistryHttpEndpoint;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistryManagementConstants;

import io.opentracing.Span;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
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
 *
 * @param <S> The type of service this endpoint delegates to.
 */
public class DelegatingCredentialsManagementHttpEndpoint<S extends CredentialsManagementService> extends AbstractDelegatingRegistryHttpEndpoint<S, ServiceConfigProperties> {


    private static final String SPAN_NAME_GET_CREDENTIALS = "get Credentials from management API";
    private static final String SPAN_NAME_UPDATE_CREDENTIALS = "update Credentials from management API";

    private static final String CREDENTIALS_MANAGEMENT_ENDPOINT_NAME = String.format("%s/%s",
                    RegistryManagementConstants.API_VERSION,
                    RegistryManagementConstants.CREDENTIALS_HTTP_ENDPOINT);

    /**
     * Creates an endpoint for a service instance.
     *
     * @param vertx The vert.x instance to use.
     * @param service The service to delegate to.
     * @throws NullPointerException if any of the parameters are {@code null};
     */
    public DelegatingCredentialsManagementHttpEndpoint(final Vertx vertx, final S service) {
        super(vertx, service);
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
        router.route(pathWithTenantAndDeviceId).handler(createCorsHandler(config.getCorsAllowedOrigin(), Set.of(HttpMethod.GET, HttpMethod.PUT)));

        final BodyHandler bodyHandler = BodyHandler.create(DEFAULT_UPLOADS_DIRECTORY);
        bodyHandler.setBodyLimit(config.getMaxPayloadSize());

        // get all credentials for a given device
        router.get(pathWithTenantAndDeviceId).handler(this::getCredentialsForDevice);

        // set credentials for a given device
        router.put(pathWithTenantAndDeviceId).handler(bodyHandler);
        router.put(pathWithTenantAndDeviceId).handler(this::extractRequiredJsonArrayPayload);
        router.put(pathWithTenantAndDeviceId).handler(this::extractIfMatchVersionParam);
        router.put(pathWithTenantAndDeviceId).handler(this::updateCredentials);
    }

    private void getCredentialsForDevice(final RoutingContext ctx) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_GET_CREDENTIALS,
                getClass().getSimpleName()
        ).start();

        final Future<String> tenantId = getRequestParameter(ctx, PARAM_TENANT_ID, getPredicate(config.getTenantIdPattern(), false));
        final Future<String> deviceId = getRequestParameter(ctx, PARAM_DEVICE_ID, getPredicate(config.getDeviceIdPattern(), false));

        CompositeFuture.all(tenantId, deviceId)
            .compose(ok -> {
                TracingHelper.setDeviceTags(span, tenantId.result(), deviceId.result());
                logger.debug("getting credentials [tenant: {}, device-id: {}]]",
                        tenantId.result(), deviceId.result());
                return getService().readCredentials(tenantId.result(), deviceId.result(), span);
            })
            .map(operationResult -> {
                if (operationResult.isOk()) {
                    // we need to map the payload from List to JsonArray because
                    // Jackson does not include the credential's type property in the
                    // JSON when serializing a plain java List<?> object.
                    final JsonArray credentialsArray = operationResult.getPayload().stream()
                            .map(credentials -> JsonObject.mapFrom(credentials))
                            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
                    return OperationResult.ok(
                            operationResult.getStatus(),
                            credentialsArray,
                            operationResult.getCacheDirective(),
                            operationResult.getResourceVersion());
                } else {
                    return operationResult;
                }
            })
            .onSuccess(operationResult -> writeResponse(ctx, operationResult, span))
            .onFailure(t -> failRequest(ctx, t, span))
            .onComplete(s -> span.finish());
    }

    private void updateCredentials(final RoutingContext ctx) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_UPDATE_CREDENTIALS,
                getClass().getSimpleName()
        ).start();

        final Future<String> tenantId = getRequestParameter(ctx, PARAM_TENANT_ID, getPredicate(config.getTenantIdPattern(), false));
        final Future<String> deviceId = getRequestParameter(ctx, PARAM_DEVICE_ID, getPredicate(config.getDeviceIdPattern(), false));
        final Future<List<CommonCredential>> updatedCredentials = fromPayload(ctx);

        CompositeFuture.all(tenantId, deviceId, updatedCredentials)
            .compose(ok -> {
                TracingHelper.setDeviceTags(span, tenantId.result(), deviceId.result());
                logger.debug("updating {} credentials [tenant: {}, device-id: {}]",
                        updatedCredentials.result().size(), tenantId.result(), deviceId.result());
                final Optional<String> resourceVersion = Optional.ofNullable(ctx.get(KEY_RESOURCE_VERSION));
                return getService().updateCredentials(
                        tenantId.result(),
                        deviceId.result(),
                        updatedCredentials.result(),
                        resourceVersion,
                        span);
            })
            .onSuccess(operationResult -> writeResponse(ctx, operationResult, span))
            .onFailure(t -> failRequest(ctx, t, span))
            .onComplete(s -> span.finish());
    }

    /**
     * Decodes a list of secrets from a request body.
     *
     * @param ctx The request to retrieve the updated credentials from.
     * @return The list of decoded secrets.
     * @throws NullPointerException if context is {@code null}.
     * @throws IllegalArgumentException If a credentials object is invalid.
     */
    private static Future<List<CommonCredential>> fromPayload(final RoutingContext ctx) {

        Objects.requireNonNull(ctx);
        final Promise<List<CommonCredential>> result = Promise.promise();
        Optional.ofNullable(ctx.get(KEY_REQUEST_BODY))
            .map(JsonArray.class::cast)
            .ifPresentOrElse(
                    // deserialize & validate payload
                    array -> {
                        try {
                            result.complete(decodeCredentials(array));
                        } catch (final IllegalArgumentException | DecodeException e) {
                            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                                    "request body does not contain valid Credentials objects"));
                        }
                    },
                    () -> result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                            "request body does not contain JSON array")));
        return result.future();

    }

    private static List<CommonCredential> decodeCredentials(final JsonArray array) {
        return array.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(json -> json.mapTo(CommonCredential.class))
                .collect(Collectors.toList());
    }
}
