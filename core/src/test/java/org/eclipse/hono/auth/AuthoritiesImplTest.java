/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.auth;

import static org.junit.Assert.*;

import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Test;

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

        Claims claims = Jwts.claims();
        claims.put("r:telemetry/*", "W");
        claims.put("r:registration/DEFAULT_TENANT", "RW");
        claims.put("o:credentials/*:get", "E");
        Authorities auth = AuthoritiesImpl.from(claims);
        assertTrue(auth.isAuthorized(ResourceIdentifier.fromString("telemetry/tenantA"), Activity.WRITE));
        assertTrue(auth.isAuthorized(ResourceIdentifier.fromString("registration/DEFAULT_TENANT"), Activity.READ));
        assertFalse(auth.isAuthorized(ResourceIdentifier.fromString("registration/tenantA"), Activity.READ));
        assertTrue(auth.isAuthorized(ResourceIdentifier.fromString("credentials/DEFAULT_TENANT"), "get"));
        assertFalse(auth.isAuthorized(ResourceIdentifier.fromString("credentials/DEFAULT_TENANT"), "add"));
    }

    /**
     * Verifies that wildcard character matches any operation.
     */
    @Test
    public void testIsAuthorizedConsidersWildCards() {

        AuthoritiesImpl authorities = new AuthoritiesImpl()
                .addOperation("endpoint", "*", "*");
        assertFalse(authorities.isAuthorized(ResourceIdentifier.fromString("other-endpoint/tenant"), "get"));
        assertTrue(authorities.isAuthorized(ResourceIdentifier.fromString("endpoint/tenant"), "get"));
    }
}
