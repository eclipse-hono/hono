/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.cache;

import java.time.Instant;

/**
 * A generic <em>value</em> with a limited validity period.
 *
 * @param <T> The type of value.
 */
public interface ExpiringValue<T> {

    /**
     * Gets the value.
     * 
     * @return The value.
     */
    T getValue();

    /**
     * Checks if the value has already expired.
     * 
     * @return {@code true} if the value has expired based on the current system time,
     *         {@code false} otherwise.
     */
    boolean isExpired();

    /**
     * Checks if the value has already expired.
     * 
     * @param refInstant The reference point in time to check expiration against.
     * @return {@code true} if the value has expired based on the given instant,
     *         {@code false} otherwise.
     * @throws NullPointerException if the instant is {@code null}.
     */
    boolean isExpired(Instant refInstant);
}
