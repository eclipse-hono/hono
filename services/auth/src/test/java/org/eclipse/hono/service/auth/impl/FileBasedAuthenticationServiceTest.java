/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.auth.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;

import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.service.auth.AuthTokenHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ClassPathResource;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Test cases verifying behavior of {@link FileBasedAuthenticationService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class FileBasedAuthenticationServiceTest {

    private static final String TOKEN = "not-a-real-token";
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(5);
    private static FileBasedAuthenticationService authService;

    /**
     * Loads permissions from file.
     * 
     * @throws IOException if the permissions cannot be loaded.
     */
    @BeforeClass
    public static void loadPermissions() throws IOException {

        AuthTokenHelper tokenFactory = mock(AuthTokenHelper.class);
        when(tokenFactory.createToken(anyString(), any(Authorities.class))).thenReturn(TOKEN);
        when(tokenFactory.getTokenLifetime()).thenReturn(TOKEN_LIFETIME);

        AuthenticationServerConfigProperties props = new AuthenticationServerConfigProperties();
        props.setPermissionsPath(new ClassPathResource("authentication-service-test-permissions.json"));

        authService = new FileBasedAuthenticationService();
        authService.setConfig(props);
        authService.setTokenFactory(tokenFactory);
        authService.loadPermissions();
    }

    @Test
    public void testVerifyPlainFailsForMissingUsername(final TestContext ctx) {

        authService.verifyPlain(null, null, "pwd", ctx.asyncAssertFailure());
    }

    @Test
    public void testVerifyPlainFailsForMissingPassword(final TestContext ctx) {

        authService.verifyPlain(null, "user", null, ctx.asyncAssertFailure());
    }

    @Test
    public void testVerifyPlainSucceedsForMatchingPassword(final TestContext ctx) {

        authService.verifyPlain(null, "hono-client@HONO", "secret", ctx.asyncAssertSuccess(res -> {
            assertThat(res.getName(), is("hono-client@HONO"));
            assertThat(res.getToken(), is(TOKEN));
        }));
    }

    @Test
    public void testVerifyPlainGrantsAuthorizationId(final TestContext ctx) {

        authService.verifyPlain("userB", "http-adapter@HONO", "secret", ctx.asyncAssertSuccess(res -> {
            assertThat(res.getName(), is("userB"));
            assertThat(res.getToken(), is(TOKEN));
        }));
    }

    @Test
    public void testVerifyPlainRefusesAuthorizationId(final TestContext ctx) {

        authService.verifyPlain("userB", "hono-client@HONO", "secret", ctx.asyncAssertSuccess(res -> {
            assertThat(res.getName(), is("hono-client@HONO"));
            assertThat(res.getToken(), is(TOKEN));
        }));
    }

    @Test
    public void testVerifyPlainRefusesNonExistingAuthorizationId(final TestContext ctx) {

        authService.verifyPlain("userC", "http-adapter@HONO", "secret", ctx.asyncAssertSuccess(res -> {
            assertThat(res.getName(), is("http-adapter@HONO"));
            assertThat(res.getToken(), is(TOKEN));
        }));
    }

    @Test
    public void testVerifyExternalFailsForMissingSubjectDn(final TestContext ctx) {

        authService.verifyExternal(null, null, ctx.asyncAssertFailure());
    }

    @Test
    public void testVerifyExternalGrantsCommonName(final TestContext ctx) {

        authService.verifyExternal(null, "CN=userB", ctx.asyncAssertSuccess(res -> {
            assertThat(res.getName(), is("userB"));
            assertThat(res.getToken(), is(TOKEN));
        }));
    }

    @Test
    public void testVerifyExternalGrantsAuthorizationId(final TestContext ctx) {

        authService.verifyExternal("userB", "CN=mqtt-adapter", ctx.asyncAssertSuccess(res -> {
            assertThat(res.getName(), is("userB"));
            assertThat(res.getToken(), is(TOKEN));
        }));
    }

    @Test
    public void testVerifyExternalRefusesNonExistingAuthorizationId(final TestContext ctx) {

        authService.verifyExternal("userC", "CN=mqtt-adapter", ctx.asyncAssertSuccess(res -> {
            assertThat(res.getName(), is("mqtt-adapter"));
            assertThat(res.getToken(), is(TOKEN));
        }));
    }

    @Test
    public void testVerifyExternalRefusesAuthorizationId(final TestContext ctx) {

        authService.verifyExternal("userB", "CN=userA", ctx.asyncAssertSuccess(res -> {
            assertThat(res.getName(), is("userA"));
            assertThat(res.getToken(), is(TOKEN));
        }));
    }

    @Test
    public void testVerifyPlainAddsAuthoritiesForOperations(final TestContext ctx) {

        final ResourceIdentifier registration = ResourceIdentifier.fromString("registration/tenant");
        authService.verifyPlain(null, "hono-client@HONO", "secret", ctx.asyncAssertSuccess(res -> {
            assertTrue(res.getAuthorities().isAuthorized(registration, "assert"));
            assertTrue(res.getAuthorities().isAuthorized(registration, "add"));
        }));
    }
}
