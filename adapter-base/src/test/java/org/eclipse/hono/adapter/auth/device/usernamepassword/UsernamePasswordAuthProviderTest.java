/**
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.auth.device.usernamepassword;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.auth.HonoPasswordEncoder;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link UsernamePasswordAuthProviderTest}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class UsernamePasswordAuthProviderTest {

    private static final String PWD = "the-secret";
    private static Vertx vertx;

    private UsernamePasswordCredentials deviceCredentials = UsernamePasswordCredentials.create("device@DEFAULT_TENANT", "the-secret");
    private UsernamePasswordAuthProvider provider;
    private CredentialsClient credentialsClient;
    private HonoPasswordEncoder pwdEncoder;

    /**
     * Initializes vert.x.
     */
    @BeforeAll
    public static void init() {
        vertx = Vertx.vertx();
    }

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        credentialsClient = mock(CredentialsClient.class);
        pwdEncoder = mock(HonoPasswordEncoder.class);
        when(pwdEncoder.matches(eq("the-secret"), any(JsonObject.class))).thenReturn(true);

        provider = new UsernamePasswordAuthProvider(credentialsClient, pwdEncoder, NoopTracerFactory.create());
        givenCredentialsOnRecord(CredentialsObject.fromClearTextPassword("4711", "device", "the-secret", null, null));

    }

    /**
     * Verifies that the provider fails to authenticate a device when not
     * running on a vert.x Context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateRequiresVertxContext(final VertxTestContext ctx) {

        provider.authenticate(deviceCredentials, null, ctx.failing(e -> {
            ctx.verify(() -> assertThat(e).isInstanceOf(IllegalStateException.class));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the provider succeeds to validate matching credentials when
     * running on a vert.x Context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateSucceedsWhenRunningOnVertxContext(final VertxTestContext ctx) {

        final Promise<DeviceUser> result = Promise.promise();
        vertx.runOnContext(go -> {
            provider.authenticate(deviceCredentials, null, result);
        });
        result.future().onComplete(ctx.succeeding(device -> {
            ctx.verify(() -> {
                assertThat(device.getDeviceId()).isEqualTo("4711");
                assertThat(device.getTenantId()).isEqualTo("DEFAULT_TENANT");
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the provider fails to validate wrong credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsForWrongCredentials(final VertxTestContext ctx) {

        when(pwdEncoder.matches(eq("wrong_pwd"), any(JsonObject.class))).thenReturn(false);
        final Promise<DeviceUser> result = Promise.promise();

        deviceCredentials = UsernamePasswordCredentials.create("device@DEFAULT_TENANT", "wrong_pwd");
        vertx.runOnContext(go -> {
            provider.authenticate(deviceCredentials, null, result);
        });
        result.future().onComplete(ctx.failing(e -> {
            ctx.verify(() -> assertThat(((ClientErrorException) e).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that credentials validation fails if none of the secrets on record are
     * valid any more.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsIfNoSecretsAreValidAnymore(final VertxTestContext ctx) {

        givenCredentialsOnRecord(CredentialsObject.fromClearTextPassword("4711", "device", PWD, null, Instant.now().minusSeconds(120)));
        final Promise<DeviceUser> result = Promise.promise();
        vertx.runOnContext(go -> {
            provider.authenticate(deviceCredentials, null, result);
        });
        result.future().onComplete(ctx.failing(t -> {
            // THEN authentication fails with a 401 client error
            ctx.verify(() -> assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that credentials validation fails if none of the secrets on record are
     * valid yet.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailsIfNoSecretsAreValidYet(final VertxTestContext ctx) {

        givenCredentialsOnRecord(CredentialsObject.fromClearTextPassword("4711", "device", PWD, Instant.now().plusSeconds(120), null));
        final Promise<DeviceUser> result = Promise.promise();
        vertx.runOnContext(go -> {
            provider.authenticate(deviceCredentials, null, result);
        });
        result.future().onComplete(ctx.failing(t -> {
            // THEN authentication fails with a 401 client error
            ctx.verify(() -> assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED));
            ctx.completeNow();
        }));
    }

    private void givenCredentialsOnRecord(final CredentialsObject credentials) {
        when(credentialsClient.get(
                anyString(),
                eq(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD),
                eq("device"),
                any(),
                any())).thenReturn(Future.succeededFuture(credentials));
    }

    /**
     * Verifies that the provider succeeds retrieving credentials encoded in the username.
     *
     */
    @Test
    public void testGetCredentialsRetrievesCredentialsFromUsername() {

        final String usernameIncludingPassword = "device" + "@" + Constants.DEFAULT_TENANT + ":" + "the-secret";
        final String encodedUsername = Base64.getEncoder().encodeToString(usernameIncludingPassword.getBytes(StandardCharsets.UTF_8));

        final JsonObject originalCredentials = new JsonObject()
                .put(CredentialsConstants.FIELD_USERNAME, encodedUsername)
                .put(CredentialsConstants.FIELD_PASSWORD, "");
        final UsernamePasswordCredentials credentials = provider.getCredentials(originalCredentials);

        assertThat(credentials).isNotNull();
        assertThat(credentials.getAuthId()).isEqualTo("device");
        assertThat(credentials.getTenantId()).isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(credentials.getPassword()).isEqualTo("the-secret");
    }

    /**
     * Verifies that the provider handles a missing auth-id and tenant in Base64 encoding of credentials
     * encoded in the username.
     *
     */
    @Test
    public void testGetCredentialsHandlesEmptyAuthId() {

        final String usernameIncludingPassword = ":" + "the-secret";
        final String encodedUsername = Base64.getEncoder().encodeToString(usernameIncludingPassword.getBytes(StandardCharsets.UTF_8));

        final JsonObject originalCredentials = new JsonObject()
                .put(CredentialsConstants.FIELD_USERNAME, encodedUsername)
                .put(CredentialsConstants.FIELD_PASSWORD, "");
        final UsernamePasswordCredentials credentials = provider.getCredentials(originalCredentials);

        assertThat(credentials).isNull();
    }

    /**
     * Verifies that the provider handles a missing auth-id and tenant in Base64 encoding of credentials
     * encoded in the username.
     *
     */
    @Test
    public void testGetCredentialsHandlesEmptyPassword() {

        final String usernameIncludingPassword = "device" + "@" + Constants.DEFAULT_TENANT + ":";
        final String encodedUsername = Base64.getEncoder().encodeToString(usernameIncludingPassword.getBytes(StandardCharsets.UTF_8));

        final JsonObject originalCredentials = new JsonObject()
                .put(CredentialsConstants.FIELD_USERNAME, encodedUsername)
                .put(CredentialsConstants.FIELD_PASSWORD, "");
        final UsernamePasswordCredentials credentials = provider.getCredentials(originalCredentials);

        assertThat(credentials).isNotNull();
        assertThat(credentials.getAuthId()).isEqualTo("device");
        assertThat(credentials.getTenantId()).isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(credentials.getPassword()).isEqualTo("");
    }

    /**
     * Verifies that the provider handles malformed Base64 encoding of credentials encoded in the username.
     *
     */
    @Test
    public void testGetCredentialsHandlesMalformedBase64InUsername() {

        final String usernameIncludingPassword = "device" + "@" + Constants.DEFAULT_TENANT + ":" + "the-secret";
        final String encodedUsername = Base64.getEncoder().encodeToString(usernameIncludingPassword.getBytes(StandardCharsets.UTF_8)) + "not Base64";

        final JsonObject originalCredentials = new JsonObject()
                .put(CredentialsConstants.FIELD_USERNAME, encodedUsername)
                .put(CredentialsConstants.FIELD_PASSWORD, "");
        final UsernamePasswordCredentials credentials = provider.getCredentials(originalCredentials);

        assertThat(credentials).isNull();
    }

    /**
     * Verifies that the provider adopts properties from the authInfo JSON into the clientContext
     * of the credentials object.
     *
     */
    @Test
    public void testGetCredentialsSetsClientContext() {

        final String username = "device" + "@" + Constants.DEFAULT_TENANT;

        final JsonObject originalCredentials = new JsonObject()
                .put(CredentialsConstants.FIELD_USERNAME, username)
                .put(CredentialsConstants.FIELD_PASSWORD, "the-secret")
                .put("client-id", "the-client-id");
        final UsernamePasswordCredentials credentials = provider.getCredentials(originalCredentials);

        assertThat(credentials).isNotNull();
        assertThat(credentials.getAuthId()).isEqualTo("device");
        assertThat(credentials.getTenantId()).isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(credentials.getPassword()).isEqualTo("the-secret");

        assertThat(credentials.getClientContext().size()).isEqualTo(1);
        final Map.Entry<String, Object> firstClientContextEntry = credentials.getClientContext().stream().iterator().next();
        assertThat(firstClientContextEntry.getKey()).isEqualTo("client-id");
        assertThat(firstClientContextEntry.getValue()).isEqualTo("the-client-id");
    }
}
