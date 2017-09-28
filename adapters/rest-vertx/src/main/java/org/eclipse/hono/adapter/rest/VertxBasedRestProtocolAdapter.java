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
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BasicAuthHandler;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry &amp; Event API using REST.
 */
public class VertxBasedRestProtocolAdapter
        extends AbstractVertxBasedHttpProtocolAdapter<ProtocolAdapterProperties> implements AuthProvider {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedRestProtocolAdapter.class);
    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";
    private static final String USERNAME_PROPERTY = "username";
    private static final String PASSWORD_PROPERTY = "password";

    @Override
    protected final void addRoutes(final Router router) {
        if (!getConfig().isAuthenticationRequired()) {
            LOG.warn("authentication of devices switched off");
        } else {
            setupBasicAuth(router);
        }
        addTelemetryApiRoutes(router);
        addEventApiRoutes(router);
    }

    private void setupBasicAuth(final Router router) {
        router.route().handler(BasicAuthHandler.create(this));
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
    protected final String getStatusResourcePath() {
        return null;
    }

    @Override
    public final void authenticate(final JsonObject authInfo, final Handler<AsyncResult<User>> resultHandler) {
        String username = authInfo.getString(USERNAME_PROPERTY);
        String password = authInfo.getString(PASSWORD_PROPERTY);
        UsernamePasswordCredentials credentials = UsernamePasswordCredentials.create(username,
                password, getConfig().isSingleTenant());
        if (credentials == null) {
            LOG.debug("authentication failed for device [authId: {}]", username);
            resultHandler.handle(Future.failedFuture("authentication failed"));
        } else {
            this.validateCredentialsForDevice(credentials).setHandler(attempt -> {
                if (attempt.succeeded()) {
                    resultHandler.handle(Future.succeededFuture(null));
                } else {
                    LOG.debug("authentication failed for device [tenantId: {}, authId: {}]", credentials.getTenantId(), credentials.getAuthId());
                    resultHandler.handle(Future.failedFuture("authentication failed"));
                }
            });
        }
    }
}
