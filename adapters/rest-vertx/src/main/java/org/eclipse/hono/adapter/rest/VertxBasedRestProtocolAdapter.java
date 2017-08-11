/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.rest;

import org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter;
import org.eclipse.hono.config.ServiceConfigProperties;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry &amp; Event API using REST.
 */
public final class VertxBasedRestProtocolAdapter extends AbstractVertxBasedHttpProtocolAdapter<ServiceConfigProperties> {

    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";

    @Override
    protected void addRoutes(final Router router) {
        addTelemetryApiRoutes(router);
        addEventApiRoutes(router);
    }

    private void addTelemetryApiRoutes(final Router router) {

        // route for uploading telemetry data
        router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .handler(ctx -> uploadTelemetryMessage(ctx, getTenantParam(ctx), getDeviceIdParam(ctx)));
    }

    private void addEventApiRoutes(final Router router) {

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

    /**
     * Returns {@code null} to disable status resource.
     */
    @Override
    protected String getStatusResourcePath() {
        return null;
    }
}
