/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.auth;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.EncodedPassword;
import org.eclipse.hono.util.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.vertx.core.json.JsonObject;

/**
 * A Spring Security based password matcher.
 * <p>
 * The encoder supports matching of password hashes that have been created
 * using one of the following hash functions:
 * <ul>
 * <li>sha-256</li>
 * <li>sha-512</li>
 * <li>bcrypt using the <em>2a</em> salt format</li>
 * </ul>
 */
public class SpringBasedHonoPasswordEncoder implements HonoPasswordEncoder {

    private static final int DEFAULT_BCRYPT_ITERATIONS = 10;

    private final Map<String, PasswordEncoder> encoders = new HashMap<>();
    private final PasswordEncoder encoderForEncode;
    private final String idForEncode;
    private final SecureRandom secureRandom;

    /**
     * Creates a new encoder.
     * <p>
     * This constructor will create a new {@code SecureRandom}
     * as follows
     * <ol>
     * <li>try to create a SecureRandom using algorithm <em>NativePRNGNonBlocking</em></li>
     * <li>if that fails, create a default SecureRandom, i.e. without specifying an
     * algorithm</li>
     * </ol>
     * and then invoke {@link #SpringBasedHonoPasswordEncoder(SecureRandom)}.
     * 
     * @see "https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/"
     * @see "https://hackernoon.com/hack-how-to-use-securerandom-with-kubernetes-and-docker-a375945a7b21"
     */
    public SpringBasedHonoPasswordEncoder() {
        this(newSecureRandom());
    }

    /**
     * Creates a new encoder for a random number generator.
     * 
     * @param rng The random number generator to use.
     * @throws NullPointerException if the RNG is {@code null}.
     */
    public SpringBasedHonoPasswordEncoder(final SecureRandom rng) {
        this.secureRandom = Objects.requireNonNull(rng);
        idForEncode = CredentialsConstants.HASH_FUNCTION_BCRYPT;
        encoderForEncode = new BCryptPasswordEncoder(DEFAULT_BCRYPT_ITERATIONS, secureRandom);
        encoders.put(idForEncode, encoderForEncode);
        encoders.put(
                CredentialsConstants.HASH_FUNCTION_SHA256,
                new MessageDigestPasswordEncoder(CredentialsConstants.HASH_FUNCTION_SHA256));
        encoders.put(
                CredentialsConstants.HASH_FUNCTION_SHA512,
                new MessageDigestPasswordEncoder(CredentialsConstants.HASH_FUNCTION_SHA512));
    }

    @Override
    public boolean matches(final String rawPassword, final JsonObject credentialsOnRecord) {

        try {
            final EncodedPassword encodedPassword = EncodedPassword.fromHonoSecret(credentialsOnRecord);
            final PasswordEncoder encoder = Optional.ofNullable(encoders.get(encodedPassword.hashFunction)).orElse(encoderForEncode);
            return encoder.matches(rawPassword, encodedPassword.format());
        } catch (IllegalArgumentException e) {
            // invalid Base64 scheme
            return false;
        }
    }

    private static SecureRandom newSecureRandom() {
        // this rather complicated setup is intended to prevent blocking, which is especially an issue in containers
        // see: https://hackernoon.com/hack-how-to-use-securerandom-with-kubernetes-and-docker-a375945a7b21
        // and: https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/

        try {
            return SecureRandom.getInstance("NativePRNGNonBlocking"); // non-blocking UNIX
        } catch (NoSuchAlgorithmException e) {
            return new SecureRandom(); // might block
        }
    }
}
