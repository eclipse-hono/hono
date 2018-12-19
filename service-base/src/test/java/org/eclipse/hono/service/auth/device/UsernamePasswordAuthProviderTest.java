/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.service.auth.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link UsernamePasswordAuthProviderTest}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class UsernamePasswordAuthProviderTest {

    private static Vertx vertx;

    private CredentialsObject credentialsOnRecord;
    private UsernamePasswordCredentials deviceCredentials = UsernamePasswordCredentials.create("device@DEFAULT_TENANT", "pwd", false);
    private UsernamePasswordAuthProvider provider;
    private HonoClient credentialsServiceClient;
    private CredentialsClient credentialsClient;
    private HonoPasswordEncoder pwdEncoder;

    /**
     * Time out all test after 2 seconds.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(2, TimeUnit.SECONDS);

    /**
     * Initializes vert.x.
     */
    @BeforeClass
    public static void init() {
        vertx = Vertx.vertx();
    }

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        credentialsOnRecord = new CredentialsObject()
                .setAuthId("device")
                .setDeviceId("4711")
                .setType(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)
                .setEnabled(true);
        credentialsClient = mock(CredentialsClient.class);
        when(credentialsClient.get(anyString(), anyString())).thenReturn(Future.succeededFuture(credentialsOnRecord));
        credentialsServiceClient = mock(HonoClient.class);
        when(credentialsServiceClient.getOrCreateCredentialsClient(anyString())).thenReturn(Future.succeededFuture(credentialsClient));
        pwdEncoder = mock(HonoPasswordEncoder.class);
        when(pwdEncoder.matches(anyString(), any(JsonObject.class))).thenReturn(true);


        provider = new UsernamePasswordAuthProvider(credentialsServiceClient, pwdEncoder, new ServiceConfigProperties());
    }

    /**
     * Verifies that the provider fails to authenticate a device when not
     * running on a vert.x Context.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateRequiresVertxContext(final TestContext ctx) {

        provider.authenticate(deviceCredentials, ctx.asyncAssertFailure(e -> {
            ctx.assertTrue(e instanceof IllegalStateException);
        }));
    }

    /**
     * Verifies that the provider succeeds to validate matching credentials when
     * running on a vert.x Context.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateSucceedsWhenRunningOnVertxContext(final TestContext ctx) {

        vertx.runOnContext(go -> {
            provider.authenticate(deviceCredentials, ctx.asyncAssertSuccess(device -> {
                ctx.assertEquals("4711", device.getDeviceId());
                ctx.assertEquals("DEFAULT_TENANT", device.getTenantId());
            }));
        });
    }

    /**
     * Verifies that the provider fails to validate wrong credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsForWrongCredentials(final TestContext ctx) {

        when(pwdEncoder.matches(eq("wrong_pwd"), any(JsonObject.class))).thenReturn(false);

        deviceCredentials = UsernamePasswordCredentials.create("device@DEFAULT_TENANT", "wrong_pwd", false);
        vertx.runOnContext(go -> {
            provider.authenticate(deviceCredentials, ctx.asyncAssertFailure(e -> {
                final ClientErrorException error = (ClientErrorException) e;
                ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, error.getErrorCode());
            }));
        });
    }

    /**
     * Verifies that credentials validation fails if none of the secrets on record are
     * valid any more.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsIfNoSecretsAreValidAnymore(final TestContext ctx) {

        credentialsOnRecord.addSecret(CredentialsObject.emptySecret(null, Instant.now().minusSeconds(120)));
        vertx.runOnContext(go -> {
            provider.authenticate(deviceCredentials, ctx.asyncAssertFailure(t -> {
                // THEN authentication fails with a 401 client error
                ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ClientErrorException) t).getErrorCode());
            }));
        });
    }

    /**
     * Verifies that credentials validation fails if none of the secrets on record are
     * valid yet.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsIfNoSecretsAreValidYet(final TestContext ctx) {

        credentialsOnRecord.addSecret(CredentialsObject.emptySecret(Instant.now().plusSeconds(120), null));
        vertx.runOnContext(go -> {
            provider.authenticate(deviceCredentials, ctx.asyncAssertFailure(t -> {
                // THEN authentication fails with a 401 client error
                ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ClientErrorException) t).getErrorCode());
            }));
        });
    }
}
