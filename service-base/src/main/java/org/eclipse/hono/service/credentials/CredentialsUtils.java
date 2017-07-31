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

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Utility class as Spring bean that is used by protocol adapters to find matching implementations for the
 * validation of authentication objects.
 */
@Component
public final class CredentialsUtils {
    private static final Map<String, CredentialsSecretsValidator> secretsValidators = new HashMap<>();

    @Autowired(required = false)
    public final void addValidators(final Set<CredentialsSecretsValidator> definedValidators) throws BeanCreationException {
        Objects.requireNonNull(definedValidators);
        for (CredentialsSecretsValidator secretsValidator : definedValidators) {
            addSecretsValidator(secretsValidator);
        }
    }

    private void addSecretsValidator(final CredentialsSecretsValidator secretsValidator) throws IllegalArgumentException {
        if (secretsValidators.containsKey(secretsValidator.getSecretsType())) {
            throw new IllegalArgumentException(String.format("multiple credentials validators for type <%s> found - not supported.",
                    secretsValidator.getSecretsType()));
        }
        secretsValidators.put(secretsValidator.getSecretsType(), secretsValidator);
    }

    public static CredentialsSecretsValidator findAppropriateValidators(final String secretsType) {
        return secretsValidators.get(secretsType);
    }
}
