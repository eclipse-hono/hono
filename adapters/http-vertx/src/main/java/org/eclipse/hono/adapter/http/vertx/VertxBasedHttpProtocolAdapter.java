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

import org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter;
import org.eclipse.hono.adapter.http.HonoAuthHandlerImpl;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry &amp; Event API using HTTP.
 */
public final class VertxBasedHttpProtocolAdapter extends AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedHttpProtocolAdapter.class);
    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";

    @Override
    protected String getTypeName() {
        return "hono-http";
    }

    @Override
    protected final void addRoutes(final Router router) {
        if (!getConfig().isAuthenticationRequired()) {
            LOG.warn("device authentication has been disabled");
            LOG.warn("any device may publish data on behalf of all other devices");
        } else {
            setupBasicAuth(router);
        }
        addTelemetryApiRoutes(router);
        addEventApiRoutes(router);
    }

    private void setupBasicAuth(final Router router) {
        router.route().handler(new HonoAuthHandlerImpl(getCredentialsAuthProvider(), getConfig().getRealm()) {
            @Override
            protected void processException(RoutingContext ctx, Throwable exception) {
                if (exception instanceof ServiceInvocationException) {
                    ctx.fail(((ServiceInvocationException) exception).getErrorCode());
                } else {
                    super.processException(ctx, exception);
                }
            }
        });
    }

    private void addTelemetryApiRoutes(final Router router) {

        if (getConfig().isAuthenticationRequired()) {
            // route for posting telemetry data using tenant and device ID determined as part of
            // device authentication
            router.route(HttpMethod.POST, "/telemetry").handler(this::handlePostTelemetry);
            // route for asserting that authenticated identity matches path variables
            router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(this::assertDeviceIdentity);
        }

        // route for uploading telemetry data
        router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(ctx -> uploadTelemetryMessage(ctx, getTenantParam(ctx), getDeviceIdParam(ctx)));
    }

    private void addEventApiRoutes(final Router router) {

        if (getConfig().isAuthenticationRequired()) {
            // route for posting events using tenant and device ID determined as part of
            // device authentication
            router.route(HttpMethod.POST, "/event").handler(this::handlePostEvent);
            // route for asserting that authenticated identity matches path variables
            router.route(HttpMethod.PUT, String.format("/event/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(this::assertDeviceIdentity);
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

    void assertDeviceIdentity(final RoutingContext ctx) {

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
