/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    @BeforeEach
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
        final Instant expirationMin = Instant.now().plusSeconds(59);
        final Instant expirationMax = expirationMin.plusSeconds(2);
        final String token = helper.createToken("userA", authorities);

        final Jws<Claims> parsedToken = helper.expand(token);
        assertThat(parsedToken.getBody()).isNotNull();
        assertThat(parsedToken.getBody().getExpiration().toInstant()).isBetween(expirationMin, expirationMax);
    }
}
