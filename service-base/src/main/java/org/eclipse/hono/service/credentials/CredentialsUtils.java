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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Utility class as Spring bean that is used by protocol adapters to find matching implementations for the
 * validation of authentication objects.
 */
@Component
public final class CredentialsUtils {
    private static final Map<String, SecretsValidator> secretsValidators = new HashMap<>();

    @Autowired(required = false)
    public final void addValidators(final Set<SecretsValidator> definedValidators) {
        Objects.requireNonNull(definedValidators);
        for (SecretsValidator secretsValidator : definedValidators) {
            addSecretsValidator(secretsValidator);
        }
    }

    /**
     * Add validator bean to the internal map that keeps track of a single validator per type.
     *
     * @param secretsValidator The validator bean to add to the map.
     *
     * @throws IllegalArgumentException If there was a validator for this type already (which is considered conceptually
     * illegal).
     */
    private void addSecretsValidator(final SecretsValidator secretsValidator) {
        if (secretsValidators.containsKey(secretsValidator.getSupportedType())) {
            throw new IllegalArgumentException(String.format("multiple credentials validators for type <%s> found - not supported.",
                    secretsValidator.getSupportedType()));
        }
        secretsValidators.put(secretsValidator.getSupportedType(), secretsValidator);
    }

    public static SecretsValidator findAppropriateValidators(final String secretsType) {
        return secretsValidators.get(secretsType);
    }
}
