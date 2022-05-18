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

package org.eclipse.hono.service.auth;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.vertx.core.json.JsonObject;

/**
 * A Spring Security based password encoder.
 * <p>
 * The encoder supports matching of password hashes that have been created
 * using one of the following hash functions:
 * <ul>
 * <li>sha-256</li>
 * <li>sha-512</li>
 * <li>bcrypt using the <em>2a</em> salt format</li>
 * </ul>
 * <p>
 * The encoder uses BCrypt for encoding passwords with a reasonable number of iterations.
 */
public class SpringBasedHonoPasswordEncoder implements HonoPasswordEncoder {

    /**
     * The default Bcrypt strength setting.
     */
    public static final int DEFAULT_BCRYPT_STRENGTH = 10;

    private static final Logger LOG = LoggerFactory.getLogger(SpringBasedHonoPasswordEncoder.class);

    private final Map<String, PasswordEncoder> encoders = new HashMap<>();
    private final PasswordEncoder encoderForEncode;
    private final String idForEncode;
    private final SecureRandom secureRandom;

    /**
     * Creates a new encoder.
     * <p>
     * This constructor will create a new {@code SecureRandom}
     * as follows:
     * <ol>
     * <li>try to create a SecureRandom using algorithm <em>NativePRNGNonBlocking</em></li>
     * <li>if that fails, create a default SecureRandom, i.e. without specifying an
     * algorithm</li>
     * </ol>
     * and then invoke {@link #SpringBasedHonoPasswordEncoder(SecureRandom, int)}.
     *
     * @see "https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/"
     * @see "https://hackernoon.com/hack-how-to-use-securerandom-with-kubernetes-and-docker-a375945a7b21"
     */
    public SpringBasedHonoPasswordEncoder() {
        this(newSecureRandom(), DEFAULT_BCRYPT_STRENGTH);
    }

    /**
     * Creates a new encoder.
     * <p>
     * This constructor will create a new {@code SecureRandom}
     * as follows:
     * <ol>
     * <li>try to create a SecureRandom using algorithm <em>NativePRNGNonBlocking</em></li>
     * <li>if that fails, create a default SecureRandom, i.e. without specifying an
     * algorithm</li>
     * </ol>
     * and then invoke {@link #SpringBasedHonoPasswordEncoder(SecureRandom, int)}.
     *
     * @param bcryptStrength The strength to use for creating BCrypt hashes. Value must be
     *             &gt;= 4 and &lt;= 31. Note that a higher value will increase the time
     *             it takes to compute a hash. A value around 10 is considered a good compromise
     *             between security and computation time.
     * @see "https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/"
     * @see "https://hackernoon.com/hack-how-to-use-securerandom-with-kubernetes-and-docker-a375945a7b21"
     */
    public SpringBasedHonoPasswordEncoder(final int bcryptStrength) {
        this(newSecureRandom(), bcryptStrength);
    }

    /**
     * Creates a new encoder for a random number generator.
     *
     * @param rng The random number generator to use.
     * @param bcryptStrength The strength to use for creating BCrypt hashes. Value must be
     *             &gt;= 4 and &lt;= 31. Note that a higher value will increase the time
     *             it takes to compute a hash. A value around 10 is considered a good compromise
     *             between security and computation time.
     * @throws NullPointerException if the RNG is {@code null}.
     * @throws IllegalArgumentException if BCrypt strength is &lt; 4 or &gt; 31.
     */
    public SpringBasedHonoPasswordEncoder(final SecureRandom rng, final int bcryptStrength) {

        this.secureRandom = Objects.requireNonNull(rng);
        encoderForEncode = new BCryptPasswordEncoder(bcryptStrength, secureRandom);
        idForEncode = CredentialsConstants.HASH_FUNCTION_BCRYPT;
        encoders.put(idForEncode, encoderForEncode);
        encoders.put(
                CredentialsConstants.HASH_FUNCTION_SHA256,
                new MessageDigestPasswordEncoder(CredentialsConstants.HASH_FUNCTION_SHA256, secureRandom));
        encoders.put(
                CredentialsConstants.HASH_FUNCTION_SHA512,
                new MessageDigestPasswordEncoder(CredentialsConstants.HASH_FUNCTION_SHA512, secureRandom));
        LOG.info("using BCrypt [strength: {}] with PRNG [{}] for encoding clear text passwords",
                bcryptStrength, rng.getAlgorithm());
    }

    @Override
    public JsonObject encode(final String rawPassword) {

        final EncodedPassword encodedPwd = new EncodedPassword(encoderForEncode.encode(rawPassword));
        return CredentialsObject.hashedPasswordSecretForPasswordHash(
                encodedPwd.password,
                idForEncode,
                null,
                null,
                encodedPwd.salt);
    }

    @Override
    public boolean matches(final String rawPassword, final JsonObject credentialsOnRecord) {

        try {
            final EncodedPassword encodedPassword = EncodedPassword.fromHonoSecret(credentialsOnRecord);
            final PasswordEncoder encoder = Optional.ofNullable(encoders.get(encodedPassword.hashFunction)).orElse(encoderForEncode);
            return encoder.matches(rawPassword, encodedPassword.format());
        } catch (final IllegalArgumentException e) {
            // invalid Base64 scheme
            LOG.debug("error matching password", e);
            return false;
        }
    }

    private static SecureRandom newSecureRandom() {
        // this rather complicated setup is intended to prevent blocking, which is especially an issue in containers
        // see: https://hackernoon.com/hack-how-to-use-securerandom-with-kubernetes-and-docker-a375945a7b21
        // and: https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/

        try {
            return SecureRandom.getInstance("NativePRNGNonBlocking"); // non-blocking UNIX
        } catch (final NoSuchAlgorithmException e) {
            return new SecureRandom(); // might block
        }
    }
}
