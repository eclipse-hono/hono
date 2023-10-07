/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.authentication;

import java.util.Objects;

import org.eclipse.hono.service.auth.AuthTokenFactory;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

/**
 * A resource for retrieving a JSON Web Key set that contains the (public) key that clients
 * should use to verify the signature of tokens issued by the Authentication Server.
 *
 * @see "https://datatracker.ietf.org/doc/html/rfc7517"
 */
@Path(AuthenticationServerClientOptions.DEFAULT_JWKS_ENDPOINT_URI)
public final class JwkResource {

    private static final Logger LOG = LoggerFactory.getLogger(JwkResource.class);

    private final Buffer jwkBytes;

    /**
     * Creates a new resource for a token factory.
     *
     * @param authTokenFactory The factory to obtain the JWK from.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public JwkResource(final AuthTokenFactory authTokenFactory) {
        Objects.requireNonNull(authTokenFactory);
        jwkBytes = authTokenFactory.getValidatingJwkSet().toBuffer();
    }

    /**
     * Gets the JWK representation of the key to use for verifying tokens issued.
     * by this Authentication Server.
     *
     * @return The HTTP response.
     */
    @GET
    @Produces("application/jwk-set+json")
    public Buffer getJwk() {
        LOG.debug("serving JWK set for validating JWTs");
        return jwkBytes;
    }
}
