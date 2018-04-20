/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.http.vertx;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter;
import org.eclipse.hono.adapter.http.HonoBasicAuthHandler;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry &amp; Event API using HTTP.
 */
public final class VertxBasedHttpProtocolAdapter extends AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedHttpProtocolAdapter.class);
    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";

    private HonoClientBasedAuthProvider usernamePasswordAuthProvider;

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
    public void setUsernamePasswordAuthProvider(final HonoClientBasedAuthProvider provider) {
        this.usernamePasswordAuthProvider = Objects.requireNonNull(provider);
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

            final HonoClientBasedAuthProvider authProvider = Optional.ofNullable(usernamePasswordAuthProvider)
                .orElse(new UsernamePasswordAuthProvider(getCredentialsServiceClient(), getConfig()));

            final Handler<RoutingContext> basicAuthHandler = new HonoBasicAuthHandler(authProvider, getConfig().getRealm());
            addTelemetryApiRoutes(router, basicAuthHandler);
            addEventApiRoutes(router, basicAuthHandler);

        } else {

            LOG.warn("device authentication has been disabled");
            LOG.warn("any device may publish data on behalf of all other devices");
            addTelemetryApiRoutes(router, null);
            addEventApiRoutes(router, null);
        }
    }

    private void addTelemetryApiRoutes(final Router router, final Handler<RoutingContext> basicAuthHandler) {

        // support CORS headers for PUTing telemetry
        router.routeWithRegex("\\/telemetry\\/[^\\/]+\\/.*").handler(CorsHandler.create(getConfig().getCorsAllowedOrigin())
                .allowedMethod(HttpMethod.PUT)
                .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                .allowedHeader(HttpHeaders.CONTENT_TYPE.toString()));

        if (getConfig().isAuthenticationRequired()) {

            // support CORS headers for POSTing telemetry
            router.route("/telemetry").handler(CorsHandler.create(getConfig().getCorsAllowedOrigin())
                    .allowedMethod(HttpMethod.POST)
                    .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                    .allowedHeader(HttpHeaders.CONTENT_TYPE.toString()));

            // require Basic auth for POSTing telemetry
            router.route(HttpMethod.POST, "/telemetry").handler(basicAuthHandler);

            // route for posting telemetry data using tenant and device ID determined as part of
            // device authentication
            router.route(HttpMethod.POST, "/telemetry").handler(this::handlePostTelemetry);

            // require Basic auth for PUTing telemetry
            router.route(HttpMethod.PUT, "/telemetry/*").handler(basicAuthHandler);
            // assert that authenticated device's tenant matches tenant from path variables
            router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(this::assertTenant);
        }

        // route for uploading telemetry data
        router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(ctx -> uploadTelemetryMessage(ctx, getTenantParam(ctx), getDeviceIdParam(ctx)));
    }

    private void addEventApiRoutes(final Router router, final Handler<RoutingContext> basicAuthHandler) {

        // support CORS headers for PUTing events
        router.routeWithRegex("\\/event\\/[^\\/]+\\/.*").handler(CorsHandler.create(getConfig().getCorsAllowedOrigin())
                .allowedMethod(HttpMethod.PUT)
                .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                .allowedHeader(HttpHeaders.CONTENT_TYPE.toString()));

        if (getConfig().isAuthenticationRequired()) {

            // support CORS headers for POSTing events
            router.route("/event").handler(CorsHandler.create(getConfig().getCorsAllowedOrigin())
                    .allowedMethod(HttpMethod.POST)
                    .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                    .allowedHeader(HttpHeaders.CONTENT_TYPE.toString()));

            // require Basic auth for POSTing telemetry
            router.route(HttpMethod.POST, "/event").handler(basicAuthHandler);

            // route for posting events using tenant and device ID determined as part of
            // device authentication
            router.route(HttpMethod.POST, "/event").handler(this::handlePostEvent);
            // route for asserting that authenticated device's tenant matches tenant from path variables
            router.route(HttpMethod.PUT, String.format("/event/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(this::assertTenant);
        }

        // route for sending event messages
        router.route(HttpMethod.PUT, String.format("/event/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(ctx -> uploadEventMessage(ctx, getTenantParam(ctx), getDeviceIdParam(ctx)));
    }

    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT);
    }

    private static String getDeviceIdParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_DEVICE_ID);
    }

    private void handle401(final RoutingContext ctx) {
        HttpUtils.unauthorized(ctx, "Basic realm=\"" + getConfig().getRealm() + "\"");
    }

    void handlePostTelemetry(final RoutingContext ctx) {

        if (Device.class.isInstance(ctx.user())) {
            Device device = (Device) ctx.user();
            uploadTelemetryMessage(ctx, device.getTenantId(), device.getDeviceId());
        } else {
            handle401(ctx);
        }
    }

    void handlePostEvent(final RoutingContext ctx) {

        if (Device.class.isInstance(ctx.user())) {
            Device device = (Device) ctx.user();
            uploadEventMessage(ctx, device.getTenantId(), device.getDeviceId());
        } else {
            handle401(ctx);
        }
    }

    void assertTenant(final RoutingContext ctx) {

        if (Device.class.isInstance(ctx.user())) {
            Device device = (Device) ctx.user();
            if (device.getTenantId().equals(getTenantParam(ctx))) {
                ctx.next();
            } else {
                ctx.fail(HttpURLConnection.HTTP_FORBIDDEN);
            }
        } else {
            handle401(ctx);
        }
    }
}
