/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.resourcelimits;

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
     */
    LimitedResource(
            final V currentLimit,
            final V currentValue) {
        this.currentValue = currentValue;
        this.currentLimit = currentLimit;
    }

    /**
     * @return The currentValue.
     */
    public V getCurrentValue() {
        return currentValue;
    }

    /**
     * @return The currentLimit.
     */
    public V getCurrentLimit() {
        return currentLimit;
    }
}
