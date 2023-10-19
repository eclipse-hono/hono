/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.auth.device.jwt;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RequestResponseApiConstants;

import io.jsonwebtoken.Claims;
import io.vertx.core.json.JsonObject;

/**
 * A service for extracting and parsing tenant and device information.
 *
 */
public interface CredentialsParser {

    /**
     * Extracts the tenant-id, auth-id and issuer from JWS Claims.
     *
     * @param claims A JsonObject containing the JWS Claims "tid", "sub" and optionally "iss".
     * @return A JsonObject containing values for "tenant-id", "auth-id" and "iss" (same as "auth-id" if the input claims have no "iss" claim) extracted from the JWS Claims.
     * @throws NullPointerException If the given JsonObject is {@code null}.
     * @throws ClientErrorException If tenant-id or auth-id cannot correctly be extracted from the JWS Claims.
     */
    default JsonObject parseCredentialsFromClaims(final JsonObject claims) {

        Objects.requireNonNull(claims);

        final var credentials = new JsonObject();
        credentials.put(
                RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID,
                Optional.ofNullable(claims.getString(CredentialsConstants.CLAIM_TENANT_ID))
                        .orElseThrow(() -> new ClientErrorException(
                                HttpURLConnection.HTTP_UNAUTHORIZED,
                                "JWT must specify tenant ID in %s claim"
                                        .formatted(CredentialsConstants.CLAIM_TENANT_ID))));
        final var authId = Optional.ofNullable(claims.getString(Claims.SUBJECT))
                .orElseThrow(() -> new ClientErrorException(
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        "JWT must specify auth ID in %s claim".formatted(Claims.SUBJECT)));
        credentials.put(CredentialsConstants.FIELD_AUTH_ID, authId);
        final var issuer = Optional.ofNullable(claims.getString(Claims.ISSUER)).orElse(authId);
        credentials.put(Claims.ISSUER, issuer);
        return credentials;
    }

    /**
     * Extracts the tenant-id and auth-id from a String.
     *
     * @param input An input String containing the tenant-id and auth-id.
     * @return A JsonObject containing values for "tenant-id", "auth-id" and "iss" (same as "auth-id") extracted from the input String.
     * @throws NullPointerException if the given string is {@code null}.
     */
    JsonObject parseCredentialsFromString(String input);
}
