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
package org.eclipse.hono.service.credentials;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.EventBusMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link CredentialsService}.
 */
@ExtendWith(VertxExtension.class)
public class BaseCredentialsServiceTest {

    private static EventBusCredentialsAdapter<?> service;

    private static final String TEST_TENANT = "dummy";

    /**
     * Sets up the fixture.
     */
    @BeforeAll
    public static void setUp() {
        service = createCredentialsService();
    }

    /**
     * Verifies that the base service fails a request for getting credentials
     * with a 400 error code if the type is missing.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetFailsForMissingType(final VertxTestContext ctx) {

        // GIVEN a request for getting credentials that does not specify a type
        final CredentialsObject malformedPayload = new CredentialsObject()
                .setAuthId("bumlux")
                .addSecret(CredentialsObject.emptySecret(null, null));
        final EventBusMessage request = createRequestForPayload(
                CredentialsConstants.CredentialsAction.get,
                JsonObject.mapFrom(malformedPayload));

        // WHEN processing the request
        service.processRequest(request).setHandler(ctx.failing(t -> ctx.verify(() -> {
            // THEN the response contains a 400 error code
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the base service fails a request for getting credentials
     * with a 400 error code if the authentication identifier is missing.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetFailsForMissingAuthId(final VertxTestContext ctx) {

        // GIVEN a request for getting credentials that does not specify an auth ID
        final CredentialsObject malformedPayload = new CredentialsObject()
                .setType("my-type")
                .addSecret(CredentialsObject.emptySecret(null, null));
        final EventBusMessage request = createRequestForPayload(
                CredentialsConstants.CredentialsAction.get,
                JsonObject.mapFrom(malformedPayload));

        // WHEN processing the request
        service.processRequest(request).setHandler(ctx.failing(t -> ctx.verify(() -> {
            // THEN the response contains a 400 error code
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
            ctx.completeNow();
        })));
    }

    private static EventBusMessage createRequestForPayload(final CredentialsConstants.CredentialsAction operation, final JsonObject payload) {

        return EventBusMessage.forOperation(operation.name())
                .setTenant(TEST_TENANT)
                .setJsonPayload(payload);
    }

    private static EventBusCredentialsAdapter<?> createCredentialsService() {

        final var service = new CredentialsService() {

            @Override
            public void get(final String tenantId, final String type, final String authId, final Span span,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
            }

            @Override
            public void get(final String tenantId, final String type, final String authId, final JsonObject clientContext, final Span span,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
            }
        };

        return new EventBusCredentialsAdapter<>() {

            @Override
            protected CredentialsService getService() {
                return service;
            }

            @Override
            public void setConfig(final Object configuration) {
                // TODO Auto-generated method stub

            }
        };
    }
}
