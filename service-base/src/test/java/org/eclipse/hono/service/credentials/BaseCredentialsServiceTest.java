/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.credentials;

import java.net.HttpURLConnection;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.EventBusMessage;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests verifying behavior of {@link BaseCredentialsService}.
 */
@RunWith(VertxUnitRunner.class)
public class BaseCredentialsServiceTest {

    private static BaseCredentialsService<ServiceConfigProperties> service;

    private static final String TEST_TENANT = "dummy";

    /**
     * Time out each test after 5 seconds.
     */
    public Timeout timeout = Timeout.seconds(5);

    /**
     * Sets up the fixture.
     */
    @BeforeClass
    public static void setUp() {
        service = createBaseCredentialsService();
    }

    /**
     * Verifies that the base service fails a request for getting credentials
     * with a 400 error code if the type is missing.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetFailsForMissingType(final TestContext ctx) {

        // GIVEN a request for getting credentials that does not specify a type
        final CredentialsObject malformedPayload = new CredentialsObject()
                .setAuthId("bumlux")
                .addSecret(CredentialsObject.emptySecret(null, null));
        final EventBusMessage request = createRequestForPayload(
                CredentialsConstants.CredentialsAction.get,
                JsonObject.mapFrom(malformedPayload));

        // WHEN processing the request
        service.processRequest(request).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the response contains a 400 error code
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service fails a request for getting credentials
     * with a 400 error code if the authentication identifier is missing.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetFailsForMissingAuthId(final TestContext ctx) {

        // GIVEN a request for getting credentials that does not specify an auth ID
        final CredentialsObject malformedPayload = new CredentialsObject()
                .setType("my-type")
                .addSecret(CredentialsObject.emptySecret(null, null));
        final EventBusMessage request = createRequestForPayload(
                CredentialsConstants.CredentialsAction.get,
                JsonObject.mapFrom(malformedPayload));

        // WHEN processing the request
        service.processRequest(request).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the response contains a 400 error code
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    private static EventBusMessage createRequestForPayload(final CredentialsConstants.CredentialsAction operation, final JsonObject payload) {

        return EventBusMessage.forOperation(operation.name())
                .setTenant(TEST_TENANT)
                .setJsonPayload(payload);
    }

    private static BaseCredentialsService<ServiceConfigProperties> createBaseCredentialsService() {

        return new BaseCredentialsService<ServiceConfigProperties>() {

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
            }

            @Override
            public void getAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler){
            }
        };
    }
}
