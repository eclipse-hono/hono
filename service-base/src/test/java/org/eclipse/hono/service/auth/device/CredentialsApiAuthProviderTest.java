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

package org.eclipse.hono.service.auth.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
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

    private CredentialsApiAuthProvider<AbstractDeviceCredentials> provider;
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
        when(credentialsClient.isOpen()).thenReturn(Boolean.TRUE);
        honoClient = mock(HonoClient.class);
        when(honoClient.getOrCreateCredentialsClient(anyString())).thenReturn(Future.succeededFuture(credentialsClient));
        provider = getProvider(getDeviceCredentials("type", "TENANT", "user"), NoopTracerFactory.create());
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
        when(credentialsClient.get(anyString(), anyString(), any(JsonObject.class), any())).thenReturn(Future.failedFuture(reportedException));
        provider.authenticate(new JsonObject(), ctx.asyncAssertFailure(t -> {
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
        provider = getProvider(null, NoopTracerFactory.create());
        provider.authenticate(new JsonObject(), ctx.asyncAssertFailure(t -> {
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
        when(credentialsClient.get(anyString(), eq("user"), any(JsonObject.class), any())).thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        provider.authenticate(new JsonObject(), ctx.asyncAssertFailure(t -> {
            // THEN authentication fails with a 401 client error
            ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ClientErrorException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that credentials validation fails if the credentials on record are disabled.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testValidateFailsIfCredentialsAreDisabled(final TestContext ctx) {

        // WHEN trying to authenticate a disabled device
        final AbstractDeviceCredentials creds = getDeviceCredentials("type", "tenant", "identity");
        final CredentialsObject credentialsOnRecord = getCredentialsObject("type", "identity", "device", false)
                .addSecret(CredentialsObject.emptySecret(Instant.now().minusSeconds(120), null));
        when(credentialsClient.get(eq("type"), eq("identity"), any(JsonObject.class), any())).thenReturn(Future.succeededFuture(credentialsOnRecord));
        provider.authenticate(creds, null, ctx.asyncAssertFailure(t -> {
            // THEN authentication fails with a 401 client error
            ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ClientErrorException) t).getErrorCode());
        }));
    }

    private CredentialsApiAuthProvider<AbstractDeviceCredentials> getProvider(final AbstractDeviceCredentials credentials, final Tracer tracer) {

        return new CredentialsApiAuthProvider<AbstractDeviceCredentials>(honoClient, tracer) {

            @Override
            protected AbstractDeviceCredentials getCredentials(final JsonObject authInfo) {
                return credentials;
            }

            @Override
            protected Future<Device> doValidateCredentials(
                    final AbstractDeviceCredentials deviceCredentials,
                    final CredentialsObject credentialsOnRecord) {
                return Future.succeededFuture();
            }
        };
    }

    private static AbstractDeviceCredentials getDeviceCredentials(final String type, final String tenantId, final String authId) {

        return new AbstractDeviceCredentials(tenantId, authId) {

            @Override
            public String getType() {
                return type;
            }
        };
    }

    private static CredentialsObject getCredentialsObject(final String type, final String authId, final String deviceId, final boolean enabled) {

        return new CredentialsObject()
                .setAuthId(authId)
                .setDeviceId(deviceId)
                .setType(type)
                .setEnabled(enabled);
    }

}
