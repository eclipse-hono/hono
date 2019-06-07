/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A versioned entry.
 * 
 * @param <T> The payload type.
 */
public class Versioned<T> {

    private final String version;
    private final T value;

    /**
     * Created a new versioned entry.
     * 
     * @param version The version.
     * @param value The value.
     */
    public Versioned(final String version, final T value) {
        this.version = version;
        this.value = value;
    }

    /**
     * Create a new versioned entry, with a new version.
     * 
     * @param value The value.
     */
    public Versioned(final T value) {
        this(UUID.randomUUID().toString(), value);
    }

    public T getValue() {
        return this.value;
    }

    public String getVersion() {
        return this.version;
    }

    /**
     * Create new version, in case of a version match.
     * 
     * @param resourceVersion The expected version.
     * @param newContent The new content supplier.
     * @return If the expected version was empty, or the expected version matches the current version, a new instance
     *         with the new content. Otherwise {@code null}.
     */
    public Versioned<T> update(final Optional<String> resourceVersion, final Supplier<T> newContent) {

        Objects.requireNonNull(resourceVersion);

        if (isVersionMatch(resourceVersion)) {
            return new Versioned<>(newContent.get());
        }

        return null;
    }

    /**
     * Tests if this version matches the provided version.
     * 
     * @param resourceVersion The provided version to check, may be {@link Optional#empty()}.
     * @return {@code true} if the provided version to check is empty or matches the current version, {@code false}
     *         otherwise.
     */
    public boolean isVersionMatch(final Optional<String> resourceVersion) {

        Objects.requireNonNull(resourceVersion);

        return resourceVersion.isEmpty() || resourceVersion.get().equals(this.version);

    }

}
