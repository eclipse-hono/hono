/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

    private String jwt;

    private JwtCredentials(final String tenantId, final String authId, final JsonObject clientContext) {
        super(tenantId, authId, clientContext);
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
     * @param jwt The JWT provided by the device in the password field.
     * @param clientContext The client context that can be used to get credentials from the Credentials API.
     * @return The credentials.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static JwtCredentials create(
            final String tenantId,
            final String authId,
            final String jwt,
            final JsonObject clientContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(jwt);
        Objects.requireNonNull(clientContext);

        final JwtCredentials credentials = new JwtCredentials(tenantId, authId, clientContext);
        credentials.jwt = jwt;
        return credentials;
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
     * Gets the JWT to use for verifying the identity.
     *
     * @return The JWT.
     */
    public final String getJwt() {
        return jwt;
    }
}
