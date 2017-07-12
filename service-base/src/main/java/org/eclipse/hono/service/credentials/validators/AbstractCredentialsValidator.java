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

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.service.credentials.CredentialsSecretsValidator;
import org.eclipse.hono.util.CredentialsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @param <T> The type of what has to be validated (called item below): this can be String in case of password validation, a certificate
 *           class in case of a client certificate, etc.
 */
public abstract class AbstractCredentialsValidator<T> implements CredentialsSecretsValidator<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCredentialsValidator.class);

    /**
     * Get the type of credentials secrets this validator is responsible for.
     * <p>This can be freely defined, but there are some predefined types in the
     * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
     *
     * @return The type of credentials secrets.
     */
    @Override
    public abstract String getSecretsType();

    /**
     * Validate an instance of T (e.g. a password) against a single credentials secret (as JsonObject).
     * <p>
     * Subclasses need to implement their specified algorithm for validation in this method.
     *
     * @param itemToValidate The item to validate.
     * @param aSecret The secret record as JsonObject (as returned by the <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
     * @return The result of the validation as boolean.
     */
    protected abstract boolean validateSingleSecret(final T itemToValidate, final JsonObject aSecret);

    /**
     * Validate  an instance of T (e.g. a password) against credentials secrets (as JsonObject as defined in the
     * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>).
     * <p>
     * The payload from the get operation of the <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>
     * is parsed and splitted into single secrets entries in this method. The single entries are then delegated to the
     * {@link #validateSingleSecret(Object, JsonObject)} method of the implementing subclass where the detailed
     * validation is processed. If one entry was successfully validated, the validation is considered
     * successful and the result is completed. If no secret could be validated, the validation fails and thus the result
     * is set to failed.
     *
     * @param credentialsGetPayload The payload as returned from the credentials get operation.
     * @param authenticationObject The object to authenticate.
     *
     * @return boolean True if the authenticationObject could be validated, false otherwise.
     */
    @Override
    public final boolean validate(final JsonObject credentialsGetPayload, final T authenticationObject) {

        if (!checkMandatoryFields(credentialsGetPayload)) {
            return false;
        }

        if (credentialsGetPayload.containsKey(CredentialsConstants.FIELD_ENABLED) && !credentialsGetPayload.getBoolean(CredentialsConstants.FIELD_ENABLED)) {
            // if not found : default is enabled
            LOG.debug("Credentials not validated - device disabled");
            return false;
        }

        JsonArray secrets = credentialsGetPayload.getJsonArray(CredentialsConstants.FIELD_SECRETS);

        if (secrets == null) {
            LOG.debug("Credentials not validated - mandatory field {} is null", CredentialsConstants.FIELD_SECRETS);
            return false;
        }

        if (secrets.size() == 0) {
            LOG.debug("Credentials not validated - mandatory field {} is empty", CredentialsConstants.FIELD_SECRETS);
            return false;
        }

        // find any validated secret -> validation was successful
        Predicate<Object> validationPred = secret -> validateSingleSecret(authenticationObject, (JsonObject) secret);
        Optional<Object> validationSecret = secrets.stream().filter(validationPred).findAny();

        if (!validationSecret.isPresent()) {
            LOG.debug("Credentials not validated - invalid");
            return false;
        }

        return true;
    }

    private boolean checkMandatoryFields(final JsonObject payload) {

        if (!payload.containsKey(CredentialsConstants.FIELD_SECRETS)) {
            LOG.debug("Credentials not validated - mandatory field {} not found", CredentialsConstants.FIELD_SECRETS);
            return false;
        }

        return true;
    }
}
