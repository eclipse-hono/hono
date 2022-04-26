/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.cli.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * A base class for implementing converters that can resolve environment variable references.
 *
 */
abstract class AbstractVariableConverter {

    private static final Pattern VAR_REF = Pattern.compile("\\$\\{\\S+\\}");

    private void resolve(
            final Map<String, String> environment,
            final Map<String, String> resolvedVariables,
            final String variableReference) {
        final String variableName = variableReference.substring(2, variableReference.length() - 1);
        if (environment.containsKey(variableName)) {
            resolvedVariables.put(variableReference, environment.get(variableName));
        } else {
            throw new IllegalArgumentException(
                    "Environment does not contain variable with name %s".formatted(variableName));
        }
    }

    /**
     * Resolves references to OS environment variables contained in a string.
     * <p>
     * Resolves variable references of the form {@code ${VAR_NAME}}.
     *
     * @param stringValue The string containing the variable references.
     * @param environmentVariables The environment variable definitions.
     * @return The original value with all OS variable references that it contains being replaced with the corresponding
     *         variable's (string) value.
     * @throws IllegalArgumentException if any of the variable references contained in the string cannot be resolved
     *                                  using the given environment.
     */
    protected final String getResolvedValue(
            final String stringValue,
            final Map<String, String> environmentVariables) {

        try (var scanner = new Scanner(stringValue)) {
            final Map<String, String> resolvedValues = scanner.findAll(VAR_REF)
                    .map(MatchResult::group)
                    .collect(HashMap::new, (map, varRef) -> resolve(environmentVariables, map, varRef), (a, b) -> a.putAll(b));
            String result = stringValue;
            for (var entry : resolvedValues.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
            return result;
        }
    }

}
