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
package org.eclipse.hono.service.credentials;

import org.eclipse.hono.util.CredentialsObject;

/**
 * Interface that all credentials validators need to implement.
 * <p>
 * Any mechanism to authenticate a device is based on credentials secrets that are defined in the
 * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
 * This interface defines the methods necessary to provide an algorithm for validating any of these secrets.
 * <p>
 * Validators that implement this interface and are implemented as Spring beans in the package
 * {@link org.eclipse.hono.service.credentials.validators} will be automatically found during startup and are used
 * during authentication.
 * This makes adding of own validators very easy.
 * <p>See the provided validators (e.g. {@link org.eclipse.hono.service.credentials.validators.CredentialsValidatorHashedPassword})
 * as a blueprint for how to write own validators.
 * See {@link org.eclipse.hono.service.credentials.validators.AbstractCredentialsValidator} as the base class for such
 * implementations.
 *
 * @param <T> The type of what has to be validated (called item below): this can be String in case of password validation, a certificate
 *           class in case of a client certificate, etc.
 */

public interface CredentialsSecretsValidator<T> {
    /**
     * Get the type of credentials secrets this validator is responsible for.
     * <p>This can be freely defined, but there are some predefined types in the
     * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
     *
     * @return The type of credentials secrets.
     */
    String getSecretsType();

    /**
     * Validate  an instance of T (e.g. a password) against credentials secrets (as JsonObject as defined in the
     * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>).
     * <p>
     *
     * @param credentialsGetPayload The payload as returned from the credentials API get operation.
     * @param itemToValidate The item that has to be validated, e.g. a password String, a certificate, etc.
     *
     * @return True if the item could be validated, false otherwise.
     * @throws IllegalArgumentException If the payload is not correct.
     */
    boolean validate(final CredentialsObject credentialsGetPayload, T itemToValidate) throws IllegalArgumentException;
}
