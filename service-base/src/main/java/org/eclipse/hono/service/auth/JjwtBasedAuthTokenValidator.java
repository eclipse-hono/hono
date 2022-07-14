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

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.Vertx;

/**
 * A parser that creates a token from a compact serialization of a JWS containing a JSON Web Token as payload.
 * Also validates the JWT's signature if applicable.
 * Supports retrieving a JWT set that contains the key(s) from a web resource.
 *
 */
@RegisterForReflection(targets = {
                        io.jsonwebtoken.impl.DefaultJwtParserBuilder.class,
                        io.jsonwebtoken.jackson.io.JacksonDeserializer.class,
                        io.jsonwebtoken.impl.compression.DeflateCompressionCodec.class
})
public final class JjwtBasedAuthTokenValidator extends JwtSupport implements AuthTokenValidator {

    private static final Logger LOG = LoggerFactory.getLogger(JjwtBasedAuthTokenValidator.class);

    /**
     * Creates a validator for key material.
     *
     * @param vertx The Vert.x instance to run on.
     * @param config The configuration properties to determine the key material from.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if the key material cannot be determined from the configuration.
     */
    public JjwtBasedAuthTokenValidator(final Vertx vertx, final SignatureSupportingConfigProperties config) {

        super(vertx);
        Objects.requireNonNull(config);

        if (config.getSharedSecret() != null) {
            final byte[] secret = getBytes(config.getSharedSecret());
            setSharedSecret(secret);
            LOG.info("using shared secret [{} bytes] for validating tokens", secret.length);
        } else if (config.getCertPath() != null) {
            setPublicKey(config.getCertPath());
            LOG.info("using public key from certificate [{}] for validating tokens", config.getCertPath());
        } else {
            throw new IllegalArgumentException(
                    "configuration does not specify any key material for validating tokens");
        }
    }

    @Override
    public Jws<Claims> expand(final String token) {

        Objects.requireNonNull(token);
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }
}
