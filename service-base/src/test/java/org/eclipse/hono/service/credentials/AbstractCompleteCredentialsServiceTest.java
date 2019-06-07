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

import static org.junit.Assert.assertEquals;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;

/**
 * Abstract class used as a base for verifying behavior of {@link CompleteCredentialsService} in device registry implementations.
 *
 */
@Deprecated
public abstract class AbstractCompleteCredentialsServiceTest {

    /**
     * Gets credentials service being tested.
     * @return The credentials service
     */
    public abstract CompleteCredentialsService getCompleteCredentialsService();

    /**
     * Gets the cache directive that is supposed to be used for a given type of
     * credentials.
     * <p>
     * This default implementation always returns {@code CacheDirective#noCacheDirective()}.
     * 
     * @param credentialsType The type of credentials.
     * @return The expected cache directive.
     */
    protected CacheDirective getExpectedCacheDirective(final String credentialsType) {
        return CacheDirective.noCacheDirective();
    }

    /**
     * Verifies that only one set of credentials can be registered for an auth-id and type (per tenant).
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddCredentialsRefusesDuplicateRegistration(final VertxTestContext ctx) {

        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType")
        .compose(ok -> {

            final Future<CredentialsResult<JsonObject>> result = Future.future();
            final JsonObject payload2 = new JsonObject()
                    .put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, "other-device")
                    .put(CredentialsConstants.FIELD_AUTH_ID, "myId")
                    .put(CredentialsConstants.FIELD_TYPE, "myType")
                    .put(CredentialsConstants.FIELD_SECRETS, new JsonArray());

            getCompleteCredentialsService().add("tenant", payload2, result);
            return result;
        }).setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus());
            ctx.completeNow();
        })));
;
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

        register(getCompleteCredentialsService(), "tenant", "device", "myId", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)
        .compose(ok -> {
            final Future<CredentialsResult<JsonObject>> result = Future.future();
            getCompleteCredentialsService().get("tenant", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "myId", result);
            return result;
        })
        .setHandler(ctx.succeeding(r -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
            assertEquals("myId", r.getPayload().getString(CredentialsConstants.FIELD_AUTH_ID));
            assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, r.getPayload().getString(CredentialsConstants.FIELD_TYPE));
            assertEquals(getExpectedCacheDirective(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD), r.getCacheDirective());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that service returns existing credentials for proper client context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsSucceedsForProperClientContext(final VertxTestContext ctx) {

        final JsonObject clientContext = new JsonObject().put("client-id", "gateway-one");

        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", clientContext, new JsonArray())
        .compose(ok -> {
            final Future<CredentialsResult<JsonObject>> result = Future.future();
            getCompleteCredentialsService().get("tenant", "myType", "myId", clientContext, result);
            return result;
        })
        .setHandler(ctx.succeeding(r -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
            assertEquals("myId", r.getPayload().getString(CredentialsConstants.FIELD_AUTH_ID));
            assertEquals("myType", r.getPayload().getString(CredentialsConstants.FIELD_TYPE));
            assertEquals(getExpectedCacheDirective("myType"), r.getCacheDirective());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that service returns 404 if a client provides wrong client context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForWrongClientContext(final VertxTestContext ctx) {

        final JsonObject clientContext = new JsonObject().put("client-id", "gateway-one");

        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", clientContext, new JsonArray())
        .compose(ok -> {
            final Future<CredentialsResult<JsonObject>> result = Future.future();
            final JsonObject testContext = new JsonObject().put("client-id", "gateway-two");
            getCompleteCredentialsService().get("tenant", "myType", "myId", testContext, result);
            return result;
        })
        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, s.getStatus());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the service removes credentials for a given auth-id and type.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsByAuthIdAndTypeSucceeds(final VertxTestContext ctx) {

        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType")
        .compose(ok -> {
            final Future<CredentialsResult<JsonObject>> result = Future.future();
            getCompleteCredentialsService().remove("tenant", "myType", "myId", result);
            return result;
        }).map(s -> ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus())))
        .compose(c -> assertNotRegistered(getCompleteCredentialsService(), "tenant", "myId", "myType"))
        .setHandler(ctx.completing());
    }

    /**
     * Verifies that the service removes all credentials for a device but keeps credentials
     * of other devices.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsByDeviceSucceeds(final VertxTestContext ctx) {

        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType")
        .compose(ok -> register(getCompleteCredentialsService(), "tenant", "device", "myOtherId", "myOtherType"))
        .compose(ok -> register(getCompleteCredentialsService(), "tenant", "other-device", "thirdId", "myType"))
        .compose(ok -> {
            final Future<CredentialsResult<JsonObject>> result = Future.future();
            getCompleteCredentialsService().removeAll("tenant", "device", result);
            return result;
        }).map(s -> ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus())))
        .compose(c -> assertNotRegistered(getCompleteCredentialsService(), "tenant", "myId", "myType"))
        .compose(ok -> assertNotRegistered(getCompleteCredentialsService(), "tenant", "myOtherId", "myOtherType"))
        .compose(ok -> assertRegistered(getCompleteCredentialsService(), "tenant", "thirdId", "myType"))
        .setHandler(ctx.completing());
    }

    /**
     * Verifies that credentials of a particular type are registered.
     * 
     * @param svc The credentials service to probe.
     * @param tenant The tenant that the device belongs to.
     * @param authId The authentication identifier used by the device.
     * @param type The type of credentials.
     * @return A succeeded future if the credentials exist.
     */
    protected static Future<Void> assertRegistered(
            final CredentialsService svc,
            final String tenant,
            final String authId,
            final String type) {

        return assertGet(svc, tenant, authId, type, HttpURLConnection.HTTP_OK);
    }

    /**
     * Verifies that credentials of a particular type are not registered.
     * 
     * @param svc The credentials service to probe.
     * @param tenant The tenant that the device belongs to.
     * @param authId The authentication identifier used by the device.
     * @param type The type of credentials.
     * @return A succeeded future if the credentials do not exist.
     */
    protected static Future<Void> assertNotRegistered(
            final CredentialsService svc,
            final String tenant,
            final String authId,
            final String type) {

        return assertGet(svc, tenant, authId, type, HttpURLConnection.HTTP_NOT_FOUND);
    }

    private static Future<Void> assertGet(
            final CredentialsService svc,
            final String tenant,
            final String authId,
            final String type,
            final int expectedStatusCode) {

        final Future<CredentialsResult<JsonObject>> result = Future.future();
        svc.get(tenant, type, authId, result);
        return result.map(r -> {
            if (r.getStatus() == expectedStatusCode) {
                return null;
            } else {
                throw new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED);
            }
        });
    }

    /**
     * Registers a set of empty credentials for a device.
     * 
     * @param svc The service to register the credentials with.
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param authId The authentication identifier to register the secret for.
     * @param type The type of secret.
     * @return A succeeded future if the credentials have been registered successfully.
     */
    protected static Future<CredentialsResult<JsonObject>> register(
            final CompleteCredentialsService svc,
            final String tenant,
            final String deviceId,
            final String authId,
            final String type) {

        return register(svc, tenant, deviceId, authId, type, null, new JsonArray());
    }

    /**
     * Registers a set of credentials for a device.
     * 
     * @param svc The service to register the credentials with.
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param authId The authentication identifier to register the secret for.
     * @param type The type of secret.
     * @param clientContext The client context to associate the credentials with.
     * @param secrets The secrets to register for the authentication id.
     * @return A succeeded future if the credentials have been registered successfully.
     */
    protected static Future<CredentialsResult<JsonObject>> register(
            final CompleteCredentialsService svc,
            final String tenant,
            final String deviceId,
            final String authId,
            final String type,
            final JsonObject clientContext,
            final JsonArray secrets) {

        final JsonObject data = new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                .put(CredentialsConstants.FIELD_AUTH_ID, authId)
                .put(CredentialsConstants.FIELD_TYPE, type)
                .put(CredentialsConstants.FIELD_SECRETS, secrets);

        if (clientContext != null) {
            data.mergeIn(clientContext);
        }

        return register(svc, tenant, data);
    }

    /**
     * Registers a set of credentials for a device.
     * 
     * @param svc The service to register the credentials with.
     * @param tenant The tenant that the device belongs to.
     * @param credentials The credentials to register.
     * @return A succeeded future if the credentials have been registered successfully.
     */
    protected static Future<CredentialsResult<JsonObject>> register(
            final CompleteCredentialsService svc,
            final String tenant,
            final JsonObject credentials) {

        final Future<CredentialsResult<JsonObject>> result = Future.future();
        svc.add(tenant, credentials, result);
        return result.map(r -> {
            if (HttpURLConnection.HTTP_CREATED == r.getStatus()) {
                return r;
            } else {
                throw new ClientErrorException(r.getStatus());
            }
        });
    }
}
