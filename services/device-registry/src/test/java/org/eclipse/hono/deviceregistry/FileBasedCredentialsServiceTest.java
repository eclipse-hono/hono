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

package org.eclipse.hono.deviceregistry;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.HttpURLConnection;

import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link FileBasedCredentialsService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class FileBasedCredentialsServiceTest {

    Vertx vertx = Vertx.vertx();
    Context context;
    FileBasedCredentialsConfigProperties config;

    /**
     * Sets up fixture.
     */
    @Before
    public void setUp() {
        config = new FileBasedCredentialsConfigProperties();
        config.setCredentialsFilename("credentials.json");
    }

    /**
     * Verifies that credentials are successfully loaded from file.
     *  
     * @param ctx The test context.
     */
    @Test()
    public void testLoadCredentials(final TestContext ctx) {

        FileBasedCredentialsService svc = new FileBasedCredentialsService();
        svc.setConfig(config);

        vertx.deployVerticle(svc, ctx.asyncAssertSuccess(s -> {
            assertRegistered(svc, Constants.DEFAULT_TENANT, "sensor1", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, ctx);
        }));
    }

    /**
     * Verifies that only one set of credentials can be registered for an auth-id and type (per tenant).
     * 
     * @param ctx The vert.x test context.
     */
    @Test(timeout = 600)
    public void testAddCredentialsRefusesDuplicateRegistration(final TestContext ctx) {

        FileBasedCredentialsService svc = new FileBasedCredentialsService();
        svc.setConfig(config);

        register(svc, "tenant", "device", "myId", "myType", new JsonArray(), ctx);

        final JsonObject payload2 = new JsonObject()
                .put(CredentialsConstants.FIELD_DEVICE_ID, "other-device")
                .put(CredentialsConstants.FIELD_AUTH_ID, "myId")
                .put(CredentialsConstants.FIELD_TYPE, "myType")
                .put(CredentialsConstants.FIELD_SECRETS, new JsonArray());
        svc.add("tenant", payload2, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_CONFLICT));
        }));
    }

    /**
     * Verifies that the service returns 404 if a client wants to retrieve non-existing credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test(timeout = 300)
    public void testGetCredentialsFailsForNonExistingCredentials(final TestContext ctx) {

        FileBasedCredentialsService svc = new FileBasedCredentialsService();
        svc.setConfig(config);

        svc.get("tenant", "myType", "non-existing", ctx.asyncAssertSuccess(s -> {
                    assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
                }));
    }

    /**
     * Verifies that the service returns existing credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test(timeout = 300)
    public void testGetCredentialsSucceedsForExistingCredentials(final TestContext ctx) {

        FileBasedCredentialsService svc = new FileBasedCredentialsService();
        svc.setConfig(config);
        register(svc, "tenant", "device", "myId", "myType", new JsonArray(), ctx);

        svc.get("tenant", "myType", "myId", ctx.asyncAssertSuccess(s -> {
                    assertThat(s.getStatus(), is(HttpURLConnection.HTTP_OK));
                    assertThat(s.getPayload().getString(CredentialsConstants.FIELD_AUTH_ID), is("myId"));
                    assertThat(s.getPayload().getString(CredentialsConstants.FIELD_TYPE), is("myType"));
                }));

    }

    /**
     * Verifies that the service removes credentials for a given auth-id and type.
     * 
     * @param ctx The vert.x test context.
     */
    @Test(timeout = 300)
    public void testRemoveCredentialsByAuthIdAndTypeSucceeds(final TestContext ctx) {

        FileBasedCredentialsService svc = new FileBasedCredentialsService();
        svc.setConfig(config);
        register(svc, "tenant", "device", "myId", "myType", new JsonArray(), ctx);

        svc.remove("tenant", "myType", "myId", ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NO_CONTENT));
            assertNotRegistered(svc, "tenant", "myId", "myType", ctx);
        }));

    }

    /**
     * Verifies that the service removes all credentials for a device but keeps credentials
     * of other devices.
     * 
     * @param ctx The vert.x test context.
     */
    @Test(timeout = 300)
    public void testRemoveCredentialsByDeviceSucceeds(final TestContext ctx) {

        FileBasedCredentialsService svc = new FileBasedCredentialsService();
        svc.setConfig(config);
        register(svc, "tenant", "device", "myId", "myType", new JsonArray(), ctx);
        register(svc, "tenant", "device", "myOtherId", "myOtherType", new JsonArray(), ctx);
        register(svc, "tenant", "other-device", "thirdId", "myType", new JsonArray(), ctx);

        svc.removeAll("tenant", "device", ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NO_CONTENT));
            assertNotRegistered(svc, "tenant", "myId", "myType", ctx);
            assertNotRegistered(svc, "tenant", "myOtherId", "myOtherType", ctx);
            assertRegistered(svc, "tenant", "thirdId", "myType", ctx);
        }));

    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents updating an existing entry.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a registry containing a set of credentials
        // that has been configured to not allow modification of entries
        config.setModificationEnabled(false);
        FileBasedCredentialsService svc = new FileBasedCredentialsService();
        svc.setConfig(config);
        register(svc, "tenant", "device", "myId", "myType", new JsonArray(), ctx);

        // WHEN trying to update the credentials
        Async updateFailure = ctx.async();
        svc.update("tenant", new JsonObject(), ctx.asyncAssertSuccess(s -> {
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
            updateFailure.complete();
        }));

        // THEN the update fails
        updateFailure.await(2000);
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents removing an existing entry.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveDeviceFailsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a registry containing a set of credentials
        // that has been configured to not allow modification of entries
        config.setModificationEnabled(false);
        FileBasedCredentialsService svc = new FileBasedCredentialsService();
        svc.setConfig(config);
        register(svc, "tenant", "device", "myId", "myType", new JsonArray(), ctx);

        // WHEN trying to remove the credentials
        Async removeFailure = ctx.async();
        svc.update("tenant", new JsonObject(), ctx.asyncAssertSuccess(s -> {
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
            removeFailure.complete();
        }));

        // THEN the removal fails
        removeFailure.await(2000);
    }

    private static void assertRegistered(final CredentialsService svc, final String tenant, final String authId, final String type, final TestContext ctx) {
        Async registration = ctx.async();
        svc.get(tenant, type, authId, ctx.asyncAssertSuccess(t -> {
            assertThat(t.getStatus(), is(HttpURLConnection.HTTP_OK));
            registration.complete();
        }));
        registration.await(300);
    }

    private static void assertNotRegistered(final CredentialsService svc, final String tenant, final String authId, final String type, final TestContext ctx) {
        Async registration = ctx.async();
        svc.get(tenant, type, authId, ctx.asyncAssertSuccess(t -> {
            assertThat(t.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
            registration.complete();
        }));
        registration.await(300);
    }

    private static void register(
            final CredentialsService svc,
            final String tenant,
            final String deviceId,
            final String authId,
            final String type,
            final JsonArray secrets,
            final TestContext ctx) {

        JsonObject data = new JsonObject()
                .put(CredentialsConstants.FIELD_DEVICE_ID, deviceId)
                .put(CredentialsConstants.FIELD_AUTH_ID, authId)
                .put(CredentialsConstants.FIELD_TYPE, type)
                .put(CredentialsConstants.FIELD_SECRETS, secrets);

        Async registration = ctx.async();
        svc.add("tenant", data, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_CREATED));
            registration.complete();
        }));
        registration.await(300);
    }
}
