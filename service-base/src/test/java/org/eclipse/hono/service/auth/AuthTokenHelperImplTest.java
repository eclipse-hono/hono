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
