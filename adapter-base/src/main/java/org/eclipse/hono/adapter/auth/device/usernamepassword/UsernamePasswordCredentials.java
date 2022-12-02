/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.auth.device.usernamepassword;

import java.util.Objects;

import org.eclipse.hono.adapter.auth.device.AbstractDeviceCredentials;
import org.eclipse.hono.util.CredentialsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

/**
 * Helper class to parse username/password credentials provided by devices during authentication into properties to be
 * used with Hono's <em>Credentials</em> API.
 * <p>
 * The properties are determined as follows:
 * <ul>
 * <li><em>password</em> is always set to the given password.</li>
 * <li>the given username is split in two around the first occurrence of the <code>&#64;</code> sign. <em>authId</em> is
 * then set to the first part and <em>tenantId</em> is set to the second part.</li>
 * </ul>
 */
public class UsernamePasswordCredentials extends AbstractDeviceCredentials {

    private static final Logger LOG  = LoggerFactory.getLogger(UsernamePasswordCredentials.class);

    private String password;

    private UsernamePasswordCredentials(final String tenantId, final String authId, final JsonObject clientContext) {
        super(tenantId, authId, clientContext);
    }

    /**
     * Creates a new instance for a set of credentials.
     *
     * @param username The username provided by the device.
     * @param password The password provided by the device.
     * @return The credentials or {@code null} if the username does not contain a tenant ID.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static final UsernamePasswordCredentials create(final String username, final String password) {
        return create(username, password, new JsonObject());
    }

    /**
     * Creates a new instance for a set of credentials.
     *
     * @param username The username provided by the device.
     * @param password The password provided by the device.
     * @param clientContext The client context that can be used to get credentials from the Credentials API.
     * @return The credentials or {@code null} if the username does not contain a tenant ID.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static final UsernamePasswordCredentials create(
            final String username,
            final String password,
            final JsonObject clientContext) {

        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        Objects.requireNonNull(clientContext);

        // username consists of <userId>@<tenantId>
        final String[] userComponents = username.split("@", 2);
        if (userComponents.length != 2) {
            LOG.trace("username [{}] does not comply with expected pattern [<authId>@<tenantId>]", username);
            return null;
        }
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(userComponents[1],
                userComponents[0], clientContext);
        credentials.password = password;
        return credentials;
    }

    /**
     * {@inheritDoc}
     *
     * @return Always {@link CredentialsConstants#SECRETS_TYPE_HASHED_PASSWORD}
     */
    @Override
    public final String getType() {
        return CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;
    }

    /**
     * Gets the password to use for verifying the identity.
     *
     * @return The password.
     */
    public final String getPassword() {
        return password;
    }
}
