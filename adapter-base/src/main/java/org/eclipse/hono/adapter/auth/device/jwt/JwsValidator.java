/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.adapter.auth.device.jwt;

import java.time.Duration;
import java.util.List;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A service for validating JSON Web Signature structures.
 *
 */
public interface JwsValidator {

    /**
     * Expands a JWS structure's payload.
     *
     * @param jws The JWS.
     * @param candidateKeys The public keys which should be used for validation.
     * @param allowedClockSkew The amount of clock skew to tolerate when verifying
     *                         the local time against the exp and nbf claims.
     * @return The claims contained in the payload.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    Future<Jws<Claims>> expand(
            String jws,
            List<JsonObject> candidateKeys,
            Duration allowedClockSkew);
}
