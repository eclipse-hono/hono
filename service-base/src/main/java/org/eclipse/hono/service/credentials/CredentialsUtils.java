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
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Utility class as Spring bean that is used by protocol adapters to find matching implementations for the
 * validation of authentication objects.
 */
@Component
@Scope("prototype")
public final class CredentialsUtils {
    private final Map<String, Set<CredentialsSecretsValidator>> secretsValidators = new HashMap<>();

    @Autowired(required = false)
    public final void addValidators(final List<CredentialsSecretsValidator> definedValidators) {
        Objects.requireNonNull(definedValidators);
        for (CredentialsSecretsValidator secretsValidator : definedValidators) {
            addSecretsValidator(secretsValidator);
        }
    }

    private void addSecretsValidator(final CredentialsSecretsValidator secretsValidator) {
        Set<CredentialsSecretsValidator> validatorsSet;
        if (secretsValidators.containsKey(secretsValidator.getSecretsType())) {
            validatorsSet = secretsValidators.get(secretsValidator.getSecretsType());
        } else {
            validatorsSet = new HashSet<CredentialsSecretsValidator>();
        }
        validatorsSet.add(secretsValidator);
        secretsValidators.put(secretsValidator.getSecretsType(), validatorsSet);
    }

    public Set<CredentialsSecretsValidator> findAppropriateValidators(final String secretsType) {
        return secretsValidators.get(secretsType);
    }
}
