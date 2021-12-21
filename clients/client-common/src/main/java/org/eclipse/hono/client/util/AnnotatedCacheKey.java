/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A cache key that supports adding metadata attributes to be used to filter cache entries.
 * <p>
 * The metadata attributes are not taken into account by the {@link #equals(Object)} and {@link #hashCode()} methods and
 * are therefore not part of the identity checks by the cache implementation.
 *
 * @param <T> The type of the cache key.
 */
public final class AnnotatedCacheKey<T> {

    private final T key;
    private final Map<String, String> attributes = new HashMap<>();

    /**
     * Creates an instance for the given key.
     *
     * @param key The cache key to be used.
     * @throws NullPointerException if the key is {@code null}.
     */
    public AnnotatedCacheKey(final T key) {
        this.key = Objects.requireNonNull(key);
    }

    /**
     * Gets the cache key.
     *
     * @return The key.
     */
    public T getKey() {
        return key;
    }

    /**
     * Puts the given attribute to the metadata.
     *
     * @param attributeKey The key of the attribute.
     * @param value The value of the attribute.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public void putAttribute(final String attributeKey, final String value) {
        Objects.requireNonNull(attributeKey);
        Objects.requireNonNull(value);

        attributes.put(attributeKey, value);
    }

    /**
     * Gets the attribute value for the given key.
     *
     * @param attributeKey The key of the attribute
     * @return An optional that contains the value or an empty Optional if no value is present for the given attribute
     *         key.
     * @throws NullPointerException if the attribute key is {@code null}.
     */
    public Optional<String> getAttribute(final String attributeKey) {
        Objects.requireNonNull(attributeKey);

        return Optional.ofNullable(attributes.get(attributeKey));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnnotatedCacheKey<?> that = (AnnotatedCacheKey<?>) o;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "AnnotatedCacheKey{" +
                "key=" + key +
                ", attributes=" + attributes +
                '}';
    }
}
