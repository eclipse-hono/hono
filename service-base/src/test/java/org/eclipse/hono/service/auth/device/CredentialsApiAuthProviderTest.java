/**
 * Copyright (c) 2017, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.auth.device;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.ServerErrorException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link CredentialsApiAuthProvider}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CredentialsApiAuthProviderTest {

    private CredentialsApiAuthProvider provider;
    private HonoClient honoClient;
    private CredentialsClient credentialsClient;

    /**
     * Time out all test after 2 secs.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(2, TimeUnit.SECONDS);

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        credentialsClient = mock(CredentialsClient.class);
        honoClient = mock(HonoClient.class);
        when(honoClient.getOrCreateCredentialsClient(anyString())).thenReturn(Future.succeededFuture(credentialsClient));

        provider = new CredentialsApiAuthProvider(honoClient) {

            @Override
            protected DeviceCredentials getCredentials(final JsonObject authInfo) {
                return null;
            }
        };
    }

    /**
     * Verifies that the auth provider propagates the exception reported by a failed invocation
     * of the credentials service.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsWithExceptionReportedByCredentialsClient(final TestContext ctx) {

        final ServerErrorException reportedException = new ServerErrorException(503);
        when(credentialsClient.isOpen()).thenReturn(Boolean.TRUE);
        when(credentialsClient.get(anyString(), anyString())).thenReturn(Future.failedFuture(reportedException));
        provider.authenticate(UsernamePasswordCredentials.create("user@TENANT", "pwd", false), ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(t, reportedException);
        }));
    }

    /**
     * Verifies that the auth provider fails an authentication request with a 401
     * {@code ClientErrorException} if the credentials cannot be parsed.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsWith401ForMalformedCredentials(final TestContext ctx) {

        // WHEN trying to authenticate using malformed credentials
        // that do not contain a tenant
        final JsonObject authInfo = new JsonObject().put("username", "no-tenant").put("password", "secret");
        provider.authenticate(authInfo, ctx.asyncAssertFailure(t -> {
            // THEN authentication fails with a 401 client error
            ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ClientErrorException) t).getErrorCode());
        }));

    }

    /**
     * Verifies that the auth provider fails an authentication request with a 401
     * {@code ClientErrorException} if the auth-id is unknown.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsWith401ForNonExistingAuthId(final TestContext ctx) {

        // WHEN trying to authenticate using an auth-id that is not known
        final JsonObject authInfo = new JsonObject().put("username", "unknown@TENANT").put("password", "secret");
        when(credentialsClient.isOpen()).thenReturn(Boolean.TRUE);
        when(credentialsClient.get(anyString(), eq("unknown"))).thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        provider.authenticate(authInfo, ctx.asyncAssertFailure(t -> {
            // THEN authentication fails with a 401 client error
            ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ClientErrorException) t).getErrorCode());
        }));
    }

}
