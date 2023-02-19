/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.auth.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link CredentialsApiAuthProvider}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class CredentialsApiAuthProviderTest {

    private CredentialsApiAuthProvider<AbstractDeviceCredentials> provider;
    private CredentialsClient credentialsClient;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        credentialsClient = mock(CredentialsClient.class);
        provider = getProvider(getDeviceCredentials("type", "TENANT", "user"), NoopTracerFactory.create());
    }

    /**
     * Verifies that the auth provider propagates the exception reported by a failed invocation
     * of the credentials service.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsWithExceptionReportedByCredentialsClient(final VertxTestContext ctx) {

        final ServerErrorException reportedException = new ServerErrorException(503);
        when(credentialsClient.get(anyString(), anyString(), anyString(), any(JsonObject.class), any())).thenReturn(Future.failedFuture(reportedException));
        provider.authenticate(new JsonObject(), ctx.failing(t -> {
            ctx.verify(() -> assertThat(t).isEqualTo(reportedException));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the auth provider fails an authentication request with a 401
     * {@code ClientErrorException} if the credentials cannot be parsed.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsWith401ForMalformedCredentials(final VertxTestContext ctx) {

        // WHEN trying to authenticate using malformed credentials
        // that do not contain a tenant
        provider = getProvider(null, NoopTracerFactory.create());
        provider.authenticate(new JsonObject(), ctx.failing(t -> {
            // THEN authentication fails with a 401 client error
            ctx.verify(() -> assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED));
            ctx.completeNow();
        }));

    }

    /**
     * Verifies that the auth provider fails an authentication request with a 401
     * {@code ClientErrorException} if the auth-id is unknown.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsWith401ForNonExistingAuthId(final VertxTestContext ctx) {

        // WHEN trying to authenticate using an auth-id that is not known
        when(credentialsClient.get(anyString(), anyString(), eq("user"), any(JsonObject.class), any()))
            .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        provider.authenticate(new JsonObject(), ctx.failing(t -> {
            // THEN authentication fails with a 401 client error
            ctx.verify(() -> assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that credentials validation fails if the credentials on record are disabled.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testValidateFailsIfCredentialsAreDisabled(final VertxTestContext ctx) {

        // WHEN trying to authenticate a disabled device
        final AbstractDeviceCredentials creds = getDeviceCredentials("type", "tenant", "identity");
        final CredentialsObject credentialsOnRecord = getCredentialsObject("type", "identity", "device", false)
                .addSecret(CredentialsObject.emptySecret(Instant.now().minusSeconds(120), null));
        when(credentialsClient.get(eq("tenant"), eq("type"), eq("identity"), any(JsonObject.class), any()))
            .thenReturn(Future.succeededFuture(credentialsOnRecord));
        provider.authenticate(creds, null, ctx.failing(t -> {
            // THEN authentication fails with a 401 client error
            ctx.verify(() -> assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED));
            ctx.completeNow();
        }));
    }

    private CredentialsApiAuthProvider<AbstractDeviceCredentials> getProvider(final AbstractDeviceCredentials credentials, final Tracer tracer) {

        return new CredentialsApiAuthProvider<>(credentialsClient, tracer) {

            @Override
            public AbstractDeviceCredentials getCredentials(final JsonObject authInfo) {
                return credentials;
            }

            @Override
            protected Future<DeviceUser> doValidateCredentials(
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
