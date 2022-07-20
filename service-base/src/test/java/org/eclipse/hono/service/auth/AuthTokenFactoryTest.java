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

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.security.PublicKey;
import java.time.Instant;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.config.KeyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jws;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.auth.impl.jose.JWK;
import io.vertx.junit5.VertxExtension;


/**
 * Verifies behavior of {@link JjwtBasedAuthTokenFactory}.
 *
 */
@ExtendWith(VertxExtension.class)
public class AuthTokenFactoryTest {

    private SignatureSupportingConfigProperties factoryProps;
    private SignatureSupportingConfigProperties validatorProps;
    private AuthTokenFactory factory;
    private AuthTokenValidator validator;
    private PublicKey publicKey;

    /**
     * Sets up the fixture.
     *
     * @param vertx The Vert.x instance to run on.
     */
    @BeforeEach
    public void init(final Vertx vertx) {
        factoryProps = new SignatureSupportingConfigProperties();
        factoryProps.setKeyPath("target/certs/auth-server-key.pem");
        factoryProps.setCertPath("target/certs/auth-server-cert.pem");
        factoryProps.setTokenExpiration(60);
        factoryProps.setIssuer("my-issuer");
        factoryProps.setAudience("hono-components");

        validatorProps = new SignatureSupportingConfigProperties();
        validatorProps.setCertPath("target/certs/auth-server-cert.pem");
        validatorProps.setIssuer("my-issuer");
        validatorProps.setAudience("hono-components");

        publicKey = KeyLoader.fromFiles(vertx, null, "target/certs/auth-server-cert.pem").getPublicKey();
        factory = new JjwtBasedAuthTokenFactory(vertx, factoryProps);
        validator = new JjwtBasedAuthTokenValidator(vertx, validatorProps);
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
        assertThat(parsedToken.getBody().getSubject()).isEqualTo("userA");
    }

    /**
     * Verifies that the JWK set created by the factory contains the public key that it
     * has been configured to use.
     */
    @Test
    public void testGetValidatingKeysReturnsProperKey() {

        final var jwks = factory.getValidatingJwkSet();
        final JsonArray keys = jwks.getJsonArray("keys");
        assertThat(keys).hasSize(1);
        final var jwk = new JWK(keys.getJsonObject(0));
        assertThat(jwk.publicKey()).isEqualTo(publicKey);
    }

    /**
     * Verifies that the validator fails to decode a token that has an unexpected <em>iss</em> claim value.
     *
     * @param vertx The Vert.x instance to run on.
     */
    @Test
    public void testValidationFailsForUnexpectedIssuer(final Vertx vertx) {
        factoryProps.setIssuer("wrong-issuer");
        factory = new JjwtBasedAuthTokenFactory(vertx, factoryProps);
        final String token = factory.createToken("userA", new AuthoritiesImpl());

        assertThrows(IncorrectClaimException.class, () -> validator.expand(token));
    }
}
