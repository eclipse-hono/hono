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

package org.eclipse.hono.service.management.credentials;

import java.util.Base64;
import java.util.List;
import java.util.OptionalInt;

import org.eclipse.hono.auth.EncodedPassword;
import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;

/**
 * Helper methods for working with credentials.
 */
public final class Credentials {

    private Credentials() {
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
        s.setKey(psk.getBytes());
        return new PskCredential(authId, List.of(s));
    }

    /**
     * Creates a X509 certificate based credential from the given subject DN.
     *
     * @param subjectDN The subject DN.
     * @return The X509 certificate credential.
     */
    public static X509CertificateCredential createX509CertificateCredential(final String subjectDN) {
        return X509CertificateCredential.fromSubjectDn(subjectDN, List.of(new X509CertificateSecret()));
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
     * Create a new password secret.
     *
     * @param password The password to use.
     * @param bcryptCostFactor The cost factor to use for creating a bcrypt password hash.
     * @return The password secret instance.
     */
    public static PasswordSecret createPasswordSecret(final String password, final OptionalInt bcryptCostFactor) {

        final SpringBasedHonoPasswordEncoder encoder = new SpringBasedHonoPasswordEncoder(
                bcryptCostFactor.orElse(SpringBasedHonoPasswordEncoder.DEFAULT_BCRYPT_STRENGTH));
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
