/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.auth;

import static org.junit.Assert.assertNotNull;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.junit.Before;
import org.junit.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;


/**
 * Verifies behavior of {@link AuthTokenHelperImpl}.
 *
 */
public class AuthTokenHelperImplTest {

    private AuthTokenHelper helper;

    /**
     * Sets up the fixture.
     */
    @Before
    public void init() {
        helper = AuthTokenHelperImpl.forSharedSecret("suzfgsuzdfgadsjfjfaksgfkadfgduzsafdsfsaf", 60);
    }

    /**
     * Verifies that the helper can create a token for a given set of
     * authorities and can then parse the token again.
     */
    @Test
    public void testCreateAndExpandToken() {

        final Authorities authorities = new AuthoritiesImpl()
                .addResource("telemetry", "*", Activity.READ, Activity.WRITE)
                .addOperation("registration", "*", "assert");
        final String token = helper.createToken("userA", authorities);

        final Jws<Claims> parsedToken = helper.expand(token);
        assertNotNull(parsedToken.getBody());
    }
}
