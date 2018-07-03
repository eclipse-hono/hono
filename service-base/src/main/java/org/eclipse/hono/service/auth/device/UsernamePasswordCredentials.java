/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.auth.device;

import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.EncodedPassword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Helper class to parse username/password credentials provided by devices during authentication into
 * properties to be used with Hono's <em>Credentials</em> API.
 * <p>
 * The properties are determined as follows:
 * <ul>
 * <li><em>password</em> is always set to the given password.</li>
 * <li>If Hono is configured for single tenant mode, <em>tenantId</em> is set to {@link Constants#DEFAULT_TENANT} and
 * <em>authId</em> is set to the given username.</li>
 * <li>If Hono is configured for multi tenant mode, the given username is split in two around the first occurrence of
 * the <code>&#64;</code> sign. <em>authId</em> is then set to the first part and <em>tenantId</em> is set to the
 * second part.</li>
 * </ul>
 */
public class UsernamePasswordCredentials extends AbstractDeviceCredentials {

    private static final Logger LOG  = LoggerFactory.getLogger(UsernamePasswordCredentials.class);

    private String password;

    private UsernamePasswordCredentials(final String tenantId, final String authId) {
        super(tenantId, authId);
    }

    /**
     * Creates a new instance for a set of credentials.
     *
     * @param username The username provided by the device.
     * @param password The password provided by the device.
     * @param singleTenant If {@code true}, the <em>tenantId</em> is set to {@link Constants#DEFAULT_TENANT},
     *                     otherwise it is parsed from the username.
     * @return The credentials or {@code null} if single tenant is {@code false} and
     *         the username does not contain a tenant ID.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static final UsernamePasswordCredentials create(final String username, final String password,
            final boolean singleTenant) {

        Objects.requireNonNull(username);
        Objects.requireNonNull(password);

        final UsernamePasswordCredentials credentials;
        if (singleTenant) {
            credentials = new UsernamePasswordCredentials(Constants.DEFAULT_TENANT, username);
        } else {
            // multi tenantId -> <userId>@<tenantId>
            final String[] userComponents = username.split("@", 2);
            if (userComponents.length != 2) {
                LOG.trace("username does not comply with expected pattern [<authId>@<tenantId>]", username);
                return null;
            } else {
                credentials = new UsernamePasswordCredentials(userComponents[1], userComponents[0]);
            }
        }
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

    /**
     * Matches the credentials against a given secret.
     * <p>
     * The secret is expected to be of type <em>hashed-password</em> as defined by
     * <a href="https://www.eclipse.org/hono/api/credentials-api/#hashed-password">Hono's Credentials API</a>.
     * 
     * @param candidateSecret The secret to match against.
     * @return {@code true} if the credentials match the secret.
     */
    @Override
    public boolean matchesCredentials(final JsonObject candidateSecret) {
        try {
            final EncodedPassword encodedPassword = EncodedPassword.fromHonoSecret(candidateSecret);
            return encodedPassword.matches(getPassword());
        } catch (final IllegalArgumentException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("cannot decode malformed Base64 encoded property", e);
            }
            return false;
        } catch (final ClassCastException e) {
            // one or more of the properties are not of expected type
            if (LOG.isDebugEnabled()) {
                LOG.debug("cannot process malformed candidate hashed-password secret returned by Credentials service [{}]",
                        candidateSecret.encodePrettily());
            }
            return false;
        }
    }

}
