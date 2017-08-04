/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Contributors:
 * Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.deviceregistry;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.hono.util.RegistrationConstants.ACTION_GET;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.HttpServiceBase;
import org.eclipse.hono.util.RegistrationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * A server that provides a REST interface to register devices. It's parent additionally provides the
 * <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a> and
 * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a> by means of AMQP.
 * <p>
 * It contains code copied from {@code VertxBasedRestProtocolAdapter}.
 * This is only a transition step to keep changes small.
 * Currently only the "GET" operation is implemented.
 *
 * TODO: add all operations of the Registration API.
 */
public class DeviceRegistryRestServer extends HttpServiceBase<ServiceConfigProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistryRestServer.class);

    private static final String PARAM_TENANT    = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";


    @Autowired
    @Qualifier("rest")
    @Override
    public void setConfig(final ServiceConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    @Override
    protected void addRoutes(Router router) {
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
                        JsonObject registrationResult = (JsonObject) result.result().body();
                        Integer status = Integer.valueOf(registrationResult.getString("status"));
                        response.setStatusCode(status);
                        switch (status) {
                            case HTTP_OK:
                                String msg = registrationResult.encodePrettily();
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





