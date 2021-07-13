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

package org.eclipse.hono.auth;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;


/**
 * Verifies behavior of {@link AuthoritiesImpl}.
 *
 */
public class AuthoritiesImplTest {

    /**
     * Verifies that authorities are created correctly from claims found in a JWT.
     */
    @Test
    public void testFromClaims() {

        final Claims claims = Jwts.claims();
        claims.put("r:telemetry/*", "W");
        claims.put("r:registration/DEFAULT_TENANT", "RW");
        claims.put("o:credentials/*:get", "E");
        final Authorities auth = AuthoritiesImpl.from(claims);
        assertThat(auth.isAuthorized(ResourceIdentifier.fromString("telemetry/tenantA"), Activity.WRITE)).isTrue();
        assertThat(auth.isAuthorized(ResourceIdentifier.fromString("registration/DEFAULT_TENANT"), Activity.READ)).isTrue();
        assertThat(auth.isAuthorized(ResourceIdentifier.fromString("registration/tenantA"), Activity.READ)).isFalse();
        assertThat(auth.isAuthorized(ResourceIdentifier.fromString("credentials/DEFAULT_TENANT"), "get")).isTrue();
        assertThat(auth.isAuthorized(ResourceIdentifier.fromString("credentials/DEFAULT_TENANT"), "add")).isFalse();
    }

    /**
     * Verifies that wildcard character matches any operation.
     */
    @Test
    public void testIsAuthorizedConsidersWildCards() {

        final AuthoritiesImpl authorities = new AuthoritiesImpl()
                .addOperation("endpoint", "*", "*");
        assertThat(authorities.isAuthorized(ResourceIdentifier.fromString("other-endpoint/tenant"), "get")).isFalse();
        assertThat(authorities.isAuthorized(ResourceIdentifier.fromString("endpoint/tenant"), "get")).isTrue();
    }
}
