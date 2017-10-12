/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Contributors:
 * Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.auth.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private String authId;
    private String password;
    private String tenantId;

    /**
     * Creates a new instance for a set of credentials.
     *
     * @param username The username provided by the device.
     * @param password The password provided by the device.
     * @param singleTenant If {@code true}, the <em>tenantId</em> is set to {@link Constants#DEFAULT_TENANT},
     *                     otherwise it is parsed from the username.
     * @return The instance of the created object. Will be null if the userName is null, or the
     *             username does not comply to the structure userName@tenantId.
     */
    public static final UsernamePasswordCredentials create(final String username, final String password,
            final boolean singleTenant) {

        if (username == null) {
            LOG.trace("username must not be null");
            return null;
        } else if (password == null) {
            LOG.trace("password must not be null");
            return null;
        }

        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials();
        if (singleTenant) {
            credentials.authId = username;
            credentials.tenantId = Constants.DEFAULT_TENANT;
        } else {
            // multi tenantId -> <userId>@<tenantId>
            String[] userComponents = username.split("@", 2);
            if (userComponents.length != 2) {
                LOG.trace("username does not comply with expected pattern [<authId>@<tenantId>]", username);
                return null;
            } else {
                credentials.authId = userComponents[0];
                credentials.tenantId = userComponents[1];
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
     * Gets the identity that the device wants to authenticate as.
     * <p>
     * This is either the value of the username property provided by the device (single tenant),
     * or the <em>auth ID</em> part parsed from the username property (multi tenant).
     * 
     * @return The identity.
     */
    @Override
    public final String getAuthId() {
        return authId;
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
     * Gets the tenant that the device claims to belong to.
     * <p>
     * This is either the {@link Constants#DEFAULT_TENANT} (single tenant) or the <em>tenant ID</em> part
     * parsed from the username property (multi tenant).
     * 
     * @return The tenant.
     */
    @Override
    public final String getTenantId() {
        return tenantId;
    }

    /**
     * Matches the credentials against a given secret.
     * <p>
     * The secret is expected to be of type <em>hashed-password</em> as defined by
     * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Hono's Credentials API</a>.
     * 
     * @param candidateSecret The secret to match against.
     * @return {@code true} if the credentials match the secret.
     */
    @Override
    public boolean matchesCredentials(final Map<String, String> candidateSecret) {

        String pwdHash = candidateSecret.get(CredentialsConstants.FIELD_SECRETS_PWD_HASH);
        if (pwdHash == null) {
            return false;
        }

        byte[] hashedPasswordOnRecord = Base64.getDecoder().decode(pwdHash);

        byte[] salt = null;
        final String encodedSalt = candidateSecret.get(CredentialsConstants.FIELD_SECRETS_SALT);
        // the salt is optional so decodedSalt may stay null if salt was not found
        if (encodedSalt != null) {
            salt = Base64.getDecoder().decode(encodedSalt);
        }

        String hashFunction = candidateSecret.getOrDefault(
                CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION,
                CredentialsConstants.DEFAULT_HASH_FUNCTION);

        return checkPassword(hashFunction, salt, hashedPasswordOnRecord);
    }

    private boolean checkPassword(final String hashFunction, final byte[] salt, final byte[] hashedPasswordOnRecord) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(hashFunction);
            if (salt != null) {
                messageDigest.update(salt);
            }
            byte[] hashedPassword = messageDigest.digest(getPassword().getBytes(StandardCharsets.UTF_8));
            return Arrays.equals(hashedPassword, hashedPasswordOnRecord);
        } catch (final NoSuchAlgorithmException e) {
            return false;
        }
    }
}
