/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.http.impl;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.HttpContext;
import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.jwt.DefaultJwsValidator;
import org.eclipse.hono.adapter.auth.device.jwt.JwtAuthProvider;
import org.eclipse.hono.adapter.auth.device.jwt.JwtCredentials;
import org.eclipse.hono.adapter.auth.device.usernamepassword.UsernamePasswordAuthProvider;
import org.eclipse.hono.adapter.auth.device.usernamepassword.UsernamePasswordCredentials;
import org.eclipse.hono.adapter.auth.device.x509.SubjectDnCredentials;
import org.eclipse.hono.adapter.auth.device.x509.TenantServiceBasedX509Authentication;
import org.eclipse.hono.adapter.auth.device.x509.X509AuthProvider;
import org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter;
import org.eclipse.hono.adapter.http.HonoBasicAuthHandler;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.adapter.http.JwtAuthHandler;
import org.eclipse.hono.adapter.http.X509AuthHandler;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.Strings;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.ChainAuthHandler;
import io.vertx.ext.web.handler.CorsHandler;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry &amp; Event API using HTTP
 * as described in the <a href="https://www.eclipse.org/hono/docs/user-guide/http-adapter">HTTP adapter user guide</a>.
 */
public final class VertxBasedHttpProtocolAdapter extends AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> {

    private static final String PARAM_TENANT = "tenant_id";
    private static final String PARAM_DEVICE_ID = "device_id";
    private static final String PARAM_COMMAND_REQUEST_ID = "cmd_req_id";

    private static final String ROUTE_TELEMETRY_ENDPOINT = "/telemetry";
    private static final String ROUTE_EVENT_ENDPOINT = "/event";

    private DeviceCredentialsAuthProvider<UsernamePasswordCredentials> usernamePasswordAuthProvider;
    private DeviceCredentialsAuthProvider<JwtCredentials> jwtAuthProvider;
    private DeviceCredentialsAuthProvider<SubjectDnCredentials> clientCertAuthProvider;

    /**
     * Sets the provider to use for authenticating devices based on a username and password.
     * <p>
     * If not set explicitly using this method, a {@code UsernamePasswordAuthProvider} will be created during startup.
     *
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public void setUsernamePasswordAuthProvider(
            final DeviceCredentialsAuthProvider<UsernamePasswordCredentials> provider) {
        this.usernamePasswordAuthProvider = Objects.requireNonNull(provider);
    }

    /**
     * Sets the provider to use for authenticating devices based on a Json Web Token (JWT).
     * <p>
     * If not set explicitly using this method, a {@code JwtAuthProvider} will be created during startup.
     *
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public void setJwtAuthProvider(final DeviceCredentialsAuthProvider<JwtCredentials> provider) {
        this.jwtAuthProvider = Objects.requireNonNull(provider);
    }

    /**
     * Sets the provider to use for authenticating devices based on a client certificate.
     * <p>
     * If not set explicitly using this method, a {@code SubjectDnAuthProvider} will be created during startup.
     *
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public void setClientCertAuthProvider(final DeviceCredentialsAuthProvider<SubjectDnCredentials> provider) {
        this.clientCertAuthProvider = Objects.requireNonNull(provider);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Constants#PROTOCOL_ADAPTER_TYPE_HTTP}
     */
    @Override
    public String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_HTTP;
    }

    @Override
    protected void addRoutes(final Router router) {

        if (getConfig().isAuthenticationRequired()) {

            final ChainAuthHandler authHandler = ChainAuthHandler.any();
            authHandler.add(new X509AuthHandler(
                    new TenantServiceBasedX509Authentication(getTenantClient(), tracer),
                    Optional.ofNullable(clientCertAuthProvider)
                        .orElseGet(() -> new X509AuthProvider(getCredentialsClient(), tracer)),
                    this::handleBeforeCredentialsValidation));
            authHandler.add(new JwtAuthHandler(
                    Optional.ofNullable(jwtAuthProvider)
                        .orElseGet(() -> new JwtAuthProvider(getCredentialsClient(), new DefaultJwsValidator(), tracer)),
                    getConfig().getRealm(),
                    this::handleBeforeCredentialsValidation));
            authHandler.add(new HonoBasicAuthHandler(
                    Optional.ofNullable(usernamePasswordAuthProvider)
                        .orElseGet(() -> new UsernamePasswordAuthProvider(getCredentialsClient(), tracer)),
                    getConfig().getRealm(),
                    this::handleBeforeCredentialsValidation));

            addTelemetryApiRoutes(router, authHandler);
            addEventApiRoutes(router, authHandler);
            addCommandResponseRoutes(router, authHandler);
        } else {

            log.warn("device authentication has been disabled");
            log.warn("any device may publish data on behalf of all other devices");
            addTelemetryApiRoutes(router, null);
            addEventApiRoutes(router, null);
            addCommandResponseRoutes(router, null);
        }
    }

    private void addTelemetryApiRoutes(final Router router, final Handler<RoutingContext> authHandler) {

        final String telemetryPutPathWithParams = String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID);

        // support CORS headers for PUTing telemetry
        router.routeWithRegex("\\/telemetry\\/.+")
                .handler(CorsHandler.create().addOrigin(getConfig().getCorsAllowedOrigin())
                        .allowedMethod(HttpMethod.PUT)
                        .allowedHeader(Constants.HEADER_QOS_LEVEL)
                        .allowedHeader(Constants.HEADER_TIME_TILL_DISCONNECT)
                        .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                        .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
                        .exposedHeader(Constants.HEADER_COMMAND)
                        .exposedHeader(Constants.HEADER_COMMAND_REQUEST_ID))
                .setName("/telemetry (CORS)");

        if (getConfig().isAuthenticationRequired()) {

            // support CORS headers for POSTing telemetry
            router.route(ROUTE_TELEMETRY_ENDPOINT)
                    .handler(CorsHandler.create().addOrigin(getConfig().getCorsAllowedOrigin())
                            .allowedMethod(HttpMethod.POST)
                            .allowedHeader(Constants.HEADER_QOS_LEVEL)
                            .allowedHeader(Constants.HEADER_TIME_TILL_DISCONNECT)
                            .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                            .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
                            .exposedHeader(Constants.HEADER_COMMAND)
                            .exposedHeader(Constants.HEADER_COMMAND_REQUEST_ID))
                    .setName("/telemetry (CORS)");

            // require auth for POSTing telemetry
            router.post(ROUTE_TELEMETRY_ENDPOINT)
                    .handler(authHandler)
                    .handler(getBodyHandler());

            // route for posting telemetry data using tenant and device ID determined as part of
            // device authentication
            router.post(ROUTE_TELEMETRY_ENDPOINT).handler(this::handlePostTelemetry);

            // require auth for PUTing telemetry
            router.put(ROUTE_TELEMETRY_ENDPOINT + "/*")
                    .handler(authHandler)
                    .setName(telemetryPutPathWithParams);
            // assert that authenticated device's tenant matches tenant from path variables
            router.put(telemetryPutPathWithParams).handler(this::assertTenant);
        }

        // route for uploading telemetry data
        router.putWithRegex("\\/telemetry\\/.+")
                .handler(getBodyHandler())
                .handler(this::handlePutTelemetry)
                .setName(telemetryPutPathWithParams);
    }

    private void addEventApiRoutes(final Router router, final Handler<RoutingContext> authHandler) {

        final String eventPutPathWithParams = String.format("/event/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID);

        // support CORS headers for PUTing events
        router.routeWithRegex("\\/event\\/.+")
                .handler(CorsHandler.create().addOrigin(getConfig().getCorsAllowedOrigin())
                        .allowedMethod(HttpMethod.PUT)
                        .allowedHeader(Constants.HEADER_TIME_TILL_DISCONNECT)
                        .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                        .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
                        .exposedHeader(Constants.HEADER_COMMAND)
                        .exposedHeader(Constants.HEADER_COMMAND_REQUEST_ID))
                .setName("/event (CORS)");

        if (getConfig().isAuthenticationRequired()) {

            // support CORS headers for POSTing events
            router.route(ROUTE_EVENT_ENDPOINT)
                    .handler(CorsHandler.create().addOrigin(getConfig().getCorsAllowedOrigin())
                            .allowedMethod(HttpMethod.POST)
                            .allowedHeader(Constants.HEADER_TIME_TILL_DISCONNECT)
                            .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                            .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
                            .exposedHeader(Constants.HEADER_COMMAND)
                            .exposedHeader(Constants.HEADER_COMMAND_REQUEST_ID))
                    .setName("/event (CORS)");

            // require auth for POSTing events
            router.post(ROUTE_EVENT_ENDPOINT)
                    .handler(authHandler)
                    .handler(getBodyHandler());

            // route for posting events using tenant and device ID determined as part of
            // device authentication
            router.post(ROUTE_EVENT_ENDPOINT).handler(this::handlePostTelemetry);

            // require auth for PUTing events
            router.put("/event/*")
                    .handler(authHandler)
                    .setName(eventPutPathWithParams);
            // route for asserting that authenticated device's tenant matches tenant from path variables
            router.put(eventPutPathWithParams).handler(this::assertTenant);
        }

        // route for sending event messages
        router.putWithRegex("\\/event\\/.+")
                .handler(getBodyHandler())
                .handler(this::handlePutTelemetry)
                .setName(eventPutPathWithParams);
    }

    private void addCommandResponseRoutes(final Router router, final Handler<RoutingContext> authHandler) {

        final String commandResponseMatchAllPath = "/command/res/*";
        final String commandResponsePutPathWithParams = String.format("/command/res/:%s/:%s/:%s", PARAM_TENANT,
                PARAM_DEVICE_ID, PARAM_COMMAND_REQUEST_ID);

        // support CORS headers for PUTing command response messages
        final var corsHandler = CorsHandler.create().addOrigin(getConfig().getCorsAllowedOrigin())
                .allowedMethod(HttpMethod.PUT)
                .allowedHeader(Constants.HEADER_COMMAND_RESPONSE_STATUS)
                .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                .allowedHeader(HttpHeaders.CONTENT_TYPE.toString());
        router.route(commandResponseMatchAllPath)
                .handler(corsHandler)
                .setName(commandResponseMatchAllPath + " (CORS)");

        if (getConfig().isAuthenticationRequired()) {

            // support CORS headers for POSTing command response messages
            corsHandler.allowedMethod(HttpMethod.POST);

            final String commandResponsePostPathWithParam = String.format("/command/res/:%s", PARAM_COMMAND_REQUEST_ID);
            // require auth for POSTing command response messages
            router.post(commandResponseMatchAllPath)
                    .handler(authHandler)
                    .handler(getBodyHandler())
                    .setName(commandResponsePostPathWithParam);

            // route for POSTing command response messages using tenant and device ID determined as part of
            // device authentication
            router.post(commandResponsePostPathWithParam)
                .handler(this::handlePostCommandResponse);

            // require auth for PUTing command response message
            router.put(commandResponseMatchAllPath)
                    .handler(authHandler)
                    .setName(commandResponsePutPathWithParams);
            // assert that authenticated device's tenant matches tenant from path variables
            router.put(commandResponsePutPathWithParams)
               .handler(this::assertTenant);
        }

        // route for uploading command response message
        router.put(commandResponseMatchAllPath)
                .handler(getBodyHandler())
                .handler(this::handlePutCommandResponse)
                .setName(commandResponsePutPathWithParams);
    }

    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT);
    }

    private static String getCommandRequestIdParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_COMMAND_REQUEST_ID);
    }

    private static Integer getCommandResponseStatusParam(final RoutingContext ctx) {
        return HttpUtils.getCommandResponseStatus(ctx).orElse(null);
    }

    private void handle401(final RoutingContext ctx) {
        HttpUtils.unauthorized(ctx, "Basic realm=\"" + getConfig().getRealm() + "\"");
    }

    void handlePostTelemetry(final RoutingContext ctx) {

        if (ctx.user() instanceof Device authenticatedDevice) {
            doUploadMessage(
                    HttpContext.from(ctx),
                    authenticatedDevice.getTenantId(),
                    authenticatedDevice.getDeviceId());
        } else {
            handle401(ctx);
        }
    }

    void handlePutTelemetry(final RoutingContext ctx) {

        final HttpContext httpContext = HttpContext.from(ctx);
        final var requestedResource = httpContext.getRequestedResource();
        if (Strings.isNullOrEmpty(requestedResource.getResourceId())) {
            HttpUtils.notFound(ctx, "request URI must contain device ID");
        } else if (ctx.user() instanceof Device authenticatedDevice) {
            doUploadMessage(
                    httpContext,
                    // if the request URI contains a tenant ID then it has already been
                    // verified to match the tenant of the authenticated device
                    // by the assertTenant route handler
                    authenticatedDevice.getTenantId(),
                    requestedResource.getResourceId());
        } else if (Strings.isNullOrEmpty(requestedResource.getTenantId())) {
            HttpUtils.notFound(ctx, "request URI must contain tenant ID");
        } else {
            doUploadMessage(
                    httpContext,
                    requestedResource.getTenantId(),
                    requestedResource.getResourceId());
        }
    }

    void handlePostCommandResponse(final RoutingContext ctx) {

        if (ctx.user() instanceof Device authenticatedDevice) {
            uploadCommandResponseMessage(
                    HttpContext.from(ctx),
                    authenticatedDevice.getTenantId(),
                    authenticatedDevice.getDeviceId(),
                    getCommandRequestIdParam(ctx),
                    getCommandResponseStatusParam(ctx));
        } else {
            handle401(ctx);
        }
    }

    void handlePutCommandResponse(final RoutingContext ctx) {

        final HttpContext httpContext = HttpContext.from(ctx);
        final var requestedResource = httpContext.getRequestedResource();
        final var path = requestedResource.toPath();
        if (path.length < 5) {
            HttpUtils.notFound(ctx, "request URI must contain tenant, device and request ID");
            return;
        }

        // the URI scheme is a little different from that of telemetry and event
        // endpoint (command/res) is at indices 0 and 1
        // tenant ID is at index 2
        final var tenantId = path[2];
        // device ID is at index 3
        final var deviceId = path[3];
        // request ID is at index 4
        final var requestId = path[4];
        if (Strings.isNullOrEmpty(deviceId)) {
            HttpUtils.notFound(ctx, "request URI must contain device ID");
        } else if (ctx.user() instanceof Device authenticatedDevice) {
            uploadCommandResponseMessage(
                    httpContext,
                    // if the request URI contains a tenant ID then it has already been
                    // verified to match the tenant of the authenticated device
                    // by the assertTenant route handler
                    authenticatedDevice.getTenantId(),
                    deviceId,
                    requestId,
                    getCommandResponseStatusParam(ctx));
        } else if (Strings.isNullOrEmpty(tenantId)) {
            HttpUtils.notFound(ctx, "request URI must contain tenant ID");
        } else {
            uploadCommandResponseMessage(
                    httpContext,
                    tenantId,
                    deviceId,
                    requestId,
                    getCommandResponseStatusParam(ctx));
        }
    }

    void assertTenant(final RoutingContext ctx) {

        if (ctx.user() instanceof Device authenticatedDevice) {
            if (authenticatedDevice.getTenantId().equals(getTenantParam(ctx))) {
                ctx.next();
            } else {
                ctx.fail(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                        "not authorized to upload data for device from other tenant"));
            }
        } else {
            handle401(ctx);
        }
    }
}
