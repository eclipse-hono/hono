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

import org.eclipse.hono.service.credentials.SecretsValidator;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Abstract validator class from which concrete validators are derived that are specialized to a specific type of
 * credential secrets.
 * <p>
 * The class implements the validation steps that are common to all types of credentials. This e.g. includes the enabled
 * flag of credential entries, the iteration over several valid credential entries (until one is successfully validated
 * or none is left), etc.
 * <p>
 * The detailed algorithm to validate a single credential entry is delegated to the implementing subclass and so supports
 * a specified small implementation class per credentials type..
 *
 * @param <T> The type of secret that has to be validated: this can be String in case of password validation, a certificate
 *           class in case of a client certificate, etc.
 */
public abstract class AbstractSecretValidator<T> implements SecretsValidator<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractSecretValidator.class);

    /**
     * Validate an instance of T (e.g. a password) against a single credentials secret (as JsonObject).
     * <p>
     * Subclasses need to implement their specified algorithm for validation in this method.
     *
     * @param secretToValidate The item to validate.
     * @param aSecret The secret record as Map representing the JsonObject returned by the <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
     * @return The result of the validation as boolean.
     */
    protected abstract boolean validateSingleSecret(final T secretToValidate, final Map<String, String> aSecret);

    /**
     * Validate  an instance of T (e.g. a password) against credentials secrets (as JsonObject as defined in the
     * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>).
     * <p>
     * The payload from the get operation of the <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>
     * is parsed and splitted into single secrets entries in this method. The single entries are then delegated to the
     * {@link #validateSingleSecret} method of the implementing subclass where the detailed
     * validation is processed. If one entry was successfully validated, the validation is considered
     * successful and the result is completed. If no secret could be validated, the validation fails and thus the result
     * is set to failed.
     *
     * @param credentialsObject The credentials that were returned from the credentials get operation.
     * @param secret The secret to validate.
     *
     * @return boolean True if the authenticationObject could be validated, false otherwise.
     * @throws IllegalArgumentException If the payload is not correct.
     */
    @Override
    public final boolean validate(final CredentialsObject credentialsObject, final T secret) {

        if (!credentialsObject.getEnabled()) {
            // if not found : default is enabled
            LOG.debug("credentials not validated - device disabled");
            return false;
        }

        List<Map<String, String>> secrets = credentialsObject.getSecrets();

        if (secrets == null) {
            throw new IllegalArgumentException(String.format("credentials not validated - mandatory field %s is null", CredentialsConstants.FIELD_SECRETS));
        }

        if (secrets.size() == 0) {
            throw new IllegalArgumentException(String.format("credentials not validated - mandatory field %s is empty", CredentialsConstants.FIELD_SECRETS));
        }

        try {
            // find any validated secret -> validation was successful
            Predicate<Object> validationPred = credentialsSecret -> validateSingleSecret(secret, (Map<String, String>) credentialsSecret);
            Optional<Map<String, String>> validationSecret = secrets.stream().filter(validationPred).findAny();

            if (!validationSecret.isPresent()) {
                LOG.debug("credentials not validated - invalid");
                return false;
            }
        }
        catch(ClassCastException e) {
            throw new IllegalArgumentException(String.format("validator for type <%s> does not match with passed class %s", getSupportedType(),
                    secret.getClass().getName()));
        }
        return true;
    }
}
