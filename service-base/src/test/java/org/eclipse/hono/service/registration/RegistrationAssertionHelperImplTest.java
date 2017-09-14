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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.config.KeyLoader;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.junit.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.Vertx;


/**
 * Tests verifying behavior of {@link RegistrationAssertionHelperImpl}.
 *
 */
public class RegistrationAssertionHelperImplTest {

    private Vertx vertx = Vertx.vertx();

    /**
     * Verifies that the helper asserts a minimum length of 32 bytes for shared secrets.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testForSigningRejectsShortSecret() {

        String shortSecret = "01234567890123456"; // not 32 bytes long
        RegistrationAssertionHelperImpl.forSharedSecret(shortSecret, 10, 100_000L);
    }

    /**
     * Verifies that signatures created using an RSA private key can be validated using the corresponding public key.
     */
    @Test
    public void testForSigningWorksWithRsaSignatures() {

        SignatureSupportingConfigProperties props = new SignatureSupportingConfigProperties();
        props.setKeyPath("target/certs/hono-messaging-key.pem");
        props.setCertPath("target/certs/hono-messaging-cert.pem");

        RegistrationAssertionHelper factory = RegistrationAssertionHelperImpl.forSigning(vertx, props);
        String assertion = factory.getAssertion("tenant", "device");
        assertNotNull(assertion);
        RegistrationAssertionHelper validator = RegistrationAssertionHelperImpl.forValidating(vertx, props);
        assertTrue(validator.isValid(assertion, "tenant", "device"));
    }

    /**
     * Verifies that bad tenant, bad token and bad device are detected.
     */
    @Test
    public void testTokenConsistency() {
        RegistrationAssertionHelper validator = createValidator();

        String token = createToken(1, "tenant", "device");
        assertNotNull(token);
        assertFalse(validator.isValid(token, "bad-tenant", "device"));
        assertFalse(validator.isValid(token, "tenant", "bad-device"));

        String badToken = createToken(1, "another-tenant", "device");
        assertNotNull(badToken);
        assertFalse(validator.isValid(badToken, "tenant", "device"));
    }

    /**
     * Verifies that expired token is detected
     */
    @Test
    public void testValidatingAlreadyExpiredTokens() {
        RegistrationAssertionHelper validator = createValidator();

        String tokenStillValid = createToken(1, "tenant", "device");
        assertTrue(validator.isValid(tokenStillValid, "tenant", "device"));

        String tokenTooOld = createToken(-1, "tenant", "device");
        assertFalse(validator.isValid(tokenTooOld, "tenant", "device"));
    }

    /**
     * Verifies that cache is used
     */
    @Test
    public void testValidationIsUsingCache() {
        String token = createToken(1, "tenant", "device");

        long firstValidationParsed = getDurationOfValidation(token);
        long secondValidationCached = getDurationOfValidation(token);

        // ensure that cached validation is at least twice as fast as parsed validation
        assertTrue((secondValidationCached * 2) < firstValidationParsed);
    }

    private long getDurationOfValidation(String token) {
        RegistrationAssertionHelper validator = createValidator();
        Instant before = Instant.now();
        assertTrue(validator.isValid(token, "tenant", "device"));
        Instant after = Instant.now();
        long result = TimeUnit.NANOSECONDS.toMillis(Duration.between(before, after).getNano());
        System.out.println("Validation took " + result + " ms");
        return result;
    }

    private RegistrationAssertionHelper createValidator() {
        SignatureSupportingConfigProperties props = new SignatureSupportingConfigProperties();
        props.setKeyPath("target/certs/hono-messaging-key.pem");
        props.setCertPath("target/certs/hono-messaging-cert.pem");

        return RegistrationAssertionHelperImpl.forValidating(vertx, props);
    }

    private String createToken(int validForSeconds, String tenant, String device) {

        PrivateKey key = KeyLoader.fromFiles(vertx, "target/certs/hono-messaging-key.pem", null).getPrivateKey();
        SignatureAlgorithm algorithm = SignatureAlgorithm.RS256;

        // considering default clock skew
        int allowedClockSkew = 10;
        if (validForSeconds <= 0) {
            validForSeconds -= allowedClockSkew;
        }

        return Jwts.builder()
                .signWith(algorithm, key)
                .setSubject(device)
                .claim("ten", tenant)
                .setExpiration(Date.from(Instant.now().plus(Duration.ofSeconds(validForSeconds))))
                .compact();
    }

}
