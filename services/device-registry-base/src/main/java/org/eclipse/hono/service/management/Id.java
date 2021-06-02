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

package org.eclipse.hono.service.management;

import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * An ID used in results.
 */
public final class Id {

    private final String id;

    private Id(final String id) {
        this.id = id;
    }

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

    /**
     * Create {@link ToStringHelper} for this instance. <br>
     * Derived classes should call the super method, and add their own fields. Following this pattern derived classes do
     * not need to implement {@link #toString()}.
     *
     * @return A new instance of a {@link ToStringHelper}, filled with fields from this instance.
     */
    protected ToStringHelper toStringHelper() {
        return MoreObjects
                .toStringHelper(this)
                .add("id", this.id);
    }

    /**
     * {@code toString} method implemented based on {@link #toStringHelper()}.
     */
    @Override
    public String toString() {
        return toStringHelper().toString();
    }
}
