/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

/**
 * A utility for parsing JSON Web Tokens containing user identity and
 * granted authorities.
 *
 */
public interface AuthTokenValidator {

    /**
     * Expands an encoded JWT token containing a signed <em>claims</em> body.
     *
     * @param token The compact encoding of the JWT token to expand.
     * @return The expanded token.
     * @throws io.jsonwebtoken.JwtException if the token cannot be expanded, e.g. because its signature cannot
     *                      be verified or it is expired or malformed.
     */
    Jws<Claims> expand(String token);
}
