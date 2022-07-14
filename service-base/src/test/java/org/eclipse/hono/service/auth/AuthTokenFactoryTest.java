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

package org.eclipse.hono.service.auth;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;


/**
 * Verifies behavior of {@link JjwtBasedAuthTokenFactory}.
 *
 */
@ExtendWith(VertxExtension.class)
public class AuthTokenFactoryTest {

    private AuthTokenFactory factory;
    private AuthTokenValidator validator;

    /**
     * Sets up the fixture.
     *
     * @param vertx The Vert.x instance to run on.
     */
    @BeforeEach
    public void init(final Vertx vertx) {
        final var props = new SignatureSupportingConfigProperties();
        props.setKeyPath("target/certs/auth-server-key.pem");
        props.setCertPath("target/certs/auth-server-cert.pem");
        props.setTokenExpiration(60);
        factory = new JjwtBasedAuthTokenFactory(vertx, props);
        validator = new JjwtBasedAuthTokenValidator(vertx, props);
    }

    /**
     * Verifies that the token created by the factory can be parsed again using the factory's validating key.
     */
    @Test
    public void testCreateAndExpandToken() {

        final Authorities authorities = new AuthoritiesImpl()
                .addResource("telemetry", "*", Activity.READ, Activity.WRITE)
                .addOperation("registration", "*", "assert");
        final Instant expirationMin = Instant.now().plusSeconds(59);
        final Instant expirationMax = expirationMin.plusSeconds(2);
        final String token = factory.createToken("userA", authorities);

        final Jws<Claims> parsedToken = validator.expand(token);
        assertThat(parsedToken.getBody()).isNotNull();
        assertThat(parsedToken.getBody().getExpiration().toInstant()).isAtLeast(expirationMin);
        assertThat(parsedToken.getBody().getExpiration().toInstant()).isAtMost(expirationMax);
    }
}
