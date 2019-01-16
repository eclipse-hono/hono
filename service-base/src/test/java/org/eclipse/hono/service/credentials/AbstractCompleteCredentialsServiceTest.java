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

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.Test;

import java.net.HttpURLConnection;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

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
    public void testAddCredentialsRefusesDuplicateRegistration(final TestContext ctx) {

        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", ctx);

        final JsonObject payload2 = new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, "other-device")
                .put(CredentialsConstants.FIELD_AUTH_ID, "myId")
                .put(CredentialsConstants.FIELD_TYPE, "myType")
                .put(CredentialsConstants.FIELD_SECRETS, new JsonArray());
        final Async add = ctx.async();
        getCompleteCredentialsService().add("tenant", payload2, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_CONFLICT));
            add.complete();
        }));
        add.await();
    }

    /**
     * Verifies that the service returns 404 if a client wants to retrieve non-existing credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForNonExistingCredentials(final TestContext ctx) {

        final Async get = ctx.async();
        getCompleteCredentialsService().get("tenant", "myType", "non-existing", ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
            get.complete();
        }));
        get.await();
    }

    /**
     * Verifies that the service returns existing credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsSucceedsForExistingCredentials(final TestContext ctx) {

        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", ctx);

        final Async get = ctx.async();
        getCompleteCredentialsService().get("tenant", "myType", "myId", ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_OK));
            assertThat(s.getPayload().getString(CredentialsConstants.FIELD_AUTH_ID), is("myId"));
            assertThat(s.getPayload().getString(CredentialsConstants.FIELD_TYPE), is("myType"));
            get.complete();
        }));
        get.await();
    }

    /**
     * Verifies that service returns existing credentials for proper client context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsSucceedsForProperClientContext(final TestContext ctx) {
        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", clientContext, new JsonArray(), ctx);


        final Async get = ctx.async();
        getCompleteCredentialsService().get("tenant", "myType", "myId", clientContext, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_OK));
            assertThat(s.getPayload().getString(CredentialsConstants.FIELD_AUTH_ID), is("myId"));
            assertThat(s.getPayload().getString(CredentialsConstants.FIELD_TYPE), is("myType"));
            get.complete();
        }));
        get.await(2000);
    }

    /**
     * Verifies that service returns 404 if a client provides wrong client context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForWrongClientContext(final TestContext ctx) {
        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", clientContext, new JsonArray(), ctx);

        final JsonObject testContext = new JsonObject()
                .put("client-id", "gateway-two");

        final Async get = ctx.async();
        getCompleteCredentialsService().get("tenant", "myType", "myId", testContext, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
            get.complete();
        }));
        get.await(2000);
    }

    /**
     * Verifies that the service removes credentials for a given auth-id and type.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsByAuthIdAndTypeSucceeds(final TestContext ctx) {

        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", ctx);

        final Async remove = ctx.async();
        getCompleteCredentialsService().remove("tenant", "myType", "myId", ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NO_CONTENT));
            assertNotRegistered(getCompleteCredentialsService(), "tenant", "myId", "myType", ctx);
            remove.complete();
        }));
        remove.await();
    }

    /**
     * Verifies that the service removes all credentials for a device but keeps credentials
     * of other devices.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsByDeviceSucceeds(final TestContext ctx) {

        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", ctx);
        register(getCompleteCredentialsService(), "tenant", "device", "myOtherId", "myOtherType", ctx);
        register(getCompleteCredentialsService(), "tenant", "other-device", "thirdId", "myType", ctx);

        final Async remove = ctx.async();
        getCompleteCredentialsService().removeAll("tenant", "device", ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NO_CONTENT));
            assertNotRegistered(getCompleteCredentialsService(), "tenant", "myId", "myType", ctx);
            assertNotRegistered(getCompleteCredentialsService(), "tenant", "myOtherId", "myOtherType", ctx);
            assertRegistered(getCompleteCredentialsService(), "tenant", "thirdId", "myType", ctx);
            remove.complete();
        }));
        remove.await();
    }

    protected static void assertRegistered(
            final CompleteCredentialsService svc,
            final String tenant,
            final String authId,
            final String type,
            final TestContext ctx) {

        final Async registration = ctx.async();
        svc.get(tenant, type, authId, ctx.asyncAssertSuccess(t -> {
            assertThat(t.getStatus(), is(HttpURLConnection.HTTP_OK));
            registration.complete();
        }));
        registration.await();
    }

    protected static void assertNotRegistered(
            final CompleteCredentialsService svc,
            final String tenant,
            final String authId,
            final String type,
            final TestContext ctx) {

        final Async registration = ctx.async();
        svc.get(tenant, type, authId, ctx.asyncAssertSuccess(t -> {
            assertThat(t.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
            registration.complete();
        }));
        registration.await();
    }

    protected static void register(
            final CompleteCredentialsService svc,
            final String tenant,
            final String deviceId,
            final String authId,
            final String type,
            final TestContext ctx) {
        register(svc, tenant, deviceId, authId, type, null, new JsonArray(), ctx);
    }

    protected static void register(
            final CompleteCredentialsService svc,
            final String tenant,
            final String deviceId,
            final String authId,
            final String type,
            final JsonObject clientContext,
            final JsonArray secrets,
            final TestContext ctx) {

        final JsonObject data = new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                .put(CredentialsConstants.FIELD_AUTH_ID, authId)
                .put(CredentialsConstants.FIELD_TYPE, type)
                .put(CredentialsConstants.FIELD_SECRETS, secrets);

        if (clientContext != null) {
            data.mergeIn(clientContext);
        }

        final Async registration = ctx.async();
        svc.add("tenant", data, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_CREATED));
            registration.complete();
        }));
        registration.await();
    }
}
