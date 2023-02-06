/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.credentials;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.OptionalInt;

import org.eclipse.hono.service.auth.EncodedPassword;
import org.eclipse.hono.service.auth.SpringBasedHonoPasswordEncoder;

/**
 * Helper methods for working with credentials.
 */
public final class Credentials {

    private static final int MAX_BCRYPT_COST_FACTOR = Integer.getInteger("max.bcrypt.costFactor", 10);

    private Credentials() {
    }

    /**
     * Gets the maximum BCrypt cost factor supported by Hono.
     *
     * @return The cost factor.
     */
    public static int getMaxBcryptCostFactor() {
        return MAX_BCRYPT_COST_FACTOR;
    }

    /**
     * Creates RawPublicKey type based credentials for a certificate.
     *
     * @param authId The authentication to use.
     * @param cert The certificate to extract the public key from.
     * @return The credentials.
     */
    public static RpkCredential createRPKCredential(final String authId, final X509Certificate cert) {
        return createRPKCredential(authId, cert.getPublicKey());
    }

    /**
     * Creates RawPublicKey type based credentials for a public key.
     *
     * @param authId The authentication to use.
     * @param key The raw public key.
     * @return The credentials.
     */
    public static RpkCredential createRPKCredential(final String authId, final PublicKey key) {
        final var secret = new RpkSecret()
                .setKey(key.getEncoded())
                .setAlgorithm(key.getAlgorithm());
        return new RpkCredential(authId, List.of(secret));
    }

    /**
     * Creates a PSK type based credential containing a psk secret.
     *
     * @param authId The authentication to use.
     * @param psk The psk to use.
     * @return The fully populated secret.
     */
    public static PskCredential createPSKCredential(final String authId, final String psk) {

        final PskSecret s = new PskSecret();
        s.setKey(psk.getBytes(StandardCharsets.UTF_8));
        return new PskCredential(authId, List.of(s));
    }

    /**
     * Create a password type based credential containing a password secret.
     *
     * @param authId The authentication to use.
     * @param password The password to use.
     * @return The fully populated credential.
     */
    public static PasswordCredential createPasswordCredential(final String authId, final String password) {
        return createPasswordCredential(authId, password, OptionalInt.empty());
    }

    /**
     * Creates a password type based credential containing a hashed password secret.
     *
     * @param authId The authentication to use.
     * @param password The password to use.
     * @param bcryptCostFactor The cost factor to use for creating a bcrypt password hash.
     * @return The fully populated credential.
     */
    public static PasswordCredential createPasswordCredential(
            final String authId,
            final String password,
            final OptionalInt bcryptCostFactor) {

        return new PasswordCredential(
                authId,
                List.of(createPasswordSecret(password, bcryptCostFactor)));
    }

    /**
     * Create a password type based credential containing a plain password secret.
     *
     * @param authId The authentication to use.
     * @param password The password to use.
     * @return The fully populated credential.
     */
    public static PasswordCredential createPlainPasswordCredential(final String authId, final String password) {

        final PasswordSecret secret = new PasswordSecret();
        secret.setPasswordPlain(password);
        return new PasswordCredential(authId, List.of(secret));
    }

    /**
     * Create a new password secret.
     *
     * @param password The password to use.
     * @param bcryptCostFactor The cost factor to use for creating a bcrypt password hash.
     * @return The password secret instance.
     */
    public static PasswordSecret createPasswordSecret(final String password, final OptionalInt bcryptCostFactor) {

        final var encoder = new SpringBasedHonoPasswordEncoder(bcryptCostFactor.orElse(MAX_BCRYPT_COST_FACTOR));
        final EncodedPassword encodedPwd = EncodedPassword.fromHonoSecret(encoder.encode(password));

        final PasswordSecret s = new PasswordSecret();
        s.setHashFunction(encodedPwd.hashFunction);
        if (encodedPwd.salt != null) {
            s.setSalt(Base64.getEncoder().encodeToString(encodedPwd.salt));
        }
        s.setPasswordHash(encodedPwd.password);
        return s;
    }

}
