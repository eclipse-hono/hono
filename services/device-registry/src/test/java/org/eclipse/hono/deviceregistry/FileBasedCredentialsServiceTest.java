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
            svc.getCredentials(Constants.DEFAULT_TENANT, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "sensor1",
                    ctx.asyncAssertSuccess(creds -> {
                        assertThat(creds.getStatus(), is(200));
                    }));
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

        final JsonObject payload1 = new JsonObject()
                .put(CredentialsConstants.FIELD_DEVICE_ID, "device")
                .put(CredentialsConstants.FIELD_AUTH_ID, "myId")
                .put(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY)
                .put(CredentialsConstants.FIELD_SECRETS, new JsonArray());
        register(svc, "tenant", payload1, ctx);

        final JsonObject payload2 = new JsonObject()
                .put(CredentialsConstants.FIELD_DEVICE_ID, "other-device")
                .put(CredentialsConstants.FIELD_AUTH_ID, "myId")
                .put(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY)
                .put(CredentialsConstants.FIELD_SECRETS, new JsonArray());
        svc.addCredentials("tenant", payload2, ctx.asyncAssertSuccess(s -> {
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

        svc.getCredentials("tenant", "myType", "non-existing", ctx.asyncAssertSuccess(s -> {
                    assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
                }));
    }

    /**
     * Verifies that the service returns existing credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test(timeout = 300)
    public void testGetCredentialsSucceedsdForExistingCredentials(final TestContext ctx) {

        FileBasedCredentialsService svc = new FileBasedCredentialsService();
        svc.setConfig(config);
        final JsonObject payload1 = new JsonObject()
                .put(CredentialsConstants.FIELD_DEVICE_ID, "device")
                .put(CredentialsConstants.FIELD_AUTH_ID, "myId")
                .put(CredentialsConstants.FIELD_TYPE, "myType")
                .put(CredentialsConstants.FIELD_SECRETS, new JsonArray());
        register(svc, "tenant", payload1, ctx);


        svc.getCredentials("tenant", "myType", "myId", ctx.asyncAssertSuccess(s -> {
                    assertThat(s.getStatus(), is(HttpURLConnection.HTTP_OK));
                    assertThat(s.getPayload().getString(CredentialsConstants.FIELD_AUTH_ID), is("myId"));
                    assertThat(s.getPayload().getString(CredentialsConstants.FIELD_TYPE), is("myType"));
                }));

    }
    private static void register(final CredentialsService svc, final String tenant, final JsonObject data, final TestContext ctx) {

        Async registration = ctx.async();
        svc.addCredentials("tenant", data, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_CREATED));
            registration.complete();
        }));
        registration.await(300);

    }
}
