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

package org.eclipse.hono.authentication.impl;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.eclipse.hono.service.auth.AuthTokenHelperImpl;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Test cases verifying behavior of {@code AcceptAllPlainAuthenticationService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class AcceptAllAuthenticationServiceTest {

    private static final String SECRET = "shfjksgdhfuiasgihgfahgjkhsfdghgsfdkghsfdghsfdgisfdhguisdhg";
    private static AcceptAllAuthenticationService authService;

    @BeforeClass
    public static void setUp() {
        authService = new AcceptAllAuthenticationService();
        authService.setTokenFactory(AuthTokenHelperImpl.forSharedSecret(SECRET, 5));
    }

    @Test
    public void testVerifyPlainSucceedsForAnyPassword(final TestContext ctx) {

        authService.verifyPlain(null, "userA", "pwd", ctx.asyncAssertSuccess(user -> {
            assertThat(user.getName(), is("userA"));
        })); 
    }

    @Test
    public void testVerifyPlainGrantsAuthorizationId(final TestContext ctx) {

        authService.verifyPlain("userB", "userA", "pwd", ctx.asyncAssertSuccess(user -> {
            assertThat(user.getName(), is("userB"));
        })); 
    }

    @Test
    public void testAuthenticatedUserContainsToken(final TestContext ctx) {

        authService.verifyPlain("userB", "userA", "pwd", ctx.asyncAssertSuccess(user -> {
            assertThat(user.getName(), is("userB"));
            assertNotNull(user.getToken());
        })); 
    }
}
