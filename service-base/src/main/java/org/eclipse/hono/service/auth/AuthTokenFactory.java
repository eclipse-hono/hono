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

import java.time.Duration;

import org.eclipse.hono.auth.Authorities;

import io.vertx.core.json.JsonObject;

/**
 * A factory for creating JSON Web Tokens containing user identity and
 * granted authorities.
 *
 */
public interface AuthTokenFactory {

    /**
     * Gets the duration being used for calculating the <em>exp</em> claim of tokens
     * created by this factory.
     * <p>
     * Clients should always check if a token is expired before using any information
     * contained in the token.
     *
     * @return The duration.
     */
    Duration getTokenLifetime();

    /**
     * Creates the compact serialization of a JWS with a JSON Web Token for an identity and its
     * granted authorities as the (signed) payload.
     * <p>
     * The JWT
     * <ul>
     * <li>contains the authorization id in the <em>sub</em> claim.</li>
     * <li>contains the authorities (as returned by {@link Authorities#asMap()}) as claims.</li>
     * <li>expires after the {@linkplain #getTokenLifetime() configured expiration time}.</li>
     * </ul>
     *
     * @param authorizationId The asserted identity.
     * @param authorities The granted authorities or {@code null} if the asserted identity has no
     *                    authorities.
     * @return The compact encoding of the JWT.
     * @throws NullPointerException if authorization ID is {@code null}.
     * @see <a href="https://www.rfc-editor.org/rfc/rfc7515.html#section-3">RFC 7515, Section 3</a>
     */
    String createToken(String authorizationId, Authorities authorities);

    /**
     * Gets a JSON Web Key (JWK) set that contains the keys and parameters that can be used
     * to validate signatures created by this factory.
     *
     * @return The JWK set.
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7517">RFC 7517</a>
     */
    JsonObject getValidatingJwkSet();
}
