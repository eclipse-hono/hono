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
package org.eclipse.hono.util;

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
 * Verifies behavior of {@link JwtHelper}.
 *
 */
public class JwtHelperTest {

    byte[] secret = "usadfigdfkbsakgjhfuigagasfsdafgsgdfzugzufrwebf".getBytes(StandardCharsets.UTF_8);

    /**
     * Verifies that an expired token is detected.
     */
    @Test
    public void testIsExpired() {

        final String token = Jwts.builder()
                            .signWith(SignatureAlgorithm.HS256, secret)
                            .setExpiration(Date.from(Instant.now().minus(Duration.ofSeconds(10))))
                            .compact();

        assertTrue(JwtHelper.isExpired(token, 10));
        assertFalse(JwtHelper.isExpired(token, 15));
    }
}
