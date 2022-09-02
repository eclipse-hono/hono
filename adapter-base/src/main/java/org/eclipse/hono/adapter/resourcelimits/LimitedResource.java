/**
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.resourcelimits;

import java.util.Objects;

/**
 * A holder for a limited resource's current and max value.
 *
 * @param <V> The resource's unit of measure.
 */
public final class LimitedResource<V> {

    private final V currentValue;
    private final V currentLimit;

   /**
     * Creates a new instance for a limit and a current value.
     *
     * @param currentLimit The limit calculated for the value.
     * @param currentValue The current value.
     * @throws NullPointerException if value is {@code null}.
     */
    LimitedResource(
            final V currentLimit,
            final V currentValue) {
        this.currentValue = Objects.requireNonNull(currentValue);
        this.currentLimit = currentLimit;
    }

    /**
     * Gets the current value.
     *
     * @return The value or {@code null} if undefined.
     */
    public V getCurrentValue() {
        return currentValue;
    }

    /**
     * Gets the limit calculated for the value.
     *
     * @return The limit or {@code null} if there is no limit defined.
     */
    public V getCurrentLimit() {
        return currentLimit;
    }
}
