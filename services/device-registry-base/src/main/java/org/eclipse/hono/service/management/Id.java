/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management;

import java.util.Objects;

import com.google.common.base.MoreObjects;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * An ID used in results.
 */
@RegisterForReflection
public final class Id {

    private final String id;

    private Id(final String id) {
        this.id = id;
    }

    /**
     * Gets the id.
     *
     * @return The id.
     */
    public String getId() {
        return id;
    }

    /**
     * Create a new ID.
     *
     * @param id The value of the id.
     * @return The new instance.
     * @throws NullPointerException if id is {@code null}.
     * @throws IllegalArgumentException if id is blank.
     */
    public static Id of(final String id) {
        Objects.requireNonNull(id);

        if (id.isBlank()) {
            throw new IllegalArgumentException("'id' must not be blank or empty");
        }

        return new Id(id);
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("id", this.id)
                .toString();
    }
}
