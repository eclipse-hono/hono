/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.credentials.validators;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.eclipse.hono.util.CredentialsConstants;
import org.springframework.stereotype.Component;

/**
 * Validator to validate credentials of type {@link CredentialsConstants#SECRETS_TYPE_HASHED_PASSWORD} for a given password.
 */
@Component
public final class HashedPasswordValidator extends AbstractSecretValidator<String> {

    /**
     * Get the type of credentials secrets this validator is responsible for.
     *
     * @return The type of credentials secrets.
     */
    @Override
    public String getSupportedType() {
        return CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;
    }

    /**
     * Approve a single secret (as JsonObject) against a given password.
     * If the password matches against it, the approval is considered successful.
     * <p>This involves the hash-function found in the credentials secrets record to hash the given password before the
     * comparison against the password in the credentials secrets record is done.
     * <p>If a salt is contained in the credentials record (in base64 encoding), the hash function will be salted first.
     *
     * @param password The password to validate.
     * @param hashedPasswordSecret The secret record as JsonObject (as returned by the <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
     * @return The result of the approval as boolean.
     */
    @Override
    protected boolean validateSingleSecret(final String password, final Map<String, String> hashedPasswordSecret) {
        String hashFunction = hashedPasswordSecret.get(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION);
        if (hashFunction == null) {
            return false;
        }

        return checkPasswordAgainstSecret(hashedPasswordSecret, hashFunction, password);
    }

    private boolean checkPasswordAgainstSecret(final Map<String, String> hashedPasswordSecret, final String hashFunction, final String plainTextPassword) {

        String pwdHash = hashedPasswordSecret.get(CredentialsConstants.FIELD_SECRETS_PWD_HASH);
        if (pwdHash == null) {
            return false;
        }

        byte[] password = Base64.getDecoder().decode(pwdHash);

        final String salt = hashedPasswordSecret.get(CredentialsConstants.FIELD_SECRETS_SALT);
        byte[] decodedSalt = null;
        // the salt is optional so decodedSalt may stay null if salt was not found
        if (salt != null) {
            decodedSalt = Base64.getDecoder().decode(salt);
        }

        try {
            byte[] hashedPassword = hashPassword(hashFunction, decodedSalt, plainTextPassword);
            if (!Arrays.equals(password, hashedPassword)) {
                return false;
            }
        } catch (NoSuchAlgorithmException e) {
            return false;
        } catch (UnsupportedEncodingException e) {
            return false;
        }
        // check if the password is the hashed version of the protocol adapter password
        return true;
    }

    private byte[] hashPassword(final String hashFunction, final byte[] hashSalt, final String passwordToHash) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest messageDigest = MessageDigest.getInstance(hashFunction);
        if (hashSalt != null) {
            messageDigest.update(hashSalt);
        }
        byte[] theHashedPassword = messageDigest.digest(passwordToHash.getBytes());
        return theHashedPassword;
    }
}
