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

import org.eclipse.hono.util.CredentialsConstants;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

/**
 * Validator to validate credentials of type {@link CredentialsConstants#SECRETS_TYPE_HASHED_PASSWORD} for a given password.
 */
@Component
@Scope("prototype")
public final class CredentialsValidatorHashedPassword extends AbstractCredentialsValidator<String> {

    /**
     * Get the type of credentials secrets this validator is responsible for.
     *
     * @return The type of credentials secrets.
     */
    @Override
    public String getSecretsType() {
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
     * @param aSecret The secret record as JsonObject (as returned by the <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
     * @return The result of the approval as boolean.
     */
    @Override
    protected boolean validateSingleSecret(final String password, final JsonObject aSecret) {
        String hashFunction = aSecret.getString(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION);
        if (hashFunction == null) {
            return false;
        }

        return checkPasswordAgainstSecret(aSecret, hashFunction, password);
    }

    private boolean checkPasswordAgainstSecret(final JsonObject secret, final String hashFunction, final String protocolAdapterPassword) {

        String pwdHash = secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH);
        if (pwdHash == null) {
            return false;
        }

        byte[] password = Base64.getDecoder().decode(pwdHash);

        // TODO: performance - always decode salts is terrible
        final String salt = secret.getString(CredentialsConstants.FIELD_SECRETS_SALT);
        final byte[] decodedSalt = Base64.getDecoder().decode(salt);
        final String decodedSaltStr = new String(decodedSalt);

        try {
            byte[] hashedPassword = hashPassword(hashFunction, decodedSaltStr, protocolAdapterPassword);
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

    private byte[] hashPassword(final String hashFunction,final String hashSalt, final String passwordToHash) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest messageDigest = MessageDigest.getInstance(hashFunction);
        messageDigest.update(hashSalt.getBytes("UTF-8"));
        byte[] theHashedPassword = messageDigest.digest(passwordToHash.getBytes());
        return theHashedPassword;
    }
}
