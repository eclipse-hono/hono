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
package org.eclipse.hono.service.registration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.junit.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Verifies behavior of {@link RegistrationAssertionHelper}.
 *
 */
public class RegistrationAssertionHelperTest {

    byte[] secret = "secret".getBytes(StandardCharsets.UTF_8);

    @Test
    public void testIsExpired() {

        String token = Jwts.builder()
                            .signWith(SignatureAlgorithm.HS256, secret)
                            .setExpiration(Date.from(Instant.now().minus(Duration.ofSeconds(10))))
                            .compact();

        assertTrue(RegistrationAssertionHelper.isExpired(token, 10));
        assertFalse(RegistrationAssertionHelper.isExpired(token, 15));
    }

}
