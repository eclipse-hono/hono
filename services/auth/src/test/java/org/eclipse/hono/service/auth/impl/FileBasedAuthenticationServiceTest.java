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

package org.eclipse.hono.service.auth.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;

import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.service.auth.AuthTokenHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.io.ClassPathResource;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Test cases verifying behavior of {@link FileBasedAuthenticationService}.
 *
 */
@ExtendWith(VertxExtension.class)
public class FileBasedAuthenticationServiceTest {

    private static final String TOKEN = "not-a-real-token";
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(5);
    private static FileBasedAuthenticationService authService;

    /**
     * Loads permissions from file.
     * 
     * @throws IOException if the permissions cannot be loaded.
     */
    @BeforeAll
    public static void loadPermissions() throws IOException {

        final AuthTokenHelper tokenFactory = mock(AuthTokenHelper.class);
        when(tokenFactory.createToken(anyString(), any(Authorities.class))).thenReturn(TOKEN);
        when(tokenFactory.getTokenLifetime()).thenReturn(TOKEN_LIFETIME);

        final AuthenticationServerConfigProperties props = new AuthenticationServerConfigProperties();
        props.setPermissionsPath(new ClassPathResource("authentication-service-test-permissions.json"));

        authService = new FileBasedAuthenticationService();
        authService.setConfig(props);
        authService.setTokenFactory(tokenFactory);
        authService.loadPermissions();
    }

    /**
     * Verifies that the service fails verification of plain credentials if no
     * username is given.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyPlainFailsForMissingUsername(final VertxTestContext ctx) {

        authService.verifyPlain(null, null, "pwd", ctx.failing(t -> ctx.completeNow()));
    }

    /**
     * Verifies that the service fails verification of plain credentials if no
     * password is given.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyPlainFailsForMissingPassword(final VertxTestContext ctx) {

        authService.verifyPlain(null, "user", null, ctx.failing(t -> ctx.completeNow()));
    }

    /**
     * Verifies that the service successfully issues a token on successful verification
     * of credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyPlainSucceedsForMatchingPassword(final VertxTestContext ctx) {

        authService.verifyPlain(null, "hono-client@HONO", "secret", ctx.succeeding(res -> {
            assertUserAndToken(ctx, res, "hono-client@HONO", TOKEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service grants the requested <em>authorization identity</em> 
     * on successful verification of credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyPlainGrantsAuthorizationId(final VertxTestContext ctx) {

        authService.verifyPlain("userB", "http-adapter@HONO", "secret", ctx.succeeding(res -> {
            assertUserAndToken(ctx, res, "userB", TOKEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service refuses to grant the requested <em>authorization identity</em>
     * on successful verification of credentials if the user is not authorized to assume another
     * identity than the authentication identity.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyPlainRefusesAuthorizationId(final VertxTestContext ctx) {

        authService.verifyPlain("userB", "hono-client@HONO", "secret", ctx.succeeding(res -> {
            assertUserAndToken(ctx, res, "hono-client@HONO", TOKEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service refuses to grant an non-existing <em>authorization identity</em>
     * on successful verification of credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyPlainRefusesNonExistingAuthorizationId(final VertxTestContext ctx) {

        authService.verifyPlain("userC", "http-adapter@HONO", "secret", ctx.succeeding(res -> {
            assertUserAndToken(ctx, res, "http-adapter@HONO", TOKEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service fails verification of external credentials if no
     * subject DN is given.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyExternalFailsForMissingSubjectDn(final VertxTestContext ctx) {

        authService.verifyExternal(null, null, ctx.failing(t -> ctx.completeNow()));
    }

    /**
     * Verifies that the service successfully issues a token on successful verification
     * of external credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyExternalGrantsCommonName(final VertxTestContext ctx) {

        authService.verifyExternal(null, "CN=userB", ctx.succeeding(res -> {
            assertUserAndToken(ctx, res, "userB", TOKEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service grants the requested <em>authorization identity</em> 
     * on successful verification of external credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyExternalGrantsAuthorizationId(final VertxTestContext ctx) {

        authService.verifyExternal("userB", "CN=mqtt-adapter", ctx.succeeding(res -> {
            assertUserAndToken(ctx, res, "userB", TOKEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service refuses to grant an non-existing <em>authorization identity</em>
     * on successful verification of external credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyExternalRefusesNonExistingAuthorizationId(final VertxTestContext ctx) {

        authService.verifyExternal("userC", "CN=mqtt-adapter", ctx.succeeding(res -> {
            assertUserAndToken(ctx, res, "mqtt-adapter", TOKEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service refuses to grant the requested <em>authorization identity</em>
     * on successful verification of external credentials if the user is not authorized to assume another
     * identity than the authentication identity.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyExternalRefusesAuthorizationId(final VertxTestContext ctx) {

        authService.verifyExternal("userB", "CN=userA", ctx.succeeding(res -> {
            assertUserAndToken(ctx, res, "userA", TOKEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the token issued by the service on successful verification
     * of credentials contains the user's authorities for executing operations.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyPlainAddsAuthoritiesForOperations(final VertxTestContext ctx) {

        final ResourceIdentifier registration = ResourceIdentifier.fromString("registration/tenant");
        authService.verifyPlain(null, "hono-client@HONO", "secret", ctx.succeeding(res -> {
            ctx.verify(() -> {
                assertThat(res.getAuthorities().isAuthorized(registration, "assert")).isTrue();
                assertThat(res.getAuthorities().isAuthorized(registration, "add")).isTrue();
                ctx.completeNow();
            });
        }));
    }

    private static void assertUserAndToken(
            final VertxTestContext ctx,
            final HonoUser user,
            final String expectedName,
            final String expectedToken) {
        ctx.verify(() -> {
            assertThat(user.getName()).isEqualTo(expectedName);
            assertThat(user.getToken()).isEqualTo(expectedToken);
        });

    }
}
