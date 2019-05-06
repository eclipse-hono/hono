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

import static org.junit.Assert.assertEquals;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;

/**
 * Abstract class used as a base for verifying behavior of {@link CompleteCredentialsService} in device registry implementations.
 *
 */
public abstract class AbstractCompleteCredentialsServiceTest {

    /**
     * Gets credentials service being tested.
     * @return The credentials service
     */
    public abstract CompleteCredentialsService getCompleteCredentialsService();

    /**
     * Verifies that only one set of credentials can be registered for an auth-id and type (per tenant).
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddCredentialsRefusesDuplicateRegistration(final VertxTestContext ctx) {

        final Future registration = Future.future();
        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", ctx, registration);

        final JsonObject payload2 = new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, "other-device")
                .put(CredentialsConstants.FIELD_AUTH_ID, "myId")
                .put(CredentialsConstants.FIELD_TYPE, "myType")
                .put(CredentialsConstants.FIELD_SECRETS, new JsonArray());

        registration.setHandler(r-> {
            getCompleteCredentialsService().add("tenant", payload2, ctx.succeeding(s -> ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus());
                ctx.completeNow();
            })));
        });
    }

    /**
     * Verifies that the service returns 404 if a client wants to retrieve non-existing credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForNonExistingCredentials(final VertxTestContext ctx) {

        getCompleteCredentialsService().get("tenant", "myType", "non-existing", ctx.succeeding(s -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, s.getStatus());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the service returns existing credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsSucceedsForExistingCredentials(final VertxTestContext ctx) {

        final Future registration = Future.future();
        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", ctx, registration);

        registration.setHandler(r -> {
            getCompleteCredentialsService().get("tenant", "myType", "myId", ctx.succeeding(s -> ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
                assertEquals("myId", s.getPayload().getString(CredentialsConstants.FIELD_AUTH_ID));
                assertEquals("myType", s.getPayload().getString(CredentialsConstants.FIELD_TYPE));
                ctx.completeNow();
            })));
        });
    }

    /**
     * Verifies that service returns existing credentials for proper client context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsSucceedsForProperClientContext(final VertxTestContext ctx) {
        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        final Future registration = Future.future();
        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", clientContext, new JsonArray(), ctx, registration);

        registration.setHandler( r-> {
            getCompleteCredentialsService()
                    .get("tenant", "myType", "myId", clientContext, ctx.succeeding(s -> ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
                        assertEquals("myId", s.getPayload().getString(CredentialsConstants.FIELD_AUTH_ID));
                        assertEquals("myType", s.getPayload().getString(CredentialsConstants.FIELD_TYPE));
                        ctx.completeNow();
                    })));
        });
    }

    /**
     * Verifies that service returns 404 if a client provides wrong client context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForWrongClientContext(final VertxTestContext ctx) {
        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        final Future registration = Future.future();
        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", clientContext, new JsonArray(), ctx, registration);

        final JsonObject testContext = new JsonObject()
                .put("client-id", "gateway-two");

        registration.setHandler(r -> {
            getCompleteCredentialsService()
                    .get("tenant", "myType", "myId", testContext, ctx.succeeding(s -> ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, s.getStatus());
                        ctx.completeNow();
                    })));
        });
    }

    /**
     * Verifies that the service removes credentials for a given auth-id and type.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsByAuthIdAndTypeSucceeds(final VertxTestContext ctx) {

        final Future registration = Future.future();
        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", ctx, registration);

        registration.setHandler(r-> {
            getCompleteCredentialsService().remove("tenant", "myType", "myId", ctx.succeeding(s -> ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus());
                assertNotRegistered(getCompleteCredentialsService(), "tenant", "myId", "myType", ctx);
                ctx.completeNow();
            })));
        });
    }

    /**
     * Verifies that the service removes all credentials for a device but keeps credentials
     * of other devices.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsByDeviceSucceeds(final VertxTestContext ctx) {

        final Future registration1 = Future.future();
        final Future registration2 = Future.future();
        final Future registration3 = Future.future();
        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", ctx, registration1);
        register(getCompleteCredentialsService(), "tenant", "device", "myOtherId", "myOtherType", ctx, registration2);
        register(getCompleteCredentialsService(), "tenant", "other-device", "thirdId", "myType", ctx, registration3);

        CompositeFuture.all(registration1, registration2, registration3).setHandler( r-> {
            getCompleteCredentialsService().removeAll("tenant", "device", ctx.succeeding(s -> ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus());
                assertNotRegistered(getCompleteCredentialsService(), "tenant", "myId", "myType", ctx);
                assertNotRegistered(getCompleteCredentialsService(), "tenant", "myOtherId", "myOtherType", ctx);
                assertRegistered(getCompleteCredentialsService(), "tenant", "thirdId", "myType", ctx);
                ctx.completeNow();
            })));
        });
    }

    protected static void assertRegistered(
            final CompleteCredentialsService svc,
            final String tenant,
            final String authId,
            final String type,
            final VertxTestContext ctx) {

        svc.get(tenant, type, authId, ctx.succeeding(t -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_OK, t.getStatus());
        })));
    }

    protected static void assertNotRegistered(
            final CompleteCredentialsService svc,
            final String tenant,
            final String authId,
            final String type,
            final VertxTestContext ctx) {

        svc.get(tenant, type, authId, ctx.succeeding(t -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, t.getStatus());
        })));
    }

    protected static void register(
            final CompleteCredentialsService svc,
            final String tenant,
            final String deviceId,
            final String authId,
            final String type,
            final VertxTestContext ctx,
            final Future future) {
        register(svc, tenant, deviceId, authId, type, null, new JsonArray(), ctx, future);
    }

    protected static void register(
            final CompleteCredentialsService svc,
            final String tenant,
            final String deviceId,
            final String authId,
            final String type,
            final JsonObject clientContext,
            final JsonArray secrets,
            final VertxTestContext ctx,
            final Future future) {

        final JsonObject data = new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                .put(CredentialsConstants.FIELD_AUTH_ID, authId)
                .put(CredentialsConstants.FIELD_TYPE, type)
                .put(CredentialsConstants.FIELD_SECRETS, secrets);

        if (clientContext != null) {
            data.mergeIn(clientContext);
        }

        svc.add("tenant", data, ctx.succeeding(s -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
            future.complete();
        })));
    }
}
