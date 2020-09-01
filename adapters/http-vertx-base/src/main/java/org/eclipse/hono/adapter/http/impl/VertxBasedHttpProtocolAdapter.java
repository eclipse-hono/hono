/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.SubjectDnCredentials;
import org.eclipse.hono.service.auth.device.TenantServiceBasedX509Authentication;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.service.auth.device.X509AuthProvider;
import org.eclipse.hono.service.http.HonoBasicAuthHandler;
import org.eclipse.hono.service.http.HonoChainAuthHandler;
import org.eclipse.hono.service.http.HttpContext;
import org.eclipse.hono.service.http.HttpContextTenantAndAuthIdProvider;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.http.TenantTraceSamplingHandler;
import org.eclipse.hono.service.http.X509AuthHandler;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ExecutionContextTenantAndAuthIdProvider;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.ChainAuthHandler;
import io.vertx.ext.web.handler.CorsHandler;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry &amp; Event API using HTTP
 * as described in https://www.eclipse.org/hono/docs/user-guide/http-adapter.
 */
public final class VertxBasedHttpProtocolAdapter extends AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> {

    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";
    private static final String PARAM_COMMAND_REQUEST_ID = "cmd_req_id";

    private static final String ROUTE_TELEMETRY_ENDPOINT = "/telemetry";
    private static final String ROUTE_EVENT_ENDPOINT = "/event";

    private HonoClientBasedAuthProvider<UsernamePasswordCredentials> usernamePasswordAuthProvider;
    private HonoClientBasedAuthProvider<SubjectDnCredentials> clientCertAuthProvider;
    private ExecutionContextTenantAndAuthIdProvider<HttpContext> tenantObjectWithAuthIdProvider;

    /**
     * Sets the provider to use for authenticating devices based on
     * a username and password.
     * <p>
     * If not set explicitly using this method, a {@code UsernamePasswordAuthProvider}
     * will be created during startup.
     *
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public void setUsernamePasswordAuthProvider(final HonoClientBasedAuthProvider<UsernamePasswordCredentials> provider) {
        this.usernamePasswordAuthProvider = Objects.requireNonNull(provider);
    }

    /**
     * Sets the provider to use for authenticating devices based on
     * a client certificate.
     * <p>
     * If not set explicitly using this method, a {@code SubjectDnAuthProvider}
     * will be created during startup.
     *
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public void setClientCertAuthProvider(final HonoClientBasedAuthProvider<SubjectDnCredentials> provider) {
        this.clientCertAuthProvider = Objects.requireNonNull(provider);
    }

    /**
     * Sets the provider that determines the tenant and auth-id associated with a request.
     *
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public void setTenantObjectWithAuthIdProvider(final ExecutionContextTenantAndAuthIdProvider<HttpContext> provider) {
        this.tenantObjectWithAuthIdProvider = Objects.requireNonNull(provider);
    }

    @Override
    protected TenantTraceSamplingHandler getTenantTraceSamplingHandler() {
        return new TenantTraceSamplingHandler(
                Optional.ofNullable(tenantObjectWithAuthIdProvider)
                        .orElse(new HttpContextTenantAndAuthIdProvider(getConfig(), getTenantClientFactory(), PARAM_TENANT, PARAM_DEVICE_ID)));
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Constants#PROTOCOL_ADAPTER_TYPE_HTTP}
     */
    @Override
    protected String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_HTTP;
    }

    @Override
    protected void addRoutes(final Router router) {

        if (getConfig().isAuthenticationRequired()) {

            final ChainAuthHandler authHandler = new HonoChainAuthHandler();
            authHandler.append(new X509AuthHandler(
                    new TenantServiceBasedX509Authentication(getTenantClientFactory(), tracer),
                    Optional.ofNullable(clientCertAuthProvider).orElse(
                            new X509AuthProvider(getCredentialsClientFactory(), getConfig(), tracer)), tracer));
            authHandler.append(new HonoBasicAuthHandler(
                    Optional.ofNullable(usernamePasswordAuthProvider).orElse(
                            new UsernamePasswordAuthProvider(getCredentialsClientFactory(), getConfig(), tracer)),
                    getConfig().getRealm(), tracer));
            addTelemetryApiRoutes(router, authHandler);
            addEventApiRoutes(router, authHandler);
            addCommandResponseRoutes(CommandConstants.COMMAND_ENDPOINT, router, authHandler);
        } else {

            log.warn("device authentication has been disabled");
            log.warn("any device may publish data on behalf of all other devices");
            addTelemetryApiRoutes(router, null);
            addEventApiRoutes(router, null);
            addCommandResponseRoutes(CommandConstants.COMMAND_ENDPOINT, router, null);
        }
    }

    private void addTelemetryApiRoutes(final Router router, final Handler<RoutingContext> authHandler) {

        // support CORS headers for PUTing telemetry
        router.routeWithRegex("\\/telemetry\\/[^\\/]+\\/.*").handler(CorsHandler.create(getConfig().getCorsAllowedOrigin())
                .allowedMethod(HttpMethod.PUT)
                .allowedHeader(Constants.HEADER_QOS_LEVEL)
                .allowedHeader(Constants.HEADER_TIME_TILL_DISCONNECT)
                .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
                .exposedHeader(Constants.HEADER_COMMAND)
                .exposedHeader(Constants.HEADER_COMMAND_REQUEST_ID));

        if (getConfig().isAuthenticationRequired()) {

            // support CORS headers for POSTing telemetry
            router.route(ROUTE_TELEMETRY_ENDPOINT).handler(CorsHandler.create(getConfig().getCorsAllowedOrigin())
                    .allowedMethod(HttpMethod.POST)
                    .allowedHeader(Constants.HEADER_QOS_LEVEL)
                    .allowedHeader(Constants.HEADER_TIME_TILL_DISCONNECT)
                    .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                    .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
                    .exposedHeader(Constants.HEADER_COMMAND)
                    .exposedHeader(Constants.HEADER_COMMAND_REQUEST_ID));

            // require auth for POSTing telemetry
            router.route(HttpMethod.POST, ROUTE_TELEMETRY_ENDPOINT).handler(authHandler);

            // route for posting telemetry data using tenant and device ID determined as part of
            // device authentication
            router.route(HttpMethod.POST, ROUTE_TELEMETRY_ENDPOINT).handler(this::handlePostTelemetry);

            // require auth for PUTing telemetry
            router.route(HttpMethod.PUT, "/telemetry/*").handler(authHandler);
            // assert that authenticated device's tenant matches tenant from path variables
            router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(this::assertTenant);
        }

        // route for uploading telemetry data
        router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(ctx -> uploadTelemetryMessage(HttpContext.from(ctx), getTenantParam(ctx), getDeviceIdParam(ctx)));
    }

    private void addEventApiRoutes(final Router router, final Handler<RoutingContext> authHandler) {

        // support CORS headers for PUTing events
        router.routeWithRegex("\\/event\\/[^\\/]+\\/.*").handler(CorsHandler.create(getConfig().getCorsAllowedOrigin())
                .allowedMethod(HttpMethod.PUT)
                .allowedHeader(Constants.HEADER_TIME_TILL_DISCONNECT)
                .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
                .exposedHeader(Constants.HEADER_COMMAND)
                .exposedHeader(Constants.HEADER_COMMAND_REQUEST_ID));

        if (getConfig().isAuthenticationRequired()) {

            // support CORS headers for POSTing events
            router.route(ROUTE_EVENT_ENDPOINT).handler(CorsHandler.create(getConfig().getCorsAllowedOrigin())
                    .allowedMethod(HttpMethod.POST)
                    .allowedHeader(Constants.HEADER_TIME_TILL_DISCONNECT)
                    .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                    .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
                    .exposedHeader(Constants.HEADER_COMMAND)
                    .exposedHeader(Constants.HEADER_COMMAND_REQUEST_ID));

            // require auth for POSTing events
            router.route(HttpMethod.POST, ROUTE_EVENT_ENDPOINT).handler(authHandler);

            // route for posting events using tenant and device ID determined as part of
            // device authentication
            router.route(HttpMethod.POST, ROUTE_EVENT_ENDPOINT).handler(this::handlePostEvent);

            // require auth for PUTing events
            router.route(HttpMethod.PUT, "/event/*").handler(authHandler);
            // route for asserting that authenticated device's tenant matches tenant from path variables
            router.route(HttpMethod.PUT, String.format("/event/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(this::assertTenant);
        }

        // route for sending event messages
        router.route(HttpMethod.PUT, String.format("/event/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(ctx -> uploadEventMessage(HttpContext.from(ctx), getTenantParam(ctx), getDeviceIdParam(ctx)));
    }

    private void addCommandResponseRoutes(final String commandResponsePrefix, final Router router,
            final Handler<RoutingContext> authHandler) {

        // support CORS headers for PUTing command response messages
        router.routeWithRegex(String.format("\\/%s\\/res\\/[^\\/]+\\/[^\\/]+\\/.*", commandResponsePrefix))
                .handler(CorsHandler.create(getConfig().getCorsAllowedOrigin())
                        .allowedMethod(HttpMethod.PUT)
                        .allowedHeader(Constants.HEADER_COMMAND_RESPONSE_STATUS)
                        .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                        .allowedHeader(HttpHeaders.CONTENT_TYPE.toString()));

        if (getConfig().isAuthenticationRequired()) {

            final String commandResponseMatchAllPath = String.format("/%s/res/*", commandResponsePrefix);
            // support CORS headers for POSTing command response messages
            router.route(commandResponseMatchAllPath).handler(CorsHandler.create(getConfig().getCorsAllowedOrigin())
                    .allowedMethod(HttpMethod.POST)
                    .allowedHeader(Constants.HEADER_COMMAND_RESPONSE_STATUS)
                    .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                    .allowedHeader(HttpHeaders.CONTENT_TYPE.toString()));

            // require auth for POSTing command response messages
            router.route(HttpMethod.POST, commandResponseMatchAllPath).handler(authHandler);

            // route for POSTing command response messages using tenant and device ID determined as part of
            // device authentication
            router.route(HttpMethod.POST, String.format("/%s/res/:%s", commandResponsePrefix, PARAM_COMMAND_REQUEST_ID))
                .handler(this::handlePostCommandResponse);

            // require auth for PUTing command response message
            router.route(HttpMethod.PUT, commandResponseMatchAllPath).handler(authHandler);
            // assert that authenticated device's tenant matches tenant from path variables
            router.route(
                    HttpMethod.PUT,
                    String.format("/%s/res/:%s/:%s/:%s", commandResponsePrefix, PARAM_TENANT, PARAM_DEVICE_ID, PARAM_COMMAND_REQUEST_ID))
               .handler(this::assertTenant);
        }

        // route for uploading command response message
        router.route(
                HttpMethod.PUT,
                String.format("/%s/res/:%s/:%s/:%s", commandResponsePrefix, PARAM_TENANT, PARAM_DEVICE_ID, PARAM_COMMAND_REQUEST_ID))
            .handler(ctx -> uploadCommandResponseMessage(HttpContext.from(ctx), getTenantParam(ctx), getDeviceIdParam(ctx),
                    getCommandRequestIdParam(ctx), getCommandResponseStatusParam(ctx)));
    }



    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT);
    }

    private static String getDeviceIdParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_DEVICE_ID);
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

        if (Device.class.isInstance(ctx.user())) {
            final Device device = (Device) ctx.user();
            uploadTelemetryMessage(HttpContext.from(ctx), device.getTenantId(), device.getDeviceId());
        } else {
            handle401(ctx);
        }
    }

    void handlePostEvent(final RoutingContext ctx) {

        if (Device.class.isInstance(ctx.user())) {
            final Device device = (Device) ctx.user();
            uploadEventMessage(HttpContext.from(ctx), device.getTenantId(), device.getDeviceId());
        } else {
            handle401(ctx);
        }
    }

    void handlePostCommandResponse(final RoutingContext ctx) {

        if (Device.class.isInstance(ctx.user())) {
            final Device device = (Device) ctx.user();
            uploadCommandResponseMessage(HttpContext.from(ctx), device.getTenantId(), device.getDeviceId(),
                    getCommandRequestIdParam(ctx), getCommandResponseStatusParam(ctx));
        } else {
            handle401(ctx);
        }
    }

    void assertTenant(final RoutingContext ctx) {

        if (Device.class.isInstance(ctx.user())) {
            final Device device = (Device) ctx.user();
            if (device.getTenantId().equals(getTenantParam(ctx))) {
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
