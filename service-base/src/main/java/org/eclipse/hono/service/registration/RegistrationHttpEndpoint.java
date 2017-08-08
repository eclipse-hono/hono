/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.registration;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpEndpointUtils;
import org.eclipse.hono.util.RegistrationConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * An {@code HttpEndpoint} for managing device registration information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a>.
 * It receives HTTP requests representing operation invocations and sends them to an address on the vertx
 * event bus for processing. The outcome is then returned to the peer in the HTTP response.
 */
public final class RegistrationHttpEndpoint extends AbstractHttpEndpoint<ServiceConfigProperties> {

    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";

    /**
     * Creates an endpoint for a Vertx instance.
     * 
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public RegistrationHttpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public String getName() {
        return RegistrationConstants.REGISTRATION_ENDPOINT;
    }

    @Override
    public void addRoutes(final Router router) {
        router.route(HttpMethod.GET, String.format("/%s/:%s/:%s", RegistrationConstants.REGISTRATION_ENDPOINT, PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(this::doGetDevice);
    }

    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT);
    }

    private static String getDeviceIdParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_DEVICE_ID);
    }

    private void doGetDevice(final RoutingContext ctx) {

        final String deviceId = getDeviceIdParam(ctx);
        final String tenantId = getTenantParam(ctx);

        final JsonObject requestMsg = RegistrationConstants.getServiceRequestAsJson(RegistrationConstants.ACTION_GET, tenantId, deviceId);

        vertx.eventBus().send(RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN, requestMsg,
                result -> {
                    HttpServerResponse response = ctx.response();
                    if (result.failed()) {
                        HttpEndpointUtils.internalServerError(response, "could not get device");
                    } else {
                        final JsonObject registrationResult = (JsonObject) result.result().body();
                        final Integer status = Integer.valueOf(registrationResult.getString("status"));
                        response.setStatusCode(status);
                        switch (status) {
                            case HttpURLConnection.HTTP_OK:
                                final String msg = registrationResult.encodePrettily();
                                response
                                        .putHeader(HttpHeaders.CONTENT_TYPE, HttpEndpointUtils.CONTENT_TYPE_JSON_UFT8)
                                        .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(msg.length()))
                                        .write(msg);
                            default:
                                response.end();
                            }
                    }
                });
    }
}
