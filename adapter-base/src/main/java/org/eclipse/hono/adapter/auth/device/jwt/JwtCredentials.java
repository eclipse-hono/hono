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

import java.util.Objects;

import org.eclipse.hono.adapter.auth.device.AbstractDeviceCredentials;
import org.eclipse.hono.util.CredentialsConstants;

import io.vertx.core.json.JsonObject;

/**
 * Helper class to parse credentials provided by devices during authentication into properties to be used with Hono's
 * <em>Credentials</em> API for the JWT based authentication.
 */
public class JwtCredentials extends AbstractDeviceCredentials {

    private final String jws;

    private JwtCredentials(
            final String tenantId,
            final String authId,
            final String jws,
            final JsonObject clientContext) {
        super(tenantId, authId, clientContext);
        this.jws = Objects.requireNonNull(jws);
    }

    /**
     * Creates a new instance for a set of credentials.
     *
     * @param tenantId The {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} of the tenant the device belongs to.
     * @param authId The {@value CredentialsConstants#FIELD_AUTH_ID} provided by the device.
     * @param password The JWT provided by the device in the password field.
     * @return The credentials.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static JwtCredentials create(final String tenantId, final String authId, final String password) {
        return create(tenantId, authId, password, new JsonObject());
    }

    /**
     * Creates a new instance for a set of credentials.
     *
     * @param tenantId The {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} of the tenant the device belongs to.
     * @param authId The {@value CredentialsConstants#FIELD_AUTH_ID} provided by the device.
     * @param jws The compact encoding of a JSON Web Signature (JWS) structure that contains a JSON Web Token (JWT)
     *            provided by the client.
     * @param clientContext The client context that can be used to get credentials from the Credentials API.
     * @return The credentials.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static JwtCredentials create(
            final String tenantId,
            final String authId,
            final String jws,
            final JsonObject clientContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(jws);
        Objects.requireNonNull(clientContext);

        return new JwtCredentials(tenantId, authId, jws, clientContext);
    }

    /**
     * {@inheritDoc}
     *
     * @return Always {@link CredentialsConstants#SECRETS_TYPE_RAW_PUBLIC_KEY}
     */
    @Override
    public String getType() {
        return CredentialsConstants.SECRETS_TYPE_RAW_PUBLIC_KEY;
    }

    /**
     * Gets the JSON Web Signature (JWS) structure that contains the client's JSON Web Token (JWT).
     *
     * @return The JWS.
     */
    public final String getJws() {
        return jws;
    }
}
