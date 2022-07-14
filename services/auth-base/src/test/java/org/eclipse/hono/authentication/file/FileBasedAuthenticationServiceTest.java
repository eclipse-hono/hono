/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.authentication.file;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.service.auth.AuthTokenFactory;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Test cases verifying behavior of {@link FileBasedAuthenticationService}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class FileBasedAuthenticationServiceTest {

    private static final String TOKEN = "not-a-real-token";
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(5);
    private FileBasedAuthenticationService authService;
    private FileBasedAuthenticationServiceConfigProperties props;

    @BeforeEach
    void createInstance(final Vertx vertx) {
        this.props = new FileBasedAuthenticationServiceConfigProperties();
        this.authService = getService("target/test-classes/authentication-service-test-permissions.json");
        authService.init(vertx, vertx.getOrCreateContext());
    }

    private FileBasedAuthenticationService getService(final String permissionsPath) {

        final AuthTokenFactory tokenFactory = mock(AuthTokenFactory.class);
        when(tokenFactory.createToken(anyString(), any(Authorities.class))).thenReturn(TOKEN);
        when(tokenFactory.getTokenLifetime()).thenReturn(TOKEN_LIFETIME);

        props.setPermissionsPath(permissionsPath);

        final var service = new FileBasedAuthenticationService();
        service.setConfig(props);
        service.setTokenFactory(tokenFactory);
        return service;
    }

    private Future<Void> givenAStartedService() {
        final Promise<Void> startup = Promise.promise();
        authService.doStart(startup);
        return startup.future();
    }

    /**
     * Verifies that the service loads permissions from a file specified using URI file syntax.
     *
     * @param vertx The vert.x instance to use.
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDoStartLoadsPermissionsUsingUriSyntax(final Vertx vertx, final VertxTestContext ctx) {

        props.setPermissionsPath("file://target/test-classes/authentication-service-test-permissions.json");
        authService.setConfig(props);

        givenAStartedService().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the service fails to start if the permissions path does not exist.
     *
     * @param vertx The vert.x instance to use.
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDoStartFailsForNonExistingPermissionsPath(final Vertx vertx, final VertxTestContext ctx) {

        props.setPermissionsPath("/no/such/path");
        authService.setConfig(props);

        givenAStartedService().onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(t).isInstanceOf(FileNotFoundException.class));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service fails verification of plain credentials if no
     * username is given.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyPlainFailsForMissingUsername(final VertxTestContext ctx) {

        givenAStartedService()
            .compose(ok -> authService.verifyPlain(null, null, "pwd"))
            .onComplete(ctx.failing(t -> ctx.completeNow()));
        ;
    }

    /**
     * Verifies that the service fails verification of plain credentials if no
     * password is given.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyPlainFailsForMissingPassword(final VertxTestContext ctx) {

        givenAStartedService()
            .compose(ok -> authService.verifyPlain(null, "user", null))
            .onComplete(ctx.failing(t -> ctx.completeNow()));
    }

    /**
     * Verifies that the service successfully issues a token on successful verification
     * of credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyPlainSucceedsForMatchingPassword(final VertxTestContext ctx) {

        givenAStartedService()
            .compose(ok -> authService.verifyPlain(null, "hono-client@HONO", "secret"))
            .onComplete(ctx.succeeding(res -> {
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

        givenAStartedService()
            .compose(ok -> authService.verifyPlain("userB", "http-adapter@HONO", "secret"))
            .onComplete(ctx.succeeding(res -> {
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

        givenAStartedService()
            .compose(ok -> authService.verifyPlain("userB", "hono-client@HONO", "secret"))
            .onComplete(ctx.succeeding(res -> {
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

        givenAStartedService()
            .compose(ok -> authService.verifyPlain("userC", "http-adapter@HONO", "secret"))
            .onComplete(ctx.succeeding(res -> {
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

        givenAStartedService()
            .compose(ok -> authService.verifyExternal(null, null))
            .onComplete(ctx.failing(t -> ctx.completeNow()));
    }

    /**
     * Verifies that the service successfully issues a token on successful verification
     * of external credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testVerifyExternalGrantsCommonName(final VertxTestContext ctx) {

        givenAStartedService()
            .compose(ok -> authService.verifyExternal(null, "CN=userB"))
            .onComplete(ctx.succeeding(res -> {
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

        givenAStartedService()
            .compose(ok -> authService.verifyExternal("userB", "CN=mqtt-adapter"))
            .onComplete(ctx.succeeding(res -> {
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

        givenAStartedService()
            .compose(ok -> authService.verifyExternal("userC", "CN=mqtt-adapter"))
            .onComplete(ctx.succeeding(res -> {
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

        givenAStartedService()
            .compose(ok -> authService.verifyExternal("userB", "CN=userA"))
            .onComplete(ctx.succeeding(res -> {
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
        givenAStartedService()
            .compose(ok -> authService.verifyPlain(null, "hono-client@HONO", "secret"))
            .onComplete(ctx.succeeding(res -> {
                ctx.verify(() -> {
                    assertThat(res.getAuthorities().isAuthorized(registration, "assert")).isTrue();
                    assertThat(res.getAuthorities().isAuthorized(registration, "add")).isTrue();
                });
                ctx.completeNow();
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
