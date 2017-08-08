/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Contributors:
 * Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.registration;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.hono.util.RegistrationConstants.ACTION_GET;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.HttpServiceBase;
import org.eclipse.hono.util.RegistrationConstants;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Base implementation for a REST interface which can be used to register devices.
 *
 * @param <T> The type of configuration properties used by this service.
 *
 * TODO: add all operations of the Registration API.
 */
public class DeviceRegistryRestServerBase<T extends ServiceConfigProperties> extends HttpServiceBase<T> {

    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";

    @Override
    protected final void addRoutes(final Router router) {
        addRegistrationApiRoutes(router);
    }

    private void addRegistrationApiRoutes(final Router router) {
        router.route(HttpMethod.GET, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
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

        final JsonObject requestMsg = RegistrationConstants.getServiceRequestAsJson(ACTION_GET, tenantId, deviceId);

        vertx.eventBus().send(RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN, requestMsg,
                result -> {
                    HttpServerResponse response = ctx.response();
                    if (result.failed()) {
                        internalServerError(response, "could not get device");
                    } else {
                        final JsonObject registrationResult = (JsonObject) result.result().body();
                        final Integer status = Integer.valueOf(registrationResult.getString("status"));
                        response.setStatusCode(status);
                        switch (status) {
                            case HTTP_OK:
                                final String msg = registrationResult.encodePrettily();
                                response
                                        .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON_UFT8)
                                        .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(msg.length()))
                                        .write(msg);
                            default:
                                response.end();
                            }
                    }
                });
    }
}
