/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.service.management.credentials;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Helper methods for {@link CommonCredential}.
 */
public final class CommonCredentials {

    private CommonCredentials() {
    }

    /**
     * Find a credentials object in a set of credentials.
     *
     * @param credentials The credentials to search.
     * @param type The type to search for.
     * @param authId The auth id to search for.
     * @return The search result.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static Optional<? extends CommonCredential> findByTypeAndAuthId(
            final Collection<? extends CommonCredential> credentials,
            final String type,
            final String authId) {

        Objects.requireNonNull(credentials);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);

        return credentials.stream()
                .filter(credential -> type.equals(credential.getType()) &&
                        authId.equals(credential.getAuthId()))
                .findAny();

    }

    /**
     * Find a credentials object in a set of credentials based on another credential.
     *
     * @param credentials The credentials to search.
     * @param credential The source for *type* and *auth id*.
     * @return The search result.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static Optional<? extends CommonCredential> findByOtherCredential(
            final Collection<? extends CommonCredential> credentials,
            final CommonCredential credential) {

        Objects.requireNonNull(credentials);
        Objects.requireNonNull(credential);

        return findByTypeAndAuthId(credentials,
                credential.getType(), credential.getAuthId());

    }
}
