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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link UsernamePasswordAuthProviderTest}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class UsernamePasswordAuthProviderTest {

    private static final Vertx vertx = Vertx.vertx();

    private final CredentialsObject credentialsOnRecord = CredentialsObject.fromHashedPassword(
            "4711",
            "device",
            "pwd",
            CredentialsConstants.HASH_FUNCTION_SHA256,
            null,
            null,
            null);

    private DeviceCredentials deviceCredentials = UsernamePasswordCredentials.create("device@DEFAULT_TENANT", "pwd", false);
    private UsernamePasswordAuthProvider provider;
    private HonoClient credentialsServiceClient;
    private CredentialsClient credentialsClient;

    /**
     * Time out all test after 2 seconds.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(2, TimeUnit.SECONDS);

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        credentialsClient = mock(CredentialsClient.class);
        when(credentialsClient.get(anyString(), anyString())).thenReturn(Future.succeededFuture(credentialsOnRecord));
        credentialsServiceClient = mock(HonoClient.class);
        when(credentialsServiceClient.getOrCreateCredentialsClient(anyString())).thenReturn(Future.succeededFuture(credentialsClient));

        provider = new UsernamePasswordAuthProvider(credentialsServiceClient, new ServiceConfigProperties());
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
     * Verifies that the provider fails to validate credentials when not
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
     * Verifies that the provider fails to validate credentials when not
     * running on a vert.x Context.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsForWrongCredentials(final TestContext ctx) {

        deviceCredentials = UsernamePasswordCredentials.create("device@DEFAULT_TENANT", "wrong_pwd", false);
        vertx.runOnContext(go -> {
            provider.authenticate(deviceCredentials, ctx.asyncAssertFailure(e -> {
                final ClientErrorException error = (ClientErrorException) e;
                ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, error.getErrorCode());
            }));
        });
    }

}
